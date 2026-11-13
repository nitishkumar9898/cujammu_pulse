package com.college.events.service;

import com.college.events.dto.HelpdeskDtos.*;
import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class SupportHelpdeskService {
    private final SupportTicketRepository tickets;
    private final SupportTicketEntryRepository entries;
    private final UserRepository users;
    private final DomainAccess access;
    private final FeatureFlagService flags;
    private final HelpdeskTicketNumberService numbers;
    private final AuditService audit;
    private final NotificationRepository notifications;
    private final Validator validator;
    private final PlatformTransactionManager transactions;

    @Transactional(propagation=Propagation.NEVER)
    public Ticket create(Create command) {
        valid(command);
        User actor=current();
        if(TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Ticket creation must start outside a transaction");
        String requestDigest=digest(command.subject().trim(),command.description().trim(),command.category(),command.requestedPriority());
        var existing=tickets.findByCreatedByIdAndRequestKey(actor.getId(),command.requestKey());
        if(existing.isPresent()) {
            if(!requestDigest.equals(existing.get().getRequestDigest()))throw conflict("Request key already used for different content");
            return dto(existing.get(),actor);
        }
        // Allocate without holding a domain connection. Nested allocation would exhaust bounded pools.
        String number=numbers.nextNumber();
        var transaction=new TransactionTemplate(transactions);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return transaction.execute(status -> createCommitted(command,number));
    }

    private Ticket createCommitted(Create command,String number) {
        User actor=current();
        actor=tickets.lockOwner(actor.getId()).filter(User::isEnabled).orElseThrow(DomainAccess::denied);
        String digest=digest(command.subject().trim(),command.description().trim(),command.category(),command.requestedPriority());
        var existing=tickets.findByCreatedByIdAndRequestKey(actor.getId(),command.requestKey());
        if(existing.isPresent()) {
            if(!digest.equals(existing.get().getRequestDigest()))throw conflict("Request key already used for different content");
            return dto(existing.get(),actor);
        }
        SupportTicket ticket=SupportTicket.builder().publicId(UUID.randomUUID().toString()).requestKey(command.requestKey())
                .requestDigest(digest).ticketNumber(number).subject(command.subject().trim())
                .description(command.description().trim()).category(command.category()).requestedPriority(command.requestedPriority())
                .priority("MEDIUM").status("OPEN").createdBy(actor).createdByName(actor.getName())
                .lastActivityAt(now()).build();
        tickets.saveAndFlush(ticket);
        append(ticket,actor,command.requestKey(),digest,"CREATED","Ticket opened. Priority awaits support triage.");
        return dto(ticket,actor);
    }

    public PageResult<Ticket> ownTickets(int page) {
        User actor=current();return result(tickets.findByCreatedByIdOrderByCreatedAtDescIdDesc(actor.getId(),paging(page)).map(t->dto(t,actor)));
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Ticket linkLegacy(LegacyLink command) {
        valid(command);User actor=current();
        SupportTicket ticket=tickets.lockLegacyTicket(command.ticketNumber()).orElseThrow(SupportHelpdeskService::notFound);
        readable(ticket,actor);
        if(ticket.getCreatedBy()==null)throw conflict("Legacy ticket owner requires institutional review");
        if(ticket.getPublicId()!=null)return dto(ticket,actor);
        version(ticket,command.expectedVersion());
        ticket.setPublicId(UUID.randomUUID().toString());touch(ticket);
        append(ticket,actor,command.requestKey(),digest("LEGACY_LINK",ticket.getTicketNumber()),"LINKED","Existing ticket linked to a private conversation. Original records preserved.");
        return dto(ticket,actor);
    }
    public PageResult<Ticket> allTickets(int page) {
        current();User actor=access.requireRole(Role.ADMIN);
        return result(tickets.findAllByOrderByCreatedAtDescIdDesc(paging(page)).map(t->dto(t,actor)));
    }
    public PageResult<Ticket> assignedTickets(int page) {
        current();User actor=access.requireRole(Role.STAFF,Role.ADMIN);
        return result(tickets.findByAssignedStaffIdOrderByCreatedAtDescIdDesc(actor.getId(),paging(page)).map(t->dto(t,actor)));
    }
    public Ticket ticket(String id) {
        User actor=current();SupportTicket ticket=find(id);readable(ticket,actor);return dto(ticket,actor);
    }
    public PageResult<Entry> thread(String id,int page) {
        User actor=current();SupportTicket ticket=find(id);readable(ticket,actor);
        return result(entries.findByTicketIdOrderByIdAsc(ticket.getId(),paging(page)).map(e ->
                new Entry(e.getPublicId(),e.getKind(),e.getAuthorLabel(),e.getBody(),e.getOccurredAt())));
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Ticket reply(String id,Reply command) {
        valid(command);User actor=current();SupportTicket ticket=locked(id);readable(ticket,actor);
        String digest=digest("REPLY",command.body().trim());
        if(replayed(ticket,actor,command.requestKey(),digest))return dto(ticket,actor);
        version(ticket,command.expectedVersion());
        if(!Set.of("OPEN","IN_PROGRESS").contains(ticket.getStatus()))throw conflict("Reopen the ticket before replying");
        touch(ticket);append(ticket,actor,command.requestKey(),digest,"REPLY",command.body().trim());
        return dto(ticket,actor);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Ticket update(String id,Update command) {
        valid(command);User actor=current();SupportTicket ticket=locked(id);manage(ticket,actor);
        if(ticket.getCreatedBy()==null)throw conflict("Legacy ticket owner requires institutional review");
        String digest=digest("UPDATE",command.status(),command.priority(),command.assignedOffice(),
                String.valueOf(command.assignedStaffId()),text(command.resolutionNotes()));
        if(replayed(ticket,actor,command.requestKey(),digest))return dto(ticket,actor);
        version(ticket,command.expectedVersion());
        boolean assignmentChanged=!Objects.equals(ticket.getAssignedOffice(),command.assignedOffice())
                ||!Objects.equals(ticket.getAssignedStaffId(),command.assignedStaffId());
        if(assignmentChanged && actor.getRole()!=Role.ADMIN)throw DomainAccess.denied();
        if(command.assignedStaffId()!=null) {
            User assigned=users.findById(command.assignedStaffId()).filter(User::isEnabled).orElseThrow(DomainAccess::denied);
            if(!Set.of(Role.STAFF,Role.ADMIN).contains(assigned.getRole()) || Objects.equals(assigned.getId(),ticket.getCreatedBy().getId()))
                throw DomainAccess.denied();
        }
        String old=ticket.getStatus();
        if(!old.equals(command.status()) && !allowedTransition(old,command.status()))throw conflict("Invalid ticket status transition");
        if(Set.of("RESOLVED","CLOSED").contains(command.status()) && text(command.resolutionNotes())==null)
            throw badRequest("Resolution notes are required");
        Long previousAssignee=ticket.getAssignedStaffId();
        ticket.setStatus(command.status());ticket.setPriority(command.priority());ticket.setAssignedOffice(command.assignedOffice());
        ticket.setAssignedStaffId(command.assignedStaffId());ticket.setResolutionNotes(text(command.resolutionNotes()));
        if("RESOLVED".equals(command.status()) && !"RESOLVED".equals(old))ticket.setResolvedAt(now());
        if(Set.of("RESOLVED","CLOSED").contains(command.status()))ticket.setEscalated(false);
        touch(ticket);
        append(ticket,actor,command.requestKey(),digest,"STATUS_CHANGED","Status: "+command.status()+". Office: "+command.assignedOffice()+". Priority: "+command.priority()+"."
                +(text(command.resolutionNotes())==null?"":" Resolution: "+command.resolutionNotes().trim()));
        if(previousAssignee!=null && !Objects.equals(previousAssignee,ticket.getAssignedStaffId()))
            users.findById(previousAssignee).filter(User::isEnabled).ifPresent(u->notify(u));
        return dto(ticket,actor);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Ticket reopen(String id,OwnerAction command) {
        valid(command);User actor=current();SupportTicket ticket=locked(id);readable(ticket,actor);
        if(!owner(ticket,actor) && !manager(ticket,actor))throw DomainAccess.denied();
        String digest=digest("REOPENED",command.reason().trim());
        if(replayed(ticket,actor,command.requestKey(),digest))return dto(ticket,actor);
        version(ticket,command.expectedVersion());
        if(!Set.of("RESOLVED","CLOSED").contains(ticket.getStatus()))throw conflict("Only resolved or closed tickets can be reopened");
        ticket.setStatus("OPEN");ticket.setResolvedAt(null);ticket.setResolutionNotes(null);ticket.setEscalated(false);
        touch(ticket);append(ticket,actor,command.requestKey(),digest,"REOPENED",command.reason().trim());return dto(ticket,actor);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Ticket escalate(String id,OwnerAction command) {
        valid(command);User actor=current();SupportTicket ticket=locked(id);readable(ticket,actor);
        String digest=digest("ESCALATED",command.reason().trim());
        if(replayed(ticket,actor,command.requestKey(),digest))return dto(ticket,actor);
        version(ticket,command.expectedVersion());
        if(!Set.of("OPEN","IN_PROGRESS").contains(ticket.getStatus()))throw conflict("Only active tickets can be escalated");
        if(ticket.isEscalated())throw conflict("Escalation is already awaiting review");
        ticket.setEscalated(true);touch(ticket);append(ticket,actor,command.requestKey(),digest,"ESCALATED",command.reason().trim());
        return dto(ticket,actor);
    }

    private boolean allowedTransition(String from,String to) {
        return ("OPEN".equals(from)&&"IN_PROGRESS".equals(to)) || ("IN_PROGRESS".equals(from)&&"RESOLVED".equals(to))
                || ("RESOLVED".equals(from)&&"CLOSED".equals(to));
    }
    private boolean owner(SupportTicket ticket,User actor) {return ticket.getCreatedBy()!=null && Objects.equals(ticket.getCreatedBy().getId(),actor.getId());}
    private boolean manager(SupportTicket ticket,User actor) {
        return !owner(ticket,actor) && (actor.getRole()==Role.ADMIN || (actor.getRole()==Role.STAFF && Objects.equals(ticket.getAssignedStaffId(),actor.getId())));
    }
    private void readable(SupportTicket ticket,User actor) {if(!owner(ticket,actor)&&!manager(ticket,actor))throw DomainAccess.denied();}
    private void manage(SupportTicket ticket,User actor) {if(!manager(ticket,actor))throw DomainAccess.denied();}
    private User current() {User actor=access.currentUser();if(!flags.isFeatureEnabled("MODULE_HELPDESK"))throw notFound();return actor;}
    private SupportTicket find(String id) {return tickets.findByPublicId(id).orElseThrow(SupportHelpdeskService::notFound);}
    private SupportTicket locked(String id) {return tickets.lockTicket(id).orElseThrow(SupportHelpdeskService::notFound);}
    private void valid(Object command) {if(command==null||!validator.validate(command).isEmpty())throw badRequest("Invalid helpdesk command");}
    private void version(SupportTicket t,long expected) {if(t.getVersion()!=expected)throw conflict("Ticket changed; reload before updating");}
    private void touch(SupportTicket t) {t.setLastActivityAt(now());tickets.saveAndFlush(t);}
    private boolean replayed(SupportTicket ticket,User actor,String key,String digest) {
        var old=entries.findByTicketIdAndRequestKey(ticket.getId(),key);
        if(old.isEmpty())return false;
        if(!Objects.equals(old.get().getActorId(),actor.getId())||!digest.equals(old.get().getRequestDigest()))
            throw conflict("Request key already used for another command");
        return true;
    }
    private void append(SupportTicket ticket,User actor,String key,String digest,String kind,String body) {
        entries.saveAndFlush(SupportTicketEntry.builder().ticket(ticket).publicId(UUID.randomUUID().toString()).requestKey(key)
                .requestDigest(digest).actorId(actor.getId()).kind(kind).authorLabel(owner(ticket,actor)?"Requester":"Support")
                .body(body).occurredAt(now()).build());
        audit.logAction(actor.getEmail(),actor.getRole().name(),"HELPDESK_"+kind,"ticketId="+ticket.getPublicId());
        // Durable private inbox rows share the domain transaction. No network/provider effects.
        var recipients=new LinkedHashMap<Long,User>();
        if(ticket.getCreatedBy()!=null&&ticket.getCreatedBy().isEnabled())recipients.put(ticket.getCreatedBy().getId(),ticket.getCreatedBy());
        if(ticket.getAssignedStaffId()!=null)
            users.findById(ticket.getAssignedStaffId()).filter(User::isEnabled).ifPresent(u->recipients.put(u.getId(),u));
        if(("CREATED".equals(kind)&&ticket.getAssignedStaffId()==null)||"ESCALATED".equals(kind))
            users.findByRole(Role.ADMIN).stream().filter(User::isEnabled).forEach(u->recipients.put(u.getId(),u));
        recipients.values().forEach(this::notify);
    }
    private void notify(User recipient) {notifications.save(Notification.builder().user(recipient)
            .message("A private support ticket was updated. Open helpdesk to review it.").actionUrl("/helpdesk").isRead(false).build());}
    private Ticket dto(SupportTicket t,User actor) {return new Ticket(t.getPublicId(),t.getVersion(),t.getTicketNumber(),t.getSubject(),t.getDescription(),
            t.getCategory(),t.getPriority(),t.getRequestedPriority(),t.getStatus(),t.getAssignedOffice(),manager(t,actor)?t.getAssignedStaffId():null,t.isEscalated(),t.getResolutionNotes(),
            t.getCreatedAt(),t.getResolvedAt(),manager(t,actor),Set.of("RESOLVED","CLOSED").contains(t.getStatus()));}
    private <T> PageResult<T> result(Page<T> page){return new PageResult<>(page.getContent(),page.getNumber(),page.hasNext());}
    private PageRequest paging(int page){if(page<0||page>10000)throw badRequest("Invalid page");return PageRequest.of(page,24);}
    private static String text(String value){return value==null||value.isBlank()?null:value.trim();}
    private static LocalDateTime now(){return LocalDateTime.now(ZoneOffset.UTC);}
    private static String digest(String... parts) {
        try {
            MessageDigest hash=MessageDigest.getInstance("SHA-256");
            for(String part:parts){byte[] bytes=String.valueOf(part).getBytes(StandardCharsets.UTF_8);hash.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));hash.update((byte)':');hash.update(bytes);}
            return HexFormat.of().formatHex(hash.digest());
        }catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private static ResponseStatusException notFound(){return new ResponseStatusException(HttpStatus.NOT_FOUND);}
    private static ResponseStatusException badRequest(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    private static ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
}

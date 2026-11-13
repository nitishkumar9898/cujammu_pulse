package com.college.events.service;

import com.college.events.model.MailDelivery;
import com.college.events.model.MailDelivery.*;
import com.college.events.model.Event;
import com.college.events.model.EventStatus;
import com.college.events.model.Registration;
import com.college.events.model.RegistrationStatus;
import com.college.events.model.Role;
import com.college.events.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** SQL bridge outbox. Claims/completions are short transactions; SMTP is never called here. */
@Service @RequiredArgsConstructor
public class MailOutboxService {
    private final MailDeliveryRepository deliveries;
    private final UserRepository users;
    private final FeatureFlagRepository flags;
    private final EventRepository events;
    private final EventOperationsRepository eventOperations;
    private final RegistrationRepository registrations;
    static final int MAX_ATTEMPTS=5;
    static final List<Status> READY=List.of(Status.QUEUED,Status.RETRY);
    static final List<Status> PENDING=List.of(Status.QUEUED,Status.RETRY,Status.LEASED);
    public record Claim(String id,String leaseToken,String recipient,Kind kind) {}

    /** REQUIRED joins the owning domain transaction; persistence failure rolls it back. */
    @Transactional
    public String enqueue(String recipient,Kind kind) {
        return enqueueSource(recipient, kind, null, null, null, null);
    }

    /** Event notices retain only an event reference, scheduled-time snapshot and monotonic revision. */
    @Transactional
    public String enqueueEventNotice(String recipient,Kind kind,Long sourceEventId,
                                     LocalDateTime sourceEventDate,Long sourceEventVersion) {
        return enqueueSource(recipient, kind, sourceEventId, sourceEventDate, sourceEventVersion, null);
    }

    /** Campaign notice keeps its frozen eligibility boundary for claim-time revocation checks. */
    @Transactional
    public String enqueueCampaignEventAnnouncement(String recipient,Long sourceEventId,
            LocalDateTime sourceEventDate,Long sourceEventVersion,Long verifiedDepartmentId) {
        if (sourceEventId == null || sourceEventDate == null || sourceEventVersion == null)
            throw new IllegalArgumentException("Campaign notice requires an event revision snapshot");
        return enqueueScopedEventAnnouncement(recipient,sourceEventId,sourceEventDate,sourceEventVersion,verifiedDepartmentId);
    }

    /** Small-batch announcements retain the same live eligibility boundary as campaign fan-out. */
    @Transactional
    public String enqueueScopedEventAnnouncement(String recipient,Long sourceEventId,
            LocalDateTime sourceEventDate,Long sourceEventVersion,Long verifiedDepartmentId) {
        if (sourceEventId == null || sourceEventDate == null || sourceEventVersion == null)
            throw new IllegalArgumentException("Scoped announcement requires an event revision snapshot");
        return enqueueSource(recipient,Kind.EVENT_ANNOUNCEMENT,sourceEventId,sourceEventDate,
                sourceEventVersion,null,Role.STUDENT,verifiedDepartmentId);
    }

    /** Registration-linked notices retain only the source row ID for claim-time freshness checks. */
    @Transactional
    public String enqueueRegistrationNotice(String recipient,Kind kind,Long sourceRegistrationId) {
        return enqueueSource(recipient, kind, null, null, null, sourceRegistrationId);
    }

    private String enqueueSource(String recipient,Kind kind,Long sourceEventId,
                                 LocalDateTime sourceEventDate,Long sourceEventVersion,
                                 Long sourceRegistrationId) {
        return enqueueSource(recipient,kind,sourceEventId,sourceEventDate,sourceEventVersion,
                sourceRegistrationId,null,null);
    }

    private String enqueueSource(String recipient,Kind kind,Long sourceEventId,
                                 LocalDateTime sourceEventDate,Long sourceEventVersion,
                                 Long sourceRegistrationId,Role requiredRecipientRole,
                                 Long requiredVerifiedDepartmentId) {
        if(kind==null || recipient==null || recipient.length()>254 || recipient.contains("\r") || recipient.contains("\n"))
            throw new IllegalArgumentException("Invalid notification recipient or kind");
        if ((sourceEventId != null && sourceEventId < 1) || (sourceEventDate != null && sourceEventId == null) ||
                (sourceEventVersion != null && sourceEventId == null) ||
                (sourceEventVersion != null && sourceEventVersion < 0) ||
                (sourceRegistrationId != null && sourceRegistrationId < 1))
            throw new IllegalArgumentException("Invalid notification source reference");
        var user=users.findByEmail(recipient).orElseThrow(()->new IllegalArgumentException("Notification account unavailable"));
        Instant now=now();
        var delivery=new MailDelivery();
        delivery.setId(UUID.randomUUID().toString());delivery.setRecipientUserId(user.getId());
        delivery.setRecipient(recipient);delivery.setKind(kind);delivery.setCreatedAt(now);
        delivery.setSourceEventId(sourceEventId);delivery.setSourceEventDate(sourceEventDate);
        delivery.setSourceEventVersion(sourceEventVersion);
        delivery.setSourceRegistrationId(sourceRegistrationId);
        if (requiredRecipientRole != null && kind != Kind.EVENT_ANNOUNCEMENT)
            throw new IllegalArgumentException("Recipient scope is only valid for event announcements");
        delivery.setRequiredRecipientRole(requiredRecipientRole);
        delivery.setRequiredVerifiedDepartmentId(requiredVerifiedDepartmentId);
        // Operational lifetime, not a claim about institutional record retention.
        delivery.setExpiresAt(now.plus(24,ChronoUnit.HOURS));delivery.setNextAttemptAt(now);
        delivery.setStatus(Status.QUEUED);
        deliveries.save(delivery);
        return delivery.getId();
    }

    @Transactional(readOnly=true)
    public List<String> due() {return deliveries.findDue(READY,Status.LEASED,now(),PageRequest.of(0,25));}

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public Optional<Claim> claim(String id) {
        var delivery=deliveries.lock(id).orElse(null);Instant now=now();
        if(delivery==null || !PENDING.contains(delivery.getStatus()))return Optional.empty();
        if(delivery.getStatus()==Status.LEASED) {
            // A lease without a deadline is abandoned state; reclaim under this row lock.
            if(delivery.getLeaseUntil()!=null && delivery.getLeaseUntil().isAfter(now))return Optional.empty();
        } else {
            if(delivery.getNextAttemptAt()==null) {
                finish(delivery,Status.DEAD_LETTER,"SCHEDULE_MISSING",now);
                return Optional.empty();
            }
            if(delivery.getNextAttemptAt().isAfter(now))return Optional.empty();
        }
        if(!delivery.getExpiresAt().isAfter(now)){finish(delivery,Status.EXPIRED,"LIFETIME_EXPIRED",now);return Optional.empty();}
        if(delivery.getAttempts()>=MAX_ATTEMPTS){finish(delivery,Status.DEAD_LETTER,"ATTEMPTS_EXHAUSTED",now);return Optional.empty();}
        var user=users.findByIdForNotificationClaim(delivery.getRecipientUserId()).orElse(null);
        if(user==null || !user.isEnabled() || !Objects.equals(user.getEmail(),delivery.getRecipient())){
            finish(delivery,Status.SUPPRESSED,"ACCOUNT_CHANGED",now);return Optional.empty();
        }
        if ((delivery.getRequiredRecipientRole()!=null && user.getRole()!=delivery.getRequiredRecipientRole()) ||
                (delivery.getRequiredVerifiedDepartmentId()!=null &&
                        !delivery.getRequiredVerifiedDepartmentId().equals(user.getVerifiedDepartmentId()))) {
            finish(delivery,Status.SUPPRESSED,"AUDIENCE_SCOPE_CHANGED",now);return Optional.empty();
        }
        // Keep lock order aligned with domain commands: MODULE_EVENTS, source rows, then MODULE_NOTIFICATIONS.
        // Registration and event commands may write inbox notifications before their transaction commits.
        if(!enabled("MODULE_EVENTS")){
            finish(delivery,Status.SUPPRESSED,"FEATURE_DISABLED",now);return Optional.empty();
        }
        // Certificate issuance locks MODULE_CERTIFICATES before source rows and inbox notifications.
        if(delivery.getKind()==Kind.CERTIFICATE && !enabled("MODULE_CERTIFICATES")){
            finish(delivery,Status.SUPPRESSED,"FEATURE_DISABLED",now);return Optional.empty();
        }
        if (isEventNotice(delivery.getKind()) && !matchesCurrentEvent(delivery)) {
            finish(delivery,Status.SUPPRESSED,"SOURCE_EVENT_CHANGED",now);return Optional.empty();
        }
        if (isRegistrationNotice(delivery.getKind()) && !matchesCurrentRegistration(delivery)) {
            finish(delivery,Status.SUPPRESSED,"SOURCE_REGISTRATION_CHANGED",now);return Optional.empty();
        }
        // A disabled business channel cannot continue dispatching queued work. No cached flag authority.
        if(!enabled("MODULE_NOTIFICATIONS")){
            finish(delivery,Status.SUPPRESSED,"FEATURE_DISABLED",now);return Optional.empty();
        }
        delivery.setStatus(Status.LEASED);delivery.setLeaseToken(UUID.randomUUID().toString());
        delivery.setLeaseUntil(now.plusSeconds(120));delivery.setAttempts(delivery.getAttempts()+1);
        return Optional.of(new Claim(id,delivery.getLeaseToken(),delivery.getRecipient(),delivery.getKind()));
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void complete(Claim claim,Status outcome,String code) {
        if(!List.of(Status.PROVIDER_ACCEPTED,Status.SUPPRESSED,Status.RETRY).contains(outcome))
            throw new IllegalArgumentException("Invalid delivery outcome");
        if(!Set.of("SMTP_ACCEPTED","LOCAL_OR_DISABLED_BACKEND","SENDER_UNCONFIGURED","LINK_UNCONFIGURED","PROVIDER_FAILURE").contains(code))
            throw new IllegalArgumentException("Invalid delivery outcome code");
        var delivery=deliveries.lock(claim.id()).orElse(null);Instant now=now();
        if(delivery==null || delivery.getStatus()!=Status.LEASED || !Objects.equals(delivery.getLeaseToken(),claim.leaseToken()))return;
        if(!delivery.getExpiresAt().isAfter(now)){finish(delivery,Status.EXPIRED,"LIFETIME_EXPIRED",now);return;}
        if(outcome==Status.RETRY && delivery.getAttempts()<MAX_ATTEMPTS){
            delivery.setStatus(Status.RETRY);delivery.setNextAttemptAt(now.plusSeconds(Math.min(1800,30L << (delivery.getAttempts()-1))));
            delivery.setLeaseToken(null);delivery.setLeaseUntil(null);delivery.setOutcomeCode("PROVIDER_FAILURE");
        } else finish(delivery,outcome==Status.RETRY?Status.DEAD_LETTER:outcome,
            outcome==Status.RETRY?"ATTEMPTS_EXHAUSTED":code,now);
    }

    /** Independent bounded cleanup still runs when business notifications are disabled. */
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void scrubExpired() {
        Instant now=now();
        for(String id:deliveries.findExpiredPayload(now,PageRequest.of(0,100))) {
            var delivery=deliveries.lock(id).orElseThrow();
            if(!delivery.getExpiresAt().isAfter(now))finish(delivery,Status.EXPIRED,"LIFETIME_EXPIRED",now);
        }
        // Operational receipts are bounded too; this is not the institution's independently retained audit log.
        for(String id:deliveries.findExpiredReceipts(now.minus(30,ChronoUnit.DAYS),PageRequest.of(0,100))) {
            var delivery=deliveries.lock(id).orElseThrow();
            if(delivery.getRecipient()==null && delivery.getCompletedAt()!=null && delivery.getCompletedAt().isBefore(now.minus(30,ChronoUnit.DAYS)))
                deliveries.delete(delivery);
        }
    }
    @Transactional(readOnly=true) public long pendingCount(){return deliveries.countByStatusIn(PENDING);}
    private boolean enabled(String key){return flags.findByFeatureKeyForUpdate(key).map(f->f.isEnabled()).orElse(false);}
    private static boolean isEventNotice(Kind kind) {
        return kind==Kind.EVENT_APPROVED || kind==Kind.EVENT_REJECTED || kind==Kind.EVENT_CANCELLED ||
                kind==Kind.EVENT_POSTPONED || kind==Kind.EVENT_ANNOUNCEMENT;
    }
    private static boolean isRegistrationNotice(Kind kind) {
        return kind==Kind.REGISTRATION || kind==Kind.WAITLIST || kind==Kind.PROMOTION ||
                kind==Kind.WITHDRAWAL || kind==Kind.ATTENDANCE_UPDATE;
    }
    private boolean matchesCurrentRegistration(MailDelivery delivery) {
        if (delivery.getSourceRegistrationId()==null) return false;
        Registration preview=registrations.findWithEventById(delivery.getSourceRegistrationId()).orElse(null);
        if (preview==null || preview.getEvent()==null) return false;
        Long sourceEventId=preview.getEvent().getId();
        RegistrationStatus expected=switch(delivery.getKind()) {
            case REGISTRATION, PROMOTION -> RegistrationStatus.REGISTERED;
            case WAITLIST -> RegistrationStatus.WAITLISTED;
            case WITHDRAWAL -> RegistrationStatus.CANCELLED;
            case ATTENDANCE_UPDATE -> RegistrationStatus.ATTENDED;
            default -> null;
        };
        if (expected==null) return false;
        boolean requiresApprovedEvent=delivery.getKind()==Kind.REGISTRATION ||
                delivery.getKind()==Kind.WAITLIST || delivery.getKind()==Kind.PROMOTION;
        Event lockedEvent=null;
        if (requiresApprovedEvent) {
            // Match registration commands' control-row -> event-row -> registration-row order.
            if (eventOperations.findForUpdate(sourceEventId).isEmpty()) return false;
            lockedEvent=events.findForApproval(sourceEventId).orElse(null);
            if (lockedEvent==null || lockedEvent.getStatus()!=EventStatus.APPROVED) return false;
        }
        // The registration row lock serializes claim authorization with status-changing commands.
        Registration registration=registrations.findForUpdate(delivery.getSourceRegistrationId()).orElse(null);
        if (registration==null || registration.getUser()==null || registration.getEvent()==null ||
                !Objects.equals(registration.getUser().getId(),delivery.getRecipientUserId()) ||
                !Objects.equals(registration.getEvent().getId(),sourceEventId)) return false;
        if (registration.getStatus()!=expected) return false;
        return !requiresApprovedEvent || lockedEvent.getStatus()==EventStatus.APPROVED;
    }
    private boolean matchesCurrentEvent(MailDelivery delivery) {
        if (delivery.getSourceEventId()==null || delivery.getSourceEventVersion()==null) return false;
        // The same row lock is acquired by event lifecycle writers. Claim is the dispatch authorization point.
        Event event=events.findForApproval(delivery.getSourceEventId()).orElse(null);
        if (event==null) return false;
        if (event.getLifecycleVersion()!=delivery.getSourceEventVersion()) return false;
        EventStatus expected=switch(delivery.getKind()) {
            case EVENT_APPROVED, EVENT_ANNOUNCEMENT, EVENT_POSTPONED -> EventStatus.APPROVED;
            case EVENT_REJECTED -> EventStatus.REJECTED;
            case EVENT_CANCELLED -> EventStatus.CANCELLED;
            default -> null;
        };
        if (event.getStatus()!=expected) return false;
        if (delivery.getKind()==Kind.EVENT_APPROVED || delivery.getKind()==Kind.EVENT_ANNOUNCEMENT ||
                delivery.getKind()==Kind.EVENT_POSTPONED)
            return delivery.getSourceEventDate()!=null && delivery.getSourceEventDate().equals(event.getEventDate());
        return true;
    }
    private static Instant now(){return Instant.now().truncatedTo(ChronoUnit.MICROS);}
    private static void finish(MailDelivery d,Status status,String code,Instant now){
        d.setStatus(status);d.setOutcomeCode(code);d.setCompletedAt(now);
        d.setLeaseToken(null);d.setLeaseUntil(null);d.setNextAttemptAt(null);d.setRecipient(null);
    }
}

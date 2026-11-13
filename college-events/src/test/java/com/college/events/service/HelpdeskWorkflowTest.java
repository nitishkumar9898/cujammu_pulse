package com.college.events.service;

import com.college.events.dto.HelpdeskDtos.*;
import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class HelpdeskWorkflowTest {
    private final SupportTicketRepository tickets=mock(SupportTicketRepository.class);
    private final SupportTicketEntryRepository entries=mock(SupportTicketEntryRepository.class);
    private final UserRepository users=mock(UserRepository.class);
    private final FeatureFlagService flags=mock(FeatureFlagService.class);
    private final HelpdeskTicketNumberService numbers=mock(HelpdeskTicketNumberService.class);
    private final AuditService audit=mock(AuditService.class);
    private final NotificationRepository notifications=mock(NotificationRepository.class);
    private final jakarta.validation.ValidatorFactory validation=Validation.buildDefaultValidatorFactory();
    private final org.springframework.transaction.PlatformTransactionManager transactions=mock(org.springframework.transaction.PlatformTransactionManager.class);
    private final SupportHelpdeskService service=new SupportHelpdeskService(tickets,entries,users,new DomainAccess(users,mock(EventRepository.class)),flags,numbers,audit,notifications,validation.getValidator(),transactions);
    private final String key="00000000-0000-0000-0000-000000000001";
    private User owner;
    private SupportTicket ticket;

    @BeforeEach void setup(){
        when(transactions.getTransaction(any())).thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus());
        owner=account(1,Role.STUDENT);authenticate(owner);when(flags.isFeatureEnabled("MODULE_HELPDESK")).thenReturn(true);
        when(tickets.lockOwner(1L)).thenReturn(Optional.of(owner));when(numbers.nextNumber()).thenReturn("CUJ-TKT-202610-0001");
        when(tickets.saveAndFlush(any())).thenAnswer(call->{SupportTicket t=call.getArgument(0);if(t.getId()==null)t.setId(10L);return t;});
        ticket=SupportTicket.builder().id(10L).publicId(key).ticketNumber("CUJ-TKT-202610-0001").createdBy(owner).subject("Synthetic request")
                .description("Private synthetic description").category("GENERAL").status("OPEN").priority("MEDIUM").assignedOffice("STUDENT_AFFAIRS").build();
        when(tickets.lockTicket(key)).thenReturn(Optional.of(ticket));when(tickets.findByPublicId(key)).thenReturn(Optional.of(ticket));
    }
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();validation.close();}
    @Test void anonymousAndDisabledAccountsCannotRead(){
        SecurityContextHolder.clearContext();assertThrows(AccessDeniedException.class,()->service.ticket(key));
        authenticate(owner);owner.setEnabled(false);assertThrows(AccessDeniedException.class,()->service.ticket(key));
    }
    @Test void unrelatedMemberCannotReadThreadOrReply(){
        authenticate(account(2,Role.STUDENT));assertThrows(AccessDeniedException.class,()->service.thread(key,0));
        assertThrows(AccessDeniedException.class,()->service.reply(key,new Reply(key,0,"Private reply")));
        verifyNoInteractions(entries);
    }
    @Test void runtimeDisabledDeniesDirectServiceAccess(){when(flags.isFeatureEnabled("MODULE_HELPDESK")).thenReturn(false);
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.ticket(key)).getStatusCode().value());}
    @Test void requesterCannotSetEffectivePriorityOrAwardSupportPrivileges() throws Exception {
        var saved=service.create(create("Original request"));assertEquals("MEDIUM",saved.priority());assertEquals("URGENT",saved.requestedPriority());assertFalse(saved.canManage());
        String json=new ObjectMapper().writeValueAsString(saved);assertFalse(json.contains("password"));assertFalse(json.contains("createdByEmail"));assertFalse(json.contains("private@example.invalid"));
        verify(notifications).save(any());verify(audit).logAction(anyString(),eq("STUDENT"),eq("HELPDESK_CREATED"),anyString());
    }
    @Test void retriedCreateUsesSameTicketAndDoesNotAllocateOrNotifyAgain(){
        service.create(create("Original request"));var capture=org.mockito.ArgumentCaptor.forClass(SupportTicket.class);verify(tickets).saveAndFlush(capture.capture());
        when(tickets.findByCreatedByIdAndRequestKey(1L,key)).thenReturn(Optional.of(capture.getValue()));
        assertEquals(capture.getValue().getPublicId(),service.create(create("Original request")).id());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.create(create("Changed request"))).getStatusCode().value());
        verify(numbers,times(1)).nextNumber();verify(notifications,times(1)).save(any());
    }
    @Test void unassignedIntakeNotifiesEnabledAdminQueueWithoutPrivateDetails(){
        var disabled=account(3,Role.ADMIN);disabled.setEnabled(false);
        when(users.findByRole(Role.ADMIN)).thenReturn(java.util.List.of(account(2,Role.ADMIN),disabled));
        service.create(create("Sensitive synthetic subject"));
        var captured=org.mockito.ArgumentCaptor.forClass(Notification.class);verify(notifications,times(2)).save(captured.capture());
        for(var notification:captured.getAllValues()){assertFalse(notification.getMessage().contains("Sensitive"));assertFalse(notification.getMessage().contains("private@example.invalid"));}
        assertEquals(java.util.Set.of(1L,2L),captured.getAllValues().stream().map(n->n.getUser().getId()).collect(java.util.stream.Collectors.toSet()));
    }
    @Test void userCannotTriageAndAdminCannotResolveOwnGrievance(){
        assertThrows(AccessDeniedException.class,()->service.update(key,update("IN_PROGRESS")));
        owner.setRole(Role.ADMIN);authenticate(owner);assertThrows(AccessDeniedException.class,()->service.update(key,update("IN_PROGRESS")));
    }
    @Test void strictTransitionsAndResolutionNotesRequired(){
        authenticate(account(2,Role.ADMIN));assertEquals(409,assertThrows(ResponseStatusException.class,()->service.update(key,update("RESOLVED"))).getStatusCode().value());
        ticket.setStatus("IN_PROGRESS");assertEquals(400,assertThrows(ResponseStatusException.class,()->service.update(key,update("RESOLVED"))).getStatusCode().value());
    }
    @Test void orphanLegacyTicketProducesReviewConflictInsteadOfNullDereference(){
        ticket.setCreatedBy(null);authenticate(account(2,Role.ADMIN));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.update(key,update("IN_PROGRESS"))).getStatusCode().value());
    }
    @Test void resolutionAcknowledgesEscalationAndPreservesTimeline(){
        authenticate(account(2,Role.ADMIN));ticket.setStatus("IN_PROGRESS");ticket.setEscalated(true);
        assertFalse(service.update(key,new Update(key,0,"RESOLVED","HIGH","TECHNICAL",null,"Checked and resolved")).escalated());
        var entry=org.mockito.ArgumentCaptor.forClass(SupportTicketEntry.class);verify(entries).saveAndFlush(entry.capture());assertTrue(entry.getValue().getBody().contains("Checked and resolved"));
    }
    @Test void assignedStaffCanProgressButCannotReassign(){
        authenticate(account(3,Role.STAFF));ticket.setAssignedStaffId(3L);
        var ok=new Update(key,0,"IN_PROGRESS","HIGH","STUDENT_AFFAIRS",3L,null);
        assertEquals("IN_PROGRESS",service.update(key,ok).status());
        assertThrows(AccessDeniedException.class,()->service.update(key,new Update("00000000-0000-0000-0000-000000000002",0,"IN_PROGRESS","HIGH","TECHNICAL",3L,null)));
    }
    @Test void persistedRoleLossRemovesAssignedStaffAccess(){
        ticket.setAssignedStaffId(3L);authenticate(account(3,Role.STAFF));when(users.findById(3L)).thenReturn(Optional.of(account(3,Role.STUDENT)));
        assertThrows(AccessDeniedException.class,()->service.ticket(key));
    }
    @Test void assignmentCannotPromoteStudentToSupport(){
        authenticate(account(2,Role.ADMIN));when(users.findById(5L)).thenReturn(Optional.of(account(5,Role.STUDENT)));
        assertThrows(AccessDeniedException.class,()->service.update(key,new Update(key,0,"IN_PROGRESS","MEDIUM","TECHNICAL",5L,null)));
    }
    @Test void staleRevisionCannotWriteReply(){ticket.setVersion(2);
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.reply(key,new Reply(key,1,"Reply"))).getStatusCode().value());verify(entries,never()).saveAndFlush(any());}
    @Test void requesterCanReopenAndEscalateWithoutSelfRaisingPriority(){
        ticket.setStatus("CLOSED");ticket.setResolutionNotes("Prior resolution");
        assertEquals("OPEN",service.reopen(key,new OwnerAction(key,0,"The issue returned")).status());assertNull(ticket.getResolvedAt());
        assertTrue(service.escalate(key,new OwnerAction("00000000-0000-0000-0000-000000000002",0,"Review requested")).escalated());assertEquals("MEDIUM",ticket.getPriority());
    }
    @Test void closedTicketRequiresExplicitReopenBeforeReply(){ticket.setStatus("CLOSED");
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.reply(key,new Reply(key,0,"Reply"))).getStatusCode().value());}
    @Test void invalidStatusAndCategoryRejectedInsideService(){
        assertEquals(400,assertThrows(ResponseStatusException.class,()->service.create(new Create(key,"Subject","Description","HACKED","URGENT"))).getStatusCode().value());
        verify(numbers,never()).nextNumber();
    }
    @Test void legacyLinkPreservesNumberAndOwnerAndNeverRelinksExistingUuid(){
        ticket.setPublicId(null);when(tickets.lockLegacyTicket(ticket.getTicketNumber())).thenReturn(Optional.of(ticket));
        var command=new LegacyLink(key,0,ticket.getTicketNumber());String first=service.linkLegacy(command).id();
        assertNotNull(first);assertEquals(first,service.linkLegacy(command).id());assertSame(owner,ticket.getCreatedBy());assertEquals("CUJ-TKT-202610-0001",ticket.getTicketNumber());
        verify(audit,times(1)).logAction(anyString(),anyString(),eq("HELPDESK_LINKED"),anyString());
    }
    private Create create(String subject){return new Create(key,subject,"Synthetic private detail","GENERAL","URGENT");}
    private Update update(String status){return new Update(key,0,status,"MEDIUM","STUDENT_AFFAIRS",null,null);}
    private User account(long id,Role role){return User.builder().id(id).name("Synthetic "+id).email("private@example.invalid").password("synthetic-hash").role(role).enabled(true).build();}
    private void authenticate(User actor){when(users.findById(actor.getId())).thenReturn(Optional.of(actor));SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,null,actor.getAuthorities()));}
}

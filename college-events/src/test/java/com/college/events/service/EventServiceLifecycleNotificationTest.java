package com.college.events.service;

import com.college.events.model.*;
import com.college.events.repository.*;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.mail.javamail.JavaMailSender;
import jakarta.mail.internet.MimeMessage;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;

@org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=create-drop","spring.sql.init.mode=never",
        "logging.file.name=target/test-logs/mail-lifecycle.log"},showSql=false)
@org.springframework.context.annotation.Import({MailOutboxService.class,EmailRetryService.class,EmailService.class,MailAnnouncementCampaignService.class,EmailSnapshotTransactionTest.Config.class})
@org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
class EventServiceLifecycleNotificationTest {
    private final EventRepository events = mock(EventRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final FileStorageService files = mock(FileStorageService.class);
    @Autowired private EmailService email;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JavaMailSender sender;
    private final RegistrationRepository registrations = mock(RegistrationRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final EventOperationsService operations = mock(EventOperationsService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final FeatureFlagService featureFlags = mock(FeatureFlagService.class);
    private final EventOperationsRepository operationLocks = mock(EventOperationsRepository.class);
    @Autowired private MailDeliveryRepository deliveries;
    @Autowired private MailAnnouncementCampaignRepository campaigns;
    @Autowired private MailAnnouncementRecipientRepository campaignRecipients;
    @Autowired private MailAnnouncementCampaignService campaignService;
    @Autowired private FeatureFlagRepository persistedFlags;
    @Autowired private UserRepository persistedUsers;
    @Autowired private EventRepository persistedEvents;
    private EventService service;
    private User persist(User user) {
        user.setId(null);user.setPassword("synthetic-hash");user.setEnabled(true);
        if(user.getName()==null)user.setName("Synthetic");
        return persistedUsers.saveAndFlush(user);
    }
    private void assertIntent(String recipient,MailDelivery.Kind kind) {
        org.junit.jupiter.api.Assertions.assertEquals(1,deliveries.findAll().stream()
            .filter(row->recipient.equals(row.getRecipient()) && row.getKind()==kind).count());
        verifyNoInteractions(sender);
    }

    @Test
    void repeatedApprovalAfterLostResponseDoesNotQueueDuplicateNotices() {
        User organizer=persist(User.builder().role(Role.ORGANIZER).email("organizer-approval@example.invalid").build());
        Event event=Event.builder().id(24L).title("Synthetic approval").organizer(organizer)
                .targetDepartment("ALL").status(EventStatus.PENDING).assignedReviewerId(Long.MAX_VALUE).reviewerAssignedByUserId(Long.MAX_VALUE).reviewerAssignedAt(LocalDateTime.now()).lastReviewerAssignmentRevision(0L).build();
        when(events.findForApproval(24L)).thenReturn(Optional.of(event));
        when(users.findByRole(Role.STUDENT)).thenReturn(List.of());
        when(users.countByRole(Role.STUDENT)).thenReturn(0L);

        service.approveEvent(24L, 0L);
        service.publishApprovedEvent(24L, 0L);

        org.junit.jupiter.api.Assertions.assertEquals(EventStatus.APPROVED,event.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(1,deliveries.findAll().stream()
                .filter(row->row.getKind()==MailDelivery.Kind.EVENT_APPROVED).count());
        org.junit.jupiter.api.Assertions.assertEquals(0,deliveries.findAll().stream()
                .filter(row->row.getKind()==MailDelivery.Kind.EVENT_ANNOUNCEMENT).count());
        verify(operations,times(1)).ensureWorkspace(event);
        verify(users,times(1)).findByRole(Role.STUDENT);
    }

    @Test
    void approvalDoesNotCommitWhenAnnouncementAudienceExceedsCampaignSafetyLimit() {
        User organizer=persist(User.builder().role(Role.ORGANIZER).email("organizer-large@example.invalid").build());
        Event event=Event.builder().id(25L).title("Synthetic large audience").organizer(organizer)
                .targetDepartment("ALL").status(EventStatus.PENDING).assignedReviewerId(Long.MAX_VALUE).reviewerAssignedByUserId(Long.MAX_VALUE).reviewerAssignedAt(LocalDateTime.now()).lastReviewerAssignmentRevision(0L).build();
        when(events.findForApproval(25L)).thenReturn(Optional.of(event));
        when(users.countByRole(Role.STUDENT)).thenReturn((long) MailAnnouncementCampaignService.MAX_CAMPAIGN_AUDIENCE + 1);

        service.approveEvent(25L, 0L);
        var error=org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,()->service.publishApprovedEvent(25L, 0L));

        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("campaign safety limit"));
        org.junit.jupiter.api.Assertions.assertEquals(EventStatus.APPROVED_PENDING_PUBLICATION,event.getStatus());
        verify(users,never()).findByRole(Role.STUDENT);
        verify(operations,never()).ensureWorkspace(event);
        org.junit.jupiter.api.Assertions.assertEquals(0,deliveries.count());
    }

    @Test
    void largeAudienceSnapshotResumesInBoundedBatchesAndQueuesEachRecipientOnce() {
        Event event=persistedEvents.saveAndFlush(Event.builder().title("Synthetic campaign")
                .description("Synthetic campaign fixture").venue("Synthetic venue")
                .eventDate(LocalDateTime.now().plusDays(3)).maxSeats(1000)
                .organizer(persist(User.builder().role(Role.ORGANIZER).email("campaign-owner@example.invalid").build()))
                .targetDepartment("ALL").status(EventStatus.PENDING).assignedReviewerId(Long.MAX_VALUE).reviewerAssignedByUserId(Long.MAX_VALUE).reviewerAssignedAt(LocalDateTime.now()).lastReviewerAssignmentRevision(0L).build());
        List<User> students=java.util.stream.IntStream.range(0,501).mapToObj(index -> persist(User.builder()
                .role(Role.STUDENT).email("campaign-student-"+index+"@example.invalid").build())).toList();
        persistedFlags.saveAndFlush(FeatureFlag.builder().featureKey("MODULE_EVENTS").displayName("Events").enabled(true).build());
        persistedFlags.saveAndFlush(FeatureFlag.builder().featureKey("MODULE_NOTIFICATIONS").displayName("Notifications").enabled(true).build());
        when(users.countByRole(Role.STUDENT)).thenReturn((long)students.size());
        EventService transactionalService=new EventService(persistedEvents,categories,files,email,registrations,
                users,operations,notifications,operationLocks);
        ReflectionTestUtils.setField(transactionalService,"completionFlags",featureFlags);
        configureProposalAuthority(transactionalService);
        ReflectionTestUtils.setField(transactionalService,"announcementCampaigns",campaignService);

        new TransactionTemplate(transactionManager).execute(status->{transactionalService.approveEvent(event.getId(), 0L);transactionalService.publishApprovedEvent(event.getId(), 1L);return null;});
        String campaignId=campaigns.findAll().get(0).getId();

        org.junit.jupiter.api.Assertions.assertEquals(501,campaignRecipients.countByCampaignIdAndStatus(
                campaignId,MailAnnouncementRecipient.Status.PENDING));
        ExecutorService workers=Executors.newFixedThreadPool(2);
        CountDownLatch start=new CountDownLatch(1);
        try {
            Future<Integer> first=workers.submit(()->{start.await();return campaignService.processNextBatch();});
            Future<Integer> second=workers.submit(()->{start.await();return campaignService.processNextBatch();});
            start.countDown();
            org.junit.jupiter.api.Assertions.assertEquals(MailAnnouncementCampaignService.DISPATCH_BATCH_SIZE,
                    first.get(15,TimeUnit.SECONDS));
            org.junit.jupiter.api.Assertions.assertEquals(MailAnnouncementCampaignService.DISPATCH_BATCH_SIZE,
                    second.get(15,TimeUnit.SECONDS));
        } catch(Exception failure) {
            throw new AssertionError("Concurrent campaign batches must finish without duplicate or lost recipients",failure);
        } finally { workers.shutdownNow(); }
        org.junit.jupiter.api.Assertions.assertEquals(MailAnnouncementCampaign.Status.PROCESSING,
                campaigns.findById(campaignId).orElseThrow().getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(301,campaignRecipients.countByCampaignIdAndStatus(
                campaignId,MailAnnouncementRecipient.Status.PENDING));
        for(int batch=0;batch<3;batch++)
            org.junit.jupiter.api.Assertions.assertEquals(MailAnnouncementCampaignService.DISPATCH_BATCH_SIZE,
                    campaignService.processNextBatch());
        org.junit.jupiter.api.Assertions.assertEquals(1,campaignService.processNextBatch());
        org.junit.jupiter.api.Assertions.assertEquals(MailAnnouncementCampaign.Status.COMPLETED,
                campaigns.findById(campaignId).orElseThrow().getStatus());
        List<MailDelivery> announcementRows=deliveries.findAll().stream()
                .filter(row->row.getKind()==MailDelivery.Kind.EVENT_ANNOUNCEMENT).toList();
        org.junit.jupiter.api.Assertions.assertEquals(501,announcementRows.size());
        java.util.Set<Long> expectedRecipients=students.stream().map(User::getId)
                .collect(java.util.stream.Collectors.toSet());
        java.util.Set<Long> actualRecipients=announcementRows.stream().map(MailDelivery::getRecipientUserId)
                .collect(java.util.stream.Collectors.toSet());
        org.junit.jupiter.api.Assertions.assertEquals(501,actualRecipients.size());
        org.junit.jupiter.api.Assertions.assertEquals(expectedRecipients,actualRecipients);
        org.junit.jupiter.api.Assertions.assertEquals(0,campaignRecipients.countByCampaignIdAndStatus(
                campaignId,MailAnnouncementRecipient.Status.PENDING));
        org.junit.jupiter.api.Assertions.assertEquals(0,campaignService.processNextBatch());
        org.junit.jupiter.api.Assertions.assertEquals(501,deliveries.findAll().stream()
                .filter(row->row.getKind()==MailDelivery.Kind.EVENT_ANNOUNCEMENT).count());
        org.junit.jupiter.api.Assertions.assertEquals(1,campaignService.scrubTerminalCampaigns(
                campaigns.findById(campaignId).orElseThrow().getTerminalAt().plus(java.time.Duration.ofDays(31))));
        org.junit.jupiter.api.Assertions.assertFalse(campaigns.existsById(campaignId));
        verifyNoInteractions(sender);
    }

    @Test
    void departmentAnnouncementUsesOnlyOfficeVerifiedDepartmentBindings() {
        User organizer=persist(User.builder().role(Role.ORGANIZER).email("organizer-dept@example.invalid").build());
        Event event=Event.builder().id(26L).title("Synthetic department event").organizer(organizer)
                .eventDate(LocalDateTime.now().plusDays(1))
                .targetDepartment("  computer science and engineering ").status(EventStatus.PENDING).assignedReviewerId(Long.MAX_VALUE).reviewerAssignedByUserId(Long.MAX_VALUE).reviewerAssignedAt(LocalDateTime.now()).lastReviewerAssignmentRevision(0L).build();
        Department department=Department.builder().id(91L).code("CSE").name("Computer Science and Engineering").active(true).build();
        User verified=persist(User.builder().role(Role.STUDENT).email("verified-student@example.invalid").build());
        when(events.findForApproval(26L)).thenReturn(Optional.of(event));
        when(departments.findByCode("COMPUTER SCIENCE AND ENGINEERING")).thenReturn(Optional.empty());
        when(departments.findByNameIgnoreCase("computer science and engineering")).thenReturn(Optional.of(department));
        when(users.countByRoleAndVerifiedDepartmentId(Role.STUDENT,91L)).thenReturn(1L);
        when(users.findByRoleAndVerifiedDepartmentId(Role.STUDENT,91L)).thenReturn(List.of(verified));

        service.approveEvent(26L, 0L);
        service.publishApprovedEvent(26L, 0L);

        org.junit.jupiter.api.Assertions.assertEquals(EventStatus.APPROVED,event.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals("  computer science and engineering ",event.getTargetDepartment());
        verify(users).countByRoleAndVerifiedDepartmentId(Role.STUDENT,91L);
        verify(users).findByRoleAndVerifiedDepartmentId(Role.STUDENT,91L);
        verify(users,never()).countByRoleAndDepartment(any(),anyString());
        verify(users,never()).findByRoleAndDepartment(any(),anyString());
    }

    @Test
    void departmentAnnouncementFailsClosedWhenVerifiedDepartmentRegistryIsUnavailable() {
        User organizer=persist(User.builder().role(Role.ORGANIZER).email("organizer-unverified@example.invalid").build());
        Event event=Event.builder().id(27L).title("Synthetic department event").organizer(organizer)
                .targetDepartment("CSE").status(EventStatus.PENDING).assignedReviewerId(Long.MAX_VALUE).reviewerAssignedByUserId(Long.MAX_VALUE).reviewerAssignedAt(LocalDateTime.now()).lastReviewerAssignmentRevision(0L).build();
        when(events.findForApproval(27L)).thenReturn(Optional.of(event));
        ReflectionTestUtils.setField(service,"recipientDepartments",null);

        service.approveEvent(27L, 0L);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,()->service.publishApprovedEvent(27L, 0L));

        org.junit.jupiter.api.Assertions.assertEquals(EventStatus.APPROVED_PENDING_PUBLICATION,event.getStatus());
        verify(operations,never()).ensureWorkspace(event);
        org.junit.jupiter.api.Assertions.assertEquals(0,deliveries.count());
    }

    @Test
    void departmentAnnouncementFailsClosedWhenNoVerifiedRecipientsAreProvisioned() {
        User organizer=persist(User.builder().role(Role.ORGANIZER).email("organizer-empty-dept@example.invalid").build());
        Event event=Event.builder().id(28L).title("Synthetic department event").organizer(organizer)
                .targetDepartment("CSE").status(EventStatus.PENDING).assignedReviewerId(Long.MAX_VALUE).reviewerAssignedByUserId(Long.MAX_VALUE).reviewerAssignedAt(LocalDateTime.now()).lastReviewerAssignmentRevision(0L).build();
        when(events.findForApproval(28L)).thenReturn(Optional.of(event));
        when(departments.findByCode("CSE")).thenReturn(Optional.of(
                Department.builder().id(92L).code("CSE").name("Computer Science and Engineering").active(true).build()));
        when(users.countByRoleAndVerifiedDepartmentId(Role.STUDENT,92L)).thenReturn(0L);

        service.approveEvent(28L, 0L);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,()->service.publishApprovedEvent(28L, 0L));

        org.junit.jupiter.api.Assertions.assertEquals(EventStatus.APPROVED_PENDING_PUBLICATION,event.getStatus());
        verify(users,never()).findByRoleAndDepartment(any(),anyString());
        verify(operations,never()).ensureWorkspace(event);
        org.junit.jupiter.api.Assertions.assertEquals(0,deliveries.count());
    }

    @Test
    void listGrowthAfterApprovalFlushRollsBackPersistedEventAndOrganizerMailIntent() {
        User organizer=persist(User.builder().role(Role.ORGANIZER).email("organizer-race@example.invalid").build());
        Event event=persistedEvents.saveAndFlush(Event.builder().title("Synthetic audience race")
                .description("Synthetic event used to verify transaction rollback")
                .venue("Synthetic venue").eventDate(LocalDateTime.now().plusDays(2)).maxSeats(50)
                .organizer(organizer).targetDepartment("ALL").status(EventStatus.PENDING).assignedReviewerId(Long.MAX_VALUE).reviewerAssignedByUserId(Long.MAX_VALUE).reviewerAssignedAt(LocalDateTime.now()).lastReviewerAssignmentRevision(0L).build());
        when(users.countByRole(Role.STUDENT)).thenReturn((long) EmailService.MAX_SYNCHRONOUS_AUDIENCE);
        when(users.findByRole(Role.STUDENT)).thenReturn(java.util.stream.IntStream.rangeClosed(0, EmailService.MAX_SYNCHRONOUS_AUDIENCE)
                .mapToObj(index -> User.builder().role(Role.STUDENT).email("student-"+index+"@example.invalid").build()).toList());
        EventService transactionalService=new EventService(persistedEvents,categories,files,email,registrations,
                users,operations,notifications,operationLocks);
        ReflectionTestUtils.setField(transactionalService,"completionFlags",featureFlags);
        configureProposalAuthority(transactionalService);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,()->new TransactionTemplate(transactionManager)
                .execute(status->{transactionalService.approveEvent(event.getId(), 0L);transactionalService.publishApprovedEvent(event.getId(), 0L);return null;}));

        org.junit.jupiter.api.Assertions.assertEquals(EventStatus.PENDING,
                persistedEvents.findById(event.getId()).orElseThrow().getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(0,deliveries.count());
        verifyNoInteractions(sender);
    }

    @BeforeEach void setup() {
        clearInvocations(sender);campaignRecipients.deleteAll();campaigns.deleteAll();deliveries.deleteAll();persistedEvents.deleteAll();persistedUsers.deleteAll();persistedFlags.deleteAll();
        service=new EventService(events,categories,files,email,registrations,users,operations,notifications,operationLocks);
        ReflectionTestUtils.setField(service,"completionFlags",featureFlags);
        configureProposalAuthority(service);
        ReflectionTestUtils.setField(service,"recipientDepartments",departments);
        when(operationLocks.findForUpdate(12L)).thenReturn(Optional.of(EventOperations.builder().build()));
    }

    private void configureProposalAuthority(EventService target) {
        var access=mock(com.college.events.security.DomainAccess.class);
        when(access.requireRole(Role.ADMIN)).thenReturn(User.builder().id(Long.MAX_VALUE).role(Role.ADMIN).email("synthetic-reviewer@example.invalid").build());
        ReflectionTestUtils.setField(target,"completionAccess",access);
        ReflectionTestUtils.setField(target,"completionAudit",mock(AuditService.class));
    }

    @Test
    void eventApprovalChecksLockedModuleControlBeforeReadingOrMutatingEvent() {
        doThrow(new IllegalStateException("Required feature is disabled"))
                .when(featureFlags).requireEnabledForUpdate("MODULE_EVENTS");

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,()->service.approveEvent(999L, 0L));

        verify(featureFlags).requireEnabledForUpdate("MODULE_EVENTS");
        verifyNoInteractions(events,operations,users);
    }

    @Test
    void cancellationChecksModuleAndActionControlsBeforeTakingEventLocks() {
        doThrow(new IllegalStateException("Required feature is disabled"))
                .when(featureFlags).requireEnabledForUpdate("ACTION_EVENT_CANCEL");

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,()->service.cancelEvent(998L,null));

        org.mockito.InOrder order=inOrder(featureFlags);
        order.verify(featureFlags).requireEnabledForUpdate("MODULE_EVENTS");
        order.verify(featureFlags).requireEnabledForUpdate("ACTION_EVENT_CANCEL");
        verifyNoInteractions(operationLocks,events,registrations,notifications);
    }

    @Test
    void postponementChecksModuleAndActionControlsBeforeTakingEventLocks() {
        doThrow(new IllegalStateException("Required feature is disabled"))
                .when(featureFlags).requireEnabledForUpdate("ACTION_EVENT_POSTPONE");

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                ()->service.postponeEvent(997L,LocalDateTime.now().plusDays(1),"synthetic",null));

        org.mockito.InOrder order=inOrder(featureFlags);
        order.verify(featureFlags).requireEnabledForUpdate("MODULE_EVENTS");
        order.verify(featureFlags).requireEnabledForUpdate("ACTION_EVENT_POSTPONE");
        verifyNoInteractions(operationLocks,events,registrations,notifications);
    }

    @Test
    void cancellationWritesInboxNoticesForConfirmedAndWaitlistedUsers() {
        User organizer = User.builder().id(4L).role(Role.ORGANIZER).email("organizer@example.invalid").build();
        User confirmed = persist(User.builder().id(8L).role(Role.STUDENT).name("Confirmed").email("confirmed@example.invalid").build());
        User waitlisted = persist(User.builder().id(9L).role(Role.STUDENT).name("Waitlisted").email("waitlisted@example.invalid").build());
        User attended = User.builder().id(10L).role(Role.STUDENT).name("Attended").build();
        Event event = Event.builder().id(12L).title("Synthetic campus event").organizer(organizer)
                .status(EventStatus.APPROVED).build();
        when(events.findForApproval(12L)).thenReturn(Optional.of(event));
        var registrationRows = List.of(
                Registration.builder().user(confirmed).status(RegistrationStatus.REGISTERED).build(),
                Registration.builder().user(waitlisted).status(RegistrationStatus.WAITLISTED).build(),
                Registration.builder().user(attended).status(RegistrationStatus.ATTENDED).build());
        // Respect the repository's status filter; do not inject rows its JPQL query excludes.
        when(registrations.findNotifiableByEvent(eq(event), anyCollection())).thenAnswer(invocation -> {
            java.util.Collection<RegistrationStatus> statuses = invocation.getArgument(1);
            return registrationRows.stream().filter(row -> statuses.contains(row.getStatus())).toList();
        });

        service.cancelEvent(12L, organizer);

        verify(notifications).createNotification(eq(confirmed), contains("has been cancelled"), eq("/events/12"));
        verify(notifications).createNotification(eq(waitlisted), contains("has been cancelled"), eq("/events/12"));
        verify(notifications, never()).createNotification(eq(attended), anyString(), anyString());
        verify(registrations).findNotifiableByEvent(event,
                java.util.EnumSet.of(RegistrationStatus.REGISTERED, RegistrationStatus.WAITLISTED));
        assertIntent("confirmed@example.invalid",MailDelivery.Kind.EVENT_CANCELLED);
        assertIntent("waitlisted@example.invalid",MailDelivery.Kind.EVENT_CANCELLED);
        verify(operationLocks).findForUpdate(12L);
    }

    @Test
    void repeatedCancellationDoesNotRepeatEmailsOrInboxNotices() {
        User organizer = User.builder().id(4L).role(Role.ORGANIZER).build();
        Event event = Event.builder().id(12L).title("Synthetic campus event").organizer(organizer)
                .status(EventStatus.CANCELLED).build();
        when(events.findForApproval(12L)).thenReturn(Optional.of(event));

        service.cancelEvent(12L, organizer);

        verifyNoInteractions(registrations, sender, notifications);
        org.junit.jupiter.api.Assertions.assertEquals(0,deliveries.count());
    }

    @Test
    void cancellationIntentJoinsDomainCommitWithoutSending() {
        User organizer = User.builder().id(4L).role(Role.ORGANIZER).email("organizer@example.invalid").build();
        Event event = Event.builder().id(12L).title("Synthetic campus event").organizer(organizer)
                .status(EventStatus.APPROVED).build();
        when(events.findForApproval(12L)).thenReturn(Optional.of(event));
        var participant=persist(User.builder().id(8L).role(Role.STUDENT).name("Original name").email("original@example.invalid").build());
        when(registrations.findNotifiableByEvent(eq(event),anyCollection())).thenReturn(List.of(Registration.builder().user(participant).event(event).build()));
        new TransactionTemplate(transactionManager).execute(status->{
            service.cancelEvent(12L, organizer);
            verify(sender, never()).send(any(MimeMessage.class));
            event.setTitle("Changed title");participant.setEmail("changed@example.invalid");return null;
        });
        assertIntent("original@example.invalid",MailDelivery.Kind.EVENT_CANCELLED);
    }

    @Test
    void cancellationEmailIsSuppressedOnRollback() {
        User organizer = User.builder().id(4L).role(Role.ORGANIZER).email("organizer@example.invalid").build();
        Event event = Event.builder().id(12L).title("Synthetic campus event").organizer(organizer)
                .status(EventStatus.APPROVED).build();
        when(events.findForApproval(12L)).thenReturn(Optional.of(event));
        var participant=persist(User.builder().id(8L).role(Role.STUDENT).name("Synthetic participant").email("rollback@example.invalid").build());
        when(registrations.findNotifiableByEvent(eq(event),anyCollection())).thenReturn(List.of(Registration.builder().user(participant).event(event).build()));
        new TransactionTemplate(transactionManager).execute(status->{
            service.cancelEvent(12L, organizer);
            status.setRollbackOnly();return null;
        });
        verifyNoInteractions(sender);
        org.junit.jupiter.api.Assertions.assertEquals(0,deliveries.count());
    }

    @Test
    void postponementNotifiesAffectedUsersWithoutLeakingPrivateReason() {
        User organizer = User.builder().id(4L).role(Role.ORGANIZER).build();
        User participant = persist(User.builder().id(8L).role(Role.STUDENT).email("participant@example.invalid").build());
        Event event = Event.builder().id(12L).title("Synthetic campus event").organizer(organizer)
                .status(EventStatus.APPROVED).eventDate(LocalDateTime.parse("2030-05-01T10:00:00")).build();
        when(events.findForApproval(12L)).thenReturn(Optional.of(event));
        when(registrations.findNotifiableByEvent(eq(event), anyCollection())).thenReturn(List.of(
                Registration.builder().user(participant).status(RegistrationStatus.REGISTERED).build()));
        String privateReason = "confidential staffing details";

        service.postponeEvent(12L, LocalDateTime.parse("2030-05-02T10:00:00"), privateReason, organizer);

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(notifications).createNotification(eq(participant), message.capture(), eq("/events/12"));
        org.junit.jupiter.api.Assertions.assertTrue(message.getValue().contains("schedule"));
        org.junit.jupiter.api.Assertions.assertFalse(message.getValue().contains(privateReason));
        verify(operationLocks).findForUpdate(12L);
        assertIntent("participant@example.invalid",MailDelivery.Kind.EVENT_POSTPONED);
    }
}

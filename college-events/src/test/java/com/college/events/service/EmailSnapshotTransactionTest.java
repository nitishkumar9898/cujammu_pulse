package com.college.events.service;

import com.college.events.model.*;
import com.college.events.model.MailDelivery.*;
import com.college.events.repository.*;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real H2 transaction/proxy/lease tests. Isolated mock sender cannot contact SMTP. */
@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=create-drop","spring.sql.init.mode=never",
    "logging.file.name=target/test-logs/mail-outbox.log","app.email.backend=smtp",
    "spring.mail.username=synthetic@example.invalid","app.base-url=https://example.invalid"},showSql=false)
@Import({MailOutboxService.class,EmailRetryService.class,EmailService.class,EmailSnapshotTransactionTest.Config.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class EmailSnapshotTransactionTest {
    @TestConfiguration static class Config {
        @Bean JavaMailSender sender(){return mock(JavaMailSender.class);}
    }
    @Autowired MailOutboxService outbox;
    @Autowired EmailService email;
    @Autowired EmailRetryService worker;
    @Autowired JavaMailSender sender;
    @Autowired UserRepository users;
    @Autowired EventRepository events;
    @Autowired EventOperationsRepository eventOperations;
    @Autowired RegistrationRepository registrations;
    @Autowired FeatureFlagRepository flags;
    @Autowired MailDeliveryRepository deliveries;
    @Autowired PlatformTransactionManager manager;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager entityManager;
    TransactionTemplate tx;
    User user;
    @BeforeEach void setup(){
        reset(sender);tx=new TransactionTemplate(manager);deliveries.deleteAll();
        when(sender.createMimeMessage()).thenAnswer(call->new MimeMessage(jakarta.mail.Session.getInstance(new Properties())));
        user=users.saveAndFlush(User.builder().name("Synthetic").email(UUID.randomUUID()+"@example.invalid")
                .password("synthetic-hash").role(Role.STUDENT).enabled(true).build());
        for(String key:List.of("MODULE_NOTIFICATIONS","MODULE_EVENTS","MODULE_CERTIFICATES")){
            var flag=flags.findByFeatureKey(key).orElseGet(()->FeatureFlag.builder().featureKey(key).displayName(key).build());
            flag.setEnabled(true);flags.saveAndFlush(flag);
        }
        ReflectionTestUtils.setField(worker,"backend","smtp");
    }
    Registration registration(){return persistedRegistration(RegistrationStatus.REGISTERED,
            event(EventStatus.APPROVED,LocalDateTime.now().plusDays(1)));}
    Event event(EventStatus status,LocalDateTime date){
        Event saved=events.saveAndFlush(Event.builder().title("Synthetic event")
                .description("Synthetic event for mail source check").venue("Synthetic venue").eventDate(date).maxSeats(20)
                .organizer(user).status(status).build());
        eventOperations.saveAndFlush(EventOperations.builder().event(saved).build());
        return saved;
    }
    String enqueue(){return outbox.enqueue(user.getEmail(),Kind.CERTIFICATE);}
    Registration persistedRegistration(RegistrationStatus status, Event event) {
        return registrations.saveAndFlush(Registration.builder().user(user).event(event).status(status).build());
    }
    void ready(String id){tx.execute(s->{var row=deliveries.lock(id).orElseThrow();row.setNextAttemptAt(Instant.now().minusSeconds(1));return null;});}

    @Test void rollbackDiscardsIntentAndCannotSend(){
        tx.execute(s->{email.sendCancellationConfirmation(registration());assertEquals(1,deliveries.count());s.setRollbackOnly();return null;});
        assertEquals(0,deliveries.count());worker.processRetryQueue();verifyNoInteractions(sender);
    }
    @Test void commitPersistsSnapshotAndSeparateWorkerReconcilesWithoutManagedEntities() throws Exception {
        Registration registration=registration();String original=user.getEmail();
        tx.execute(s->{email.sendRegistrationConfirmation(registration);verifyNoInteractions(sender);
            registration.getUser().setEmail("changed-detached@example.invalid");registration.getEvent().setTitle("Changed");return null;});
        assertEquals(original,deliveries.findAll().getFirst().getRecipient());
        // Reconstructed worker demonstrates no volatile queue is required after application restart.
        var restarted=new EmailRetryService(sender,outbox,mock(Environment.class));
        var env=mock(Environment.class);when(env.getActiveProfiles()).thenReturn(new String[0]);ReflectionTestUtils.setField(restarted,"environment",env);
        ReflectionTestUtils.setField(restarted,"backend","smtp");ReflectionTestUtils.setField(restarted,"fromEmail","sender@example.invalid");
        ReflectionTestUtils.setField(restarted,"baseUrl","https://example.invalid");
        restarted.processRetryQueue();
        var captured=org.mockito.ArgumentCaptor.forClass(MimeMessage.class);verify(sender).send(captured.capture());
        assertEquals(original,captured.getValue().getAllRecipients()[0].toString());
        assertFalse(captured.getValue().getContent().toString().contains("Private"));
        var row=deliveries.findAll().getFirst();assertEquals(Status.PROVIDER_ACCEPTED,row.getStatus());assertNull(row.getRecipient());
        worker.processRetryQueue();verify(sender,times(1)).send(any(MimeMessage.class));
    }
    @Test void transactionlessCallerCreatesDurableIntentWithoutSending(){email.sendCancellationConfirmation(registration());assertEquals(1,deliveries.count());verifyNoInteractions(sender);}
    @Test void failedIntentIsNotSilentlyIgnored(){user.setEmail("missing@example.invalid");assertThrows(IllegalArgumentException.class,()->email.sendRegistrationConfirmation(registration()));verifyNoInteractions(sender);}
    @Test void workerRejectsDomainTransaction(){tx.execute(s->{assertThrows(IllegalStateException.class,worker::processRetryQueue);return null;});verifyNoInteractions(sender);}
    @Test void concurrentWorkersClaimOnlyOnce() throws Exception {
        String id=enqueue();var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            Callable<Optional<MailOutboxService.Claim>> task=()->{start.await();return outbox.claim(id);};
            var a=pool.submit(task);var b=pool.submit(task);start.countDown();
            assertEquals(1,(a.get(10,TimeUnit.SECONDS).isPresent()?1:0)+(b.get(10,TimeUnit.SECONDS).isPresent()?1:0));
            assertEquals(1,deliveries.findById(id).orElseThrow().getAttempts());
        }finally{pool.shutdownNow();}
    }
    @Test void abandonedLeaseIsReclaimedAndOldCompletionCannotOverwrite(){
        String id=enqueue();var first=outbox.claim(id).orElseThrow();
        tx.execute(s->{deliveries.lock(id).orElseThrow().setLeaseUntil(Instant.now().minusSeconds(1));return null;});
        var second=outbox.claim(id).orElseThrow();assertNotEquals(first.leaseToken(),second.leaseToken());
        outbox.complete(first,Status.PROVIDER_ACCEPTED,"SMTP_ACCEPTED");assertEquals(Status.LEASED,deliveries.findById(id).orElseThrow().getStatus());
        outbox.complete(second,Status.PROVIDER_ACCEPTED,"SMTP_ACCEPTED");assertEquals(Status.PROVIDER_ACCEPTED,deliveries.findById(id).orElseThrow().getStatus());
    }
    @Test void leasedDeliveryWithoutDeadlineIsRecoveredInsteadOfStallingThePoller(){
        String id=enqueue();
        tx.execute(s->{var row=deliveries.lock(id).orElseThrow();row.setStatus(Status.LEASED);row.setLeaseToken("stale-token");row.setLeaseUntil(null);row.setNextAttemptAt(null);return null;});

        assertTrue(outbox.due().contains(id));
        var claim=outbox.claim(id).orElseThrow();
        assertNotEquals("stale-token",claim.leaseToken());
        assertEquals(Status.LEASED,deliveries.findById(id).orElseThrow().getStatus());
    }
    @Test void queuedDeliveryWithoutRetryTimeIsDeadLetteredWithoutProviderCall(){
        String id=enqueue();
        tx.execute(s->{deliveries.lock(id).orElseThrow().setNextAttemptAt(null);return null;});

        assertTrue(outbox.due().contains(id));
        assertTrue(outbox.claim(id).isEmpty());
        var row=deliveries.findById(id).orElseThrow();
        assertEquals(Status.DEAD_LETTER,row.getStatus());assertEquals("SCHEDULE_MISSING",row.getOutcomeCode());
        assertNull(row.getRecipient());verifyNoInteractions(sender);
    }
    @Test void boundedFailureRetriesBecomeDurableDeadLetterWithoutPrivateProviderError(){
        String id=enqueue();doThrow(new MailSendException("private provider token")).when(sender).send(any(MimeMessage.class));
        for(int i=0;i<5;i++){ready(id);worker.processRetryQueue();}
        var row=deliveries.findById(id).orElseThrow();assertEquals(Status.DEAD_LETTER,row.getStatus());assertEquals(5,row.getAttempts());
        assertEquals("ATTEMPTS_EXHAUSTED",row.getOutcomeCode());assertNull(row.getRecipient());
        worker.processRetryQueue();verify(sender,times(5)).send(any(MimeMessage.class));
    }
    @Test void demoSuppressesAndNeverClaimsProviderAcceptance(){
        String id=enqueue();ReflectionTestUtils.setField(worker,"backend","console");worker.processRetryQueue();
        assertEquals(Status.SUPPRESSED,deliveries.findById(id).orElseThrow().getStatus());verifyNoInteractions(sender);
    }
    @Test void revokedAccountAndChangedAddressSuppressPendingDelivery(){
        String id=enqueue();user.setEnabled(false);users.saveAndFlush(user);worker.processRetryQueue();
        assertEquals("ACCOUNT_CHANGED",deliveries.findById(id).orElseThrow().getOutcomeCode());verifyNoInteractions(sender);
    }
    @Test void disabledFeatureSuppressesPendingDelivery(){
        String id=enqueue();var flag=flags.findByFeatureKey("MODULE_NOTIFICATIONS").orElseThrow();flag.setEnabled(false);flags.saveAndFlush(flag);
        worker.processRetryQueue();assertEquals("FEATURE_DISABLED",deliveries.findById(id).orElseThrow().getOutcomeCode());verifyNoInteractions(sender);
    }
    @Test void staleEventStatusSuppressesQueuedApprovalAndUnscopedLegacyNotice(){
        Event source=event(EventStatus.APPROVED,LocalDateTime.now().plusDays(1));
        String stale=outbox.enqueueEventNotice(user.getEmail(),Kind.EVENT_APPROVED,source.getId(),source.getEventDate(),source.getLifecycleVersion());
        String legacy=outbox.enqueue(user.getEmail(),Kind.EVENT_ANNOUNCEMENT);
        source.setStatus(EventStatus.CANCELLED);events.saveAndFlush(source);
        worker.processRetryQueue();
        assertEquals("SOURCE_EVENT_CHANGED",deliveries.findById(stale).orElseThrow().getOutcomeCode());
        assertEquals("SOURCE_EVENT_CHANGED",deliveries.findById(legacy).orElseThrow().getOutcomeCode());
        verifyNoInteractions(sender);
    }
    @Test void campaignNoticeRechecksStudentRoleAndVerifiedDepartmentAtMailClaim(){
        jdbc.update("update users set verified_department_id=? where id=?",300L,user.getId());entityManager.clear();
        Event source=event(EventStatus.APPROVED,LocalDateTime.now().plusDays(1));
        String departmentScoped=outbox.enqueueCampaignEventAnnouncement(user.getEmail(),source.getId(),
                source.getEventDate(),source.getLifecycleVersion(),300L);
        jdbc.update("update users set verified_department_id=? where id=?",301L,user.getId());entityManager.clear();
        worker.processRetryQueue();
        assertEquals("AUDIENCE_SCOPE_CHANGED",deliveries.findById(departmentScoped).orElseThrow().getOutcomeCode());
        verifyNoInteractions(sender);

        jdbc.update("update users set verified_department_id=? where id=?",300L,user.getId());entityManager.clear();
        String roleScoped=outbox.enqueueCampaignEventAnnouncement(user.getEmail(),source.getId(),
                source.getEventDate(),source.getLifecycleVersion(),300L);
        jdbc.update("update users set role=? where id=?",Role.FACULTY.name(),user.getId());entityManager.clear();

        worker.processRetryQueue();

        assertEquals("AUDIENCE_SCOPE_CHANGED",deliveries.findById(roleScoped).orElseThrow().getOutcomeCode());
        verifyNoInteractions(sender);
    }
    @Test void smallAudienceAnnouncementRechecksStudentRoleAtMailClaim(){
        jdbc.update("update users set verified_department_id=? where id=?",300L,user.getId());entityManager.clear();
        Event source=event(EventStatus.APPROVED,LocalDateTime.now().plusDays(1));
        email.sendNewEventAnnouncement(source,List.of(user));
        jdbc.update("update users set role=? where id=?",Role.FACULTY.name(),user.getId());entityManager.clear();

        worker.processRetryQueue();

        var row=deliveries.findAll().getFirst();
        assertEquals("AUDIENCE_SCOPE_CHANGED",row.getOutcomeCode());assertEquals(Status.SUPPRESSED,row.getStatus());
        verifyNoInteractions(sender);
    }
    @Test void smallAudienceAnnouncementRechecksVerifiedDepartmentAtMailClaim(){
        jdbc.update("update users set verified_department_id=? where id=?",300L,user.getId());entityManager.clear();
        user=users.findById(user.getId()).orElseThrow();
        Event source=event(EventStatus.APPROVED,LocalDateTime.now().plusDays(1));
        source.setTargetDepartment("CSE");events.saveAndFlush(source);
        email.sendNewEventAnnouncement(source,List.of(user));
        jdbc.update("update users set verified_department_id=? where id=?",301L,user.getId());entityManager.clear();

        worker.processRetryQueue();

        var row=deliveries.findAll().getFirst();
        assertEquals("AUDIENCE_SCOPE_CHANGED",row.getOutcomeCode());assertEquals(Status.SUPPRESSED,row.getStatus());
        verifyNoInteractions(sender);
    }
    @Test void allAudienceAnnouncementDoesNotFreezeAnUnrelatedDepartment(){
        jdbc.update("update users set verified_department_id=? where id=?",300L,user.getId());entityManager.clear();
        user=users.findById(user.getId()).orElseThrow();
        Event source=event(EventStatus.APPROVED,LocalDateTime.now().plusDays(1));
        email.sendNewEventAnnouncement(source,List.of(user));
        jdbc.update("update users set verified_department_id=? where id=?",301L,user.getId());entityManager.clear();

        worker.processRetryQueue();

        assertEquals(Status.PROVIDER_ACCEPTED,deliveries.findAll().getFirst().getStatus());
        verify(sender,times(1)).send(any(MimeMessage.class));
    }
    @Test void supersededPostponementDateIsSuppressedButCurrentSnapshotCanSend(){
        LocalDateTime announcedDate=LocalDateTime.now().plusDays(1);
        Event source=event(EventStatus.APPROVED,announcedDate);
        String stale=outbox.enqueueEventNotice(user.getEmail(),Kind.EVENT_POSTPONED,source.getId(),announcedDate,source.getLifecycleVersion());
        source.setEventDate(announcedDate.plusDays(2));source=events.saveAndFlush(source);
        worker.processRetryQueue();
        assertEquals("SOURCE_EVENT_CHANGED",deliveries.findById(stale).orElseThrow().getOutcomeCode());
        verifyNoInteractions(sender);

        String current=outbox.enqueueEventNotice(user.getEmail(),Kind.EVENT_POSTPONED,source.getId(),source.getEventDate(),source.getLifecycleVersion());
        worker.processRetryQueue();
        var currentDelivery=deliveries.findById(current).orElseThrow();
        assertEquals(Status.PROVIDER_ACCEPTED,currentDelivery.getStatus(),currentDelivery.getOutcomeCode());
        verify(sender,times(1)).send(any(MimeMessage.class));
    }
    @Test void eventRevisionPreventsAnAbaScheduleChangeFromRevivingAnOldNotice(){
        LocalDateTime dateA=LocalDateTime.now().plusDays(2);
        Event source=event(EventStatus.APPROVED,dateA);
        String stale=outbox.enqueueEventNotice(user.getEmail(),Kind.EVENT_POSTPONED,
                source.getId(),dateA,source.getLifecycleVersion());
        long originalVersion=source.getLifecycleVersion();
        source.setEventDate(dateA.plusDays(2));source=events.saveAndFlush(source);
        assertEquals(originalVersion+1,source.getLifecycleVersion());
        source.setEventDate(dateA);source=events.saveAndFlush(source);
        assertEquals(originalVersion+2,source.getLifecycleVersion());

        worker.processRetryQueue();
        assertEquals("SOURCE_EVENT_CHANGED",deliveries.findById(stale).orElseThrow().getOutcomeCode());
        verifyNoInteractions(sender);
    }
    @Test void staleRegistrationAndWaitlistNoticesAreSuppressedByCurrentState(){
        Event source=event(EventStatus.APPROVED,LocalDateTime.now().plusDays(2));
        Registration confirmed=persistedRegistration(RegistrationStatus.REGISTERED,source);
        String staleRegistration=outbox.enqueueRegistrationNotice(user.getEmail(),Kind.REGISTRATION,confirmed.getId());
        confirmed.setStatus(RegistrationStatus.CANCELLED);registrations.saveAndFlush(confirmed);

        Event waitlistEvent=event(EventStatus.APPROVED,LocalDateTime.now().plusDays(3));
        Registration waitlisted=persistedRegistration(RegistrationStatus.WAITLISTED,waitlistEvent);
        String staleWaitlist=outbox.enqueueRegistrationNotice(user.getEmail(),Kind.WAITLIST,waitlisted.getId());
        waitlisted.setStatus(RegistrationStatus.REGISTERED);registrations.saveAndFlush(waitlisted);
        String currentPromotion=outbox.enqueueRegistrationNotice(user.getEmail(),Kind.PROMOTION,waitlisted.getId());

        worker.processRetryQueue();
        assertEquals("SOURCE_REGISTRATION_CHANGED",deliveries.findById(staleRegistration).orElseThrow().getOutcomeCode());
        assertEquals("SOURCE_REGISTRATION_CHANGED",deliveries.findById(staleWaitlist).orElseThrow().getOutcomeCode());
        assertEquals(Status.PROVIDER_ACCEPTED,deliveries.findById(currentPromotion).orElseThrow().getStatus());
        verify(sender,times(1)).send(any(MimeMessage.class));
    }
    @Test void registrationConfirmationIsSuppressedAfterEventApprovalIsWithdrawn(){
        Event source=event(EventStatus.APPROVED,LocalDateTime.now().plusDays(2));
        Registration confirmed=persistedRegistration(RegistrationStatus.REGISTERED,source);
        String pending=outbox.enqueueRegistrationNotice(user.getEmail(),Kind.REGISTRATION,confirmed.getId());
        source.setStatus(EventStatus.CANCELLED);events.saveAndFlush(source);

        worker.processRetryQueue();

        assertEquals("SOURCE_REGISTRATION_CHANGED",deliveries.findById(pending).orElseThrow().getOutcomeCode());
        verifyNoInteractions(sender);
    }
    @Test void validWithdrawalNoticeSurvivesEventCancellationAndLegacyRegistrationNoticeFailsClosed(){
        Event source=event(EventStatus.CANCELLED,LocalDateTime.now().plusDays(2));
        Registration withdrawn=persistedRegistration(RegistrationStatus.CANCELLED,source);
        String withdrawal=outbox.enqueueRegistrationNotice(user.getEmail(),Kind.WITHDRAWAL,withdrawn.getId());
        String legacy=outbox.enqueue(user.getEmail(),Kind.REGISTRATION);
        User other=users.saveAndFlush(User.builder().name("Other synthetic account")
                .email(UUID.randomUUID()+"@example.invalid").password("synthetic-hash")
                .role(Role.STUDENT).enabled(true).build());
        String mismatchedOwner=outbox.enqueueRegistrationNotice(other.getEmail(),Kind.WITHDRAWAL,withdrawn.getId());
        worker.processRetryQueue();
        assertEquals(Status.PROVIDER_ACCEPTED,deliveries.findById(withdrawal).orElseThrow().getStatus());
        assertEquals("SOURCE_REGISTRATION_CHANGED",deliveries.findById(legacy).orElseThrow().getOutcomeCode());
        assertEquals("SOURCE_REGISTRATION_CHANGED",deliveries.findById(mismatchedOwner).orElseThrow().getOutcomeCode());
        verify(sender,times(1)).send(any(MimeMessage.class));
    }
    @Test void attendanceNoticeRequiresAttendedStateBeforeEnqueue(){
        Event source=event(EventStatus.COMPLETED,LocalDateTime.now().minusDays(1));
        Registration visit=persistedRegistration(RegistrationStatus.REGISTERED,source);
        email.sendAttendanceConfirmation(visit);
        worker.processRetryQueue();
        var early=deliveries.findAll().stream().filter(row->row.getStatus()==Status.SUPPRESSED).findFirst().orElseThrow();
        assertEquals("SOURCE_REGISTRATION_CHANGED",early.getOutcomeCode());
        verifyNoInteractions(sender);

        visit.setStatus(RegistrationStatus.ATTENDED);registrations.saveAndFlush(visit);
        email.sendAttendanceConfirmation(visit);
        String current=deliveries.findAll().stream().filter(row->row.getStatus()==Status.QUEUED)
                .map(MailDelivery::getId).findFirst().orElseThrow();
        worker.processRetryQueue();
        assertEquals(Status.PROVIDER_ACCEPTED,deliveries.findById(current).orElseThrow().getStatus());
        verify(sender,times(1)).send(any(MimeMessage.class));
    }
    @Test void expiredPendingPayloadIsScrubbedEvenWhenBusinessModuleDisabled(){
        String id=enqueue();jdbc.update("update mail_deliveries set expires_at=? where id=?",java.sql.Timestamp.from(Instant.now().minusSeconds(1)),id);
        var flag=flags.findByFeatureKey("MODULE_NOTIFICATIONS").orElseThrow();flag.setEnabled(false);flags.saveAndFlush(flag);
        worker.processRetryQueue();var row=deliveries.findById(id).orElseThrow();
        assertEquals(Status.EXPIRED,row.getStatus());assertNull(row.getRecipient());verifyNoInteractions(sender);
    }
    @Test void changedEmailAddressDoesNotRerouteOldNotification(){
        String id=enqueue();user.setEmail(UUID.randomUUID()+"@example.invalid");users.saveAndFlush(user);worker.processRetryQueue();
        assertEquals(Status.SUPPRESSED,deliveries.findById(id).orElseThrow().getStatus());verifyNoInteractions(sender);
    }
    @Test void expiredReceiptIsRemovedWithoutChangingOtherRows(){
        String id=enqueue();ReflectionTestUtils.setField(worker,"backend","console");worker.processRetryQueue();
        jdbc.update("update mail_deliveries set completed_at=? where id=?",java.sql.Timestamp.from(Instant.now().minusSeconds(31L*86400)),id);
        String pending=enqueue();outbox.scrubExpired();assertFalse(deliveries.existsById(id));assertTrue(deliveries.existsById(pending));
    }
}

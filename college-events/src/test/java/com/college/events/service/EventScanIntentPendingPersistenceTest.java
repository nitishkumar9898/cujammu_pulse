package com.college.events.service;

import com.college.events.model.*;
import com.college.events.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never"}, showSql=false)
@org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
class EventScanIntentPendingPersistenceTest {
    @Autowired UserRepository users;
    @Autowired EventRepository events;
    @Autowired RegistrationRepository registrations;
    @Autowired EventQrCredentialRepository credentials;
    @Autowired EventScanIntentRepository intents;
    @Autowired EventOperationsRepository operations;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void liveIntentBlocksDuplicateScanButExpiredIntentDoesNot() {
        User operator = user(Role.ORGANIZER);
        User owner = user(Role.STUDENT);
        Event event = events.saveAndFlush(Event.builder().organizer(operator).title("Synthetic QR event")
                .description("Persistence query fixture").venue("Synthetic hall")
                .eventDate(LocalDateTime.now().plusDays(2)).maxSeats(10).status(EventStatus.APPROVED).build());
        Registration registration = registrations.saveAndFlush(Registration.builder().event(event).user(owner)
                .status(RegistrationStatus.REGISTERED).build());
        EventQrCredential credential = credentials.saveAndFlush(EventQrCredential.builder().event(event)
                .registration(registration).tokenDigest("a".repeat(64)).action("ENTRY").scannerEpoch(1L)
                .status("ACTIVE").expiresAt(Instant.now().plusSeconds(60)).build());

        assertFalse(intents.existsLivePendingByCredentialId(credential.getId(), Instant.now()));
        EventScanIntent intent = intents.saveAndFlush(EventScanIntent.builder().event(event).registration(registration)
                .credential(credential).operator(operator).intentKey(UUID.randomUUID().toString())
                .requestKey(UUID.randomUUID().toString()).action("ENTRY").challengeDigest("b".repeat(64))
                .credentialVersionAtScan(credential.getCredentialVersion()).scannerEpochAtScan(credential.getScannerEpoch())
                .status("PENDING").expiresAt(Instant.now().plusSeconds(60)).build());
        assertTrue(intents.existsLivePendingByCredentialId(credential.getId(), Instant.now()));
        long revisionBeforeRevocation = intent.getRevision();
        assertTrue(new TransactionTemplate(transactionManager).execute(status -> intents.revokePendingByCredentialId(credential.getId())) > 0);
        EventScanIntent revoked = intents.findById(intent.getId()).orElseThrow();
        assertTrue(revoked.getRevision() > revisionBeforeRevocation);
        org.junit.jupiter.api.Assertions.assertEquals("REVOKED", revoked.getStatus());
        assertFalse(intents.existsLivePendingByCredentialId(credential.getId(), Instant.now()));

        revoked.setStatus("PENDING");
        revoked.setExpiresAt(Instant.now().minusSeconds(1));
        intents.saveAndFlush(revoked);
        assertFalse(intents.existsLivePendingByCredentialId(credential.getId(), Instant.now()));
    }

    @Test
    void eventAndCredentialLocksSerializeDistinctRequestKeysForOnePass() throws Exception {
        User operator = user(Role.ORGANIZER);
        User owner = user(Role.STUDENT);
        Event event = events.saveAndFlush(Event.builder().organizer(operator).title("Synthetic QR race")
                .description("Concurrent dedupe fixture").venue("Synthetic hall")
                .eventDate(LocalDateTime.now().plusDays(2)).maxSeats(10).status(EventStatus.APPROVED).build());
        operations.saveAndFlush(EventOperations.builder().event(event).attendanceMode("DUAL_QR")
                .scannerMasterEnabled(true).scannerScope("ALL").scannerActions("ENTRY").scannerEpoch(1L).build());
        Registration registration = registrations.saveAndFlush(Registration.builder().event(event).user(owner)
                .status(RegistrationStatus.REGISTERED).build());
        EventQrCredential credential = credentials.saveAndFlush(EventQrCredential.builder().event(event)
                .registration(registration).tokenDigest("c".repeat(64)).action("ENTRY").scannerEpoch(1L)
                .status("ACTIVE").expiresAt(Instant.now().plusSeconds(60)).build());

        var ready = new CountDownLatch(2); var start = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> createIntentIfAbsent(event, registration, credential, operator, ready, start));
            var second = pool.submit(() -> createIntentIfAbsent(event, registration, credential, operator, ready, start));
            org.junit.jupiter.api.Assertions.assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown();
            var results = java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            org.junit.jupiter.api.Assertions.assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
            org.junit.jupiter.api.Assertions.assertEquals(1, intents.countByCredentialId(credential.getId()));
        } finally { start.countDown(); pool.shutdownNow(); }
    }

    private boolean createIntentIfAbsent(Event event, Registration registration, EventQrCredential credential,
                                         User operator, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown(); org.junit.jupiter.api.Assertions.assertTrue(start.await(5, TimeUnit.SECONDS));
        var template = new TransactionTemplate(transactionManager);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return template.execute(status -> {
            operations.findForUpdate(event.getId()).orElseThrow();
            EventQrCredential locked = credentials.findForUpdateByDigest(credential.getTokenDigest()).orElseThrow();
            if (intents.existsLivePendingByCredentialId(locked.getId(), Instant.now())) return false;
            intents.saveAndFlush(EventScanIntent.builder().event(event).registration(registration).credential(locked)
                    .operator(operator).intentKey(UUID.randomUUID().toString()).requestKey(UUID.randomUUID().toString())
                    .action("ENTRY").challengeDigest("d".repeat(64)).credentialVersionAtScan(locked.getCredentialVersion())
                    .scannerEpochAtScan(locked.getScannerEpoch()).status("PENDING").expiresAt(Instant.now().plusSeconds(60)).build());
            return true;
        });
    }

    private User user(Role role) {
        return users.saveAndFlush(User.builder().name("Synthetic QR actor").email(UUID.randomUUID() + "@example.invalid")
                .password("synthetic-hash").role(role).enabled(true).verifiedEnrollmentNo("SYN-" + UUID.randomUUID()).build());
    }
}

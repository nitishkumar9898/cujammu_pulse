package com.college.events.service;

import com.college.events.model.Role;
import com.college.events.model.User;
import com.college.events.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.concurrent.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false", "spring.sql.init.mode=never", "logging.file.name=target/test-logs/password-recovery.log"}, showSql = false)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PasswordRecoveryConcurrencyTest {
    @Autowired UserRepository users;
    @Autowired PlatformTransactionManager transactions;

    @Test void staleProfileCannotRestorePasswordOrConsumedToken() {
        var tx = new TransactionTemplate(transactions);
        String digest = PasswordResetTokens.digest(PasswordResetTokens.generate());
        User stale = tx.execute(status -> users.saveAndFlush(User.builder().name("Synthetic account")
                .email("stale@example.invalid").password("old-hash").role(Role.STUDENT)
                .passwordResetToken(digest).passwordResetTokenExpiry(now().plusMinutes(30)).enabled(true).build()));
        long previousVersion = stale.getCredentialVersion();
        assertEquals(1, tx.execute(status -> users.consumePasswordReset(digest, now(), "replacement-hash")).intValue());
        stale.setName("Stale profile update");
        assertThrows(org.springframework.orm.ObjectOptimisticLockingFailureException.class,
                () -> tx.execute(status -> users.saveAndFlush(stale)));
        User stored = users.findById(stale.getId()).orElseThrow();
        assertEquals("replacement-hash", stored.getPassword());
        assertNull(stored.getPasswordResetToken());
        assertEquals(previousVersion + 1, stored.getCredentialVersion());
        assertEquals(0, tx.execute(status -> users.replacePasswordAndRevokeReset(stale.getId(), "old-hash", "stale-session-password")).intValue());
    }

    @Test void issuancePersistsOnlyDigestAndInvalidatesPreviousToken() {
        var tx = new TransactionTemplate(transactions);
        User account = tx.execute(status -> users.saveAndFlush(User.builder().name("Synthetic account")
                .email("storage@example.invalid").password("old-hash").role(Role.STUDENT).enabled(true).build()));
        var service = new UserService(users, mock(PasswordEncoder.class), mock(FileStorageService.class));
        String first = tx.execute(status -> service.generatePasswordResetToken("storage@example.invalid"));
        User stored = users.findById(account.getId()).orElseThrow();
        assertNotEquals(first, stored.getPasswordResetToken());
        assertEquals(PasswordResetTokens.digest(first), stored.getPasswordResetToken());
        assertEquals("old-hash", stored.getPassword());
        assertTrue(service.validatePasswordResetToken(first).isPresent());
        String second = tx.execute(status -> service.generatePasswordResetToken("storage@example.invalid"));
        assertNotEquals(first, second);
        assertTrue(service.validatePasswordResetToken(first).isEmpty());
        assertTrue(service.validatePasswordResetToken(second).isPresent());
    }

    @Test void simultaneousConsumersHaveExactlyOneWinnerAndCannotReplay() throws Exception {
        var tx = new TransactionTemplate(transactions);
        String digest = PasswordResetTokens.digest(PasswordResetTokens.generate());
        User user = tx.execute(status -> users.saveAndFlush(User.builder().name("Synthetic account")
                .email("concurrent@example.invalid").password("old-hash").role(Role.STUDENT)
                .passwordResetToken(digest).passwordResetTokenExpiry(now().plusMinutes(30)).enabled(true).build()));
        int workers = 8;
        var ready = new CountDownLatch(workers);
        var go = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(workers);
        try {
            var futures = new ArrayList<Future<Integer>>();
            for (int i = 0; i < workers; i++) {
                final String hash = "new-hash-" + i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    assertTrue(go.await(10, TimeUnit.SECONDS));
                    return tx.execute(status -> users.consumePasswordReset(digest, now(), hash));
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();
            int accepted = 0;
            for (Future<Integer> future : futures) accepted += future.get(20, TimeUnit.SECONDS);
            assertEquals(1, accepted);
            User stored = users.findById(user.getId()).orElseThrow();
            assertNull(stored.getPasswordResetToken());
            assertNull(stored.getPasswordResetTokenExpiry());
            assertTrue(stored.getPassword().startsWith("new-hash-"));
            assertEquals(0, tx.execute(status -> users.consumePasswordReset(digest, now(), "replay-hash")).intValue());
        } finally {
            go.countDown();
            pool.shutdownNow();
        }
    }

    @Test void expiredBoundaryDisabledAndRotatedCredentialsCannotChangePassword() {
        var tx = new TransactionTemplate(transactions);
        String digest = PasswordResetTokens.digest(PasswordResetTokens.generate());
        LocalDateTime expiry = now().withNano(0);
        User user = tx.execute(status -> users.saveAndFlush(User.builder().name("Synthetic account")
                .email("boundary@example.invalid").password("old-hash").role(Role.STUDENT)
                .passwordResetToken(digest).passwordResetTokenExpiry(expiry).enabled(true).build()));
        assertEquals(0, tx.execute(status -> users.consumePasswordReset(digest, expiry, "forbidden")).intValue());
        String replacement = PasswordResetTokens.digest(PasswordResetTokens.generate());
        assertEquals(1, tx.execute(status -> users.issuePasswordReset(user.getId(), replacement, now().plusMinutes(30))).intValue());
        assertEquals(0, tx.execute(status -> users.consumePasswordReset(digest, now(), "forbidden")).intValue());
        tx.executeWithoutResult(status -> {
            var disabled = users.findById(user.getId()).orElseThrow();
            disabled.setEnabled(false); users.saveAndFlush(disabled);
        });
        assertEquals(0, tx.execute(status -> users.consumePasswordReset(replacement, now(), "forbidden")).intValue());
        assertEquals("old-hash", users.findById(user.getId()).orElseThrow().getPassword());
    }

    private static LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
}

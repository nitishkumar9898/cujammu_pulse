package com.college.events.service;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

class OtpConcurrencySecurityTest {
    @Test void simultaneousConsumersHaveOnlyOneWinner() throws Exception {
        for (int round = 0; round < 10; round++) {
            var service = new OtpService();
            String code = service.generateOtp("concurrent@example.invalid");
            var results = concurrent(32, () -> service.verifyOtp("concurrent@example.invalid", code).success());
            assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
            assertFalse(service.verifyOtp("concurrent@example.invalid", code).success());
        }
    }

    @Test void simultaneousFailuresDoNotLoseAttempts() throws Exception {
        var service = new OtpService();
        String code = service.generateOtp("attempts@example.invalid");
        var results = concurrent(32, () -> service.verifyOtp("attempts@example.invalid", "invalid"));
        assertEquals(5, results.stream().filter(result -> result.message().startsWith("Invalid OTP.")).count());
        assertFalse(service.hasActiveOtp("attempts@example.invalid"));
        assertFalse(service.verifyOtp("attempts@example.invalid", code).success());
    }

    @Test void concurrentIssuanceCannotOverrunFiveRequestBudget() throws Exception {
        var service = new OtpService();
        var results = concurrent(32, () -> service.generateOtp(" Budget@Example.invalid "));
        assertEquals(5, results.stream().filter(Objects::nonNull).count());
        assertNull(service.generateOtp("budget@example.invalid"));
        assertNotNull(service.generateOtp("separate@example.invalid"));
    }

    @Test void exactExpiryIsDeniedAndRateWindowReopensWithoutCleanup() {
        var clock = new MutableClock();
        var service = new OtpService(clock);
        String code = service.generateOtp("expiry@example.invalid");
        for (int i = 1; i < 5; i++) assertNotNull(service.generateOtp("expiry@example.invalid"));
        assertNull(service.generateOtp("expiry@example.invalid"));
        clock.now = clock.now.plusSeconds(600);
        assertFalse(service.hasActiveOtp("expiry@example.invalid"));
        assertEquals(0, service.getRemainingSeconds("expiry@example.invalid"));
        assertFalse(service.verifyOtp("expiry@example.invalid", code).success());
        assertNotNull(service.generateOtp("expiry@example.invalid"));
    }

    @Test void localeNullAndMalformedInputsCannotBypassAccountBudget() {
        var service = new OtpService();
        Locale old = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            String code = service.generateOtp("IDENTITY@example.invalid");
            assertTrue(service.verifyOtp("identity@example.invalid", code).success());
        } finally { Locale.setDefault(old); }
        assertNull(service.generateOtp(null));
        assertNull(service.generateOtp("x".repeat(255)));
        assertFalse(service.verifyOtp(null, null).success());
        service.generateOtp("null@example.invalid");
        assertFalse(service.verifyOtp("null@example.invalid", null).success());
    }

    private static <T> List<T> concurrent(int count, Supplier<T> operation) throws Exception {
        var pool = Executors.newFixedThreadPool(count);
        var ready = new CountDownLatch(count);
        var go = new CountDownLatch(1);
        try {
            var futures = new ArrayList<Future<T>>();
            for (int i = 0; i < count; i++) futures.add(pool.submit(() -> {
                ready.countDown();
                if (!go.await(10, TimeUnit.SECONDS)) throw new AssertionError("start barrier timed out");
                return operation.get();
            }));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();
            var results = new ArrayList<T>();
            for (var future : futures) results.add(future.get(20, TimeUnit.SECONDS));
            return results;
        } finally { go.countDown(); pool.shutdownNow(); }
    }
    private static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-19T00:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
}

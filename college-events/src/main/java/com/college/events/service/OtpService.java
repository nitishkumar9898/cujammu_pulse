package com.college.events.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Single-instance OTP guard. A distributed deployment requires shared atomic storage. */
@Service
@Slf4j
public class OtpService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_REQUESTS = 5;
    private final Clock clock;
    private final Map<String, OtpEntry> otpStore = new ConcurrentHashMap<>();
    private final Map<String, RateLimitEntry> rateLimitStore = new ConcurrentHashMap<>();

    public OtpService() { this(Clock.systemUTC()); }
    OtpService(Clock clock) { this.clock = clock; }

    public String generateOtp(String email) {
        String key = normalize(email);
        if (key == null) return null;
        AtomicBoolean admitted = new AtomicBoolean();
        rateLimitStore.compute(key, (ignored, previous) -> {
            Instant now = clock.instant();
            RateLimitEntry current = previous == null || !now.isBefore(previous.expiresAt())
                    ? new RateLimitEntry(0, now.plusSeconds(600)) : previous;
            if (current.count() >= MAX_REQUESTS) return current;
            admitted.set(true);
            return new RateLimitEntry(current.count() + 1, current.expiresAt());
        });
        if (!admitted.get()) return null;
        String otp = String.format(Locale.ROOT, "%06d", SECURE_RANDOM.nextInt(1_000_000));
        otpStore.put(key, new OtpEntry(otp, clock.instant().plusSeconds(600), 0));
        log.info("OTP generated; credential and recipient omitted");
        return otp;
    }

    /** Consumption and failed-attempt counting contend on the same account key. */
    public VerificationResult verifyOtp(String email, String otp) {
        String key = normalize(email);
        if (key == null) return missing();
        AtomicReference<VerificationResult> result = new AtomicReference<>(missing());
        otpStore.compute(key, (ignored, entry) -> {
            Instant now = clock.instant();
            if (entry == null) return null;
            if (!now.isBefore(entry.expiresAt())) {
                result.set(new VerificationResult(false, "OTP has expired. Please request a new one."));
                return null;
            }
            if (otp != null && entry.otp().equals(otp.trim())) {
                result.set(new VerificationResult(true, "OTP verified successfully."));
                return null;
            }
            int attempts = entry.attempts() + 1;
            result.set(new VerificationResult(false,
                    "Invalid OTP. " + (MAX_ATTEMPTS - attempts) + " attempt(s) remaining."));
            return attempts >= MAX_ATTEMPTS ? null : new OtpEntry(entry.otp(), entry.expiresAt(), attempts);
        });
        return result.get();
    }

    public long getRemainingSeconds(String email) {
        String key = normalize(email);
        OtpEntry entry = key == null ? null : otpStore.get(key);
        return entry == null ? 0 : Math.max(0, Duration.between(clock.instant(), entry.expiresAt()).getSeconds());
    }
    public boolean hasActiveOtp(String email) {
        String key = normalize(email);
        OtpEntry entry = key == null ? null : otpStore.get(key);
        return entry != null && clock.instant().isBefore(entry.expiresAt());
    }
    public void invalidateOtp(String email) {
        String key = normalize(email);
        if (key != null) otpStore.remove(key);
    }
    @Scheduled(fixedRate = 300_000)
    public void cleanupExpiredEntries() {
        Instant now = clock.instant();
        // Conditional removal never deletes a replacement issued after this snapshot.
        otpStore.forEach((key, value) -> { if (!now.isBefore(value.expiresAt())) otpStore.remove(key, value); });
        rateLimitStore.forEach((key, value) -> { if (!now.isBefore(value.expiresAt())) rateLimitStore.remove(key, value); });
    }
    private static String normalize(String email) {
        if (email == null || email.isBlank() || email.length() > 254) return null;
        return email.trim().toLowerCase(Locale.ROOT);
    }
    private static VerificationResult missing() {
        return new VerificationResult(false, "No OTP found. Please request a new one.");
    }
    private record OtpEntry(String otp, Instant expiresAt, int attempts) {}
    private record RateLimitEntry(int count, Instant expiresAt) {}
    public record VerificationResult(boolean success, String message) {}
}

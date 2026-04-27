package com.college.events.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Secure OTP service with rate limiting, brute-force protection,
 * and automatic cleanup of expired entries.
 */
@Service
@Slf4j
public class OtpService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int OTP_LENGTH = 6;
    private static final int OTP_EXPIRY_MINUTES = 10;
    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_OTP_REQUESTS_PER_WINDOW = 5;
    private static final int RATE_LIMIT_WINDOW_MINUTES = 10;

    // In-memory store – keyed by email
    private final Map<String, OtpEntry> otpStore = new ConcurrentHashMap<>();
    private final Map<String, RateLimitEntry> rateLimitStore = new ConcurrentHashMap<>();

    /**
     * Generate and store a new OTP for the given email.
     * Returns the OTP string, or null if rate-limited.
     */
    public String generateOtp(String email) {
        String normalizedEmail = email.toLowerCase().trim();

        // Rate limiting check
        if (isRateLimited(normalizedEmail)) {
            log.warn("OTP rate limit exceeded for email: {}", normalizedEmail);
            return null;
        }

        String otp = generateSecureOtp();
        OtpEntry entry = new OtpEntry(
            otp,
            LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES),
            0
        );
        otpStore.put(normalizedEmail, entry);
        incrementRateLimit(normalizedEmail);

        log.info("OTP generated for {}: {} (expires at {})", normalizedEmail, otp, entry.expiresAt);
        return otp;
    }

    /**
     * Verify the provided OTP for the given email.
     * Returns VerificationResult with success status and message.
     */
    public VerificationResult verifyOtp(String email, String otp) {
        String normalizedEmail = email.toLowerCase().trim();
        OtpEntry entry = otpStore.get(normalizedEmail);

        if (entry == null) {
            return new VerificationResult(false, "No OTP found. Please request a new one.");
        }

        if (entry.isExpired()) {
            otpStore.remove(normalizedEmail);
            return new VerificationResult(false, "OTP has expired. Please request a new one.");
        }

        if (entry.attempts >= MAX_ATTEMPTS) {
            otpStore.remove(normalizedEmail);
            return new VerificationResult(false, "Too many failed attempts. Please request a new OTP.");
        }

        if (!entry.otp.equals(otp.trim())) {
            entry.attempts++;
            int remaining = MAX_ATTEMPTS - entry.attempts;
            log.warn("Invalid OTP attempt for {} ({} remaining)", normalizedEmail, remaining);
            return new VerificationResult(false,
                "Invalid OTP. " + remaining + " attempt(s) remaining.");
        }

        // Success — remove the OTP
        otpStore.remove(normalizedEmail);
        log.info("OTP verified successfully for {}", normalizedEmail);
        return new VerificationResult(true, "OTP verified successfully.");
    }

    /**
     * Get remaining seconds until OTP expiry for countdown display.
     */
    public long getRemainingSeconds(String email) {
        OtpEntry entry = otpStore.get(email.toLowerCase().trim());
        if (entry == null || entry.isExpired()) return 0;
        return java.time.Duration.between(LocalDateTime.now(), entry.expiresAt).getSeconds();
    }

    /**
     * Check if an active (non-expired) OTP exists for the email.
     */
    public boolean hasActiveOtp(String email) {
        OtpEntry entry = otpStore.get(email.toLowerCase().trim());
        return entry != null && !entry.isExpired();
    }

    /**
     * Remove OTP entry (e.g., on registration cancellation).
     */
    public void invalidateOtp(String email) {
        otpStore.remove(email.toLowerCase().trim());
    }

    // ─── Private helpers ─────────────────────────────────────────────────

    private String generateSecureOtp() {
        StringBuilder sb = new StringBuilder(OTP_LENGTH);
        for (int i = 0; i < OTP_LENGTH; i++) {
            sb.append(SECURE_RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    private boolean isRateLimited(String email) {
        RateLimitEntry rle = rateLimitStore.get(email);
        if (rle == null) return false;
        if (rle.isExpired()) {
            rateLimitStore.remove(email);
            return false;
        }
        return rle.count >= MAX_OTP_REQUESTS_PER_WINDOW;
    }

    private void incrementRateLimit(String email) {
        rateLimitStore.compute(email, (k, v) -> {
            if (v == null || v.isExpired()) {
                return new RateLimitEntry(1, LocalDateTime.now().plusMinutes(RATE_LIMIT_WINDOW_MINUTES));
            }
            v.count++;
            return v;
        });
    }

    /** Cleanup expired entries every 5 minutes */
    @Scheduled(fixedRate = 300_000)
    public void cleanupExpiredEntries() {
        otpStore.entrySet().removeIf(e -> e.getValue().isExpired());
        rateLimitStore.entrySet().removeIf(e -> e.getValue().isExpired());
    }

    // ─── Inner classes ───────────────────────────────────────────────────

    private static class OtpEntry {
        final String otp;
        final LocalDateTime expiresAt;
        int attempts;

        OtpEntry(String otp, LocalDateTime expiresAt, int attempts) {
            this.otp = otp;
            this.expiresAt = expiresAt;
            this.attempts = attempts;
        }

        boolean isExpired() {
            return LocalDateTime.now().isAfter(expiresAt);
        }
    }

    private static class RateLimitEntry {
        int count;
        final LocalDateTime windowEnd;

        RateLimitEntry(int count, LocalDateTime windowEnd) {
            this.count = count;
            this.windowEnd = windowEnd;
        }

        boolean isExpired() {
            return LocalDateTime.now().isAfter(windowEnd);
        }
    }

    public record VerificationResult(boolean success, String message) {}
}

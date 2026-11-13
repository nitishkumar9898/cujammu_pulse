package com.college.events.service;

import com.college.events.model.LibraryGateLog;
import com.college.events.model.User;
import com.college.events.repository.LibraryGateLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class LibraryTurnstileService {

    private final LibraryGateLogRepository libraryGateLogRepository;
    private final com.college.events.security.DomainAccess access;
    private final FeatureFlagService featureFlags;
    @Value("${app.timezone:Asia/Kolkata}")
    private String institutionTimezone = "Asia/Kolkata";


    @Transactional
    public LibraryGateLog checkIn(User user, String sessionType, Double latitude, Double longitude) {
        featureFlags.requireEnabledForUpdate("MODULE_LIBRARY_TURNSTILE");
        featureFlags.requireEnabledForUpdate("ACTION_LIBRARY_ENTRY");
        user = access.requireSelf(user.getId());
        String type = normalizePurpose(sessionType);

        // Check if there is an existing ACTIVE session
        Optional<LibraryGateLog> existing = libraryGateLogRepository.findActiveForUpdate(user, "ACTIVE", PageRequest.of(0, 1))
                .stream().findFirst();
        if (existing.isPresent()) {
            LibraryGateLog active = existing.get();
            if (active.getLogDate().equals(today())) {
                // Already inside
                return active;
            } else {
                // Reporting day boundaries do not prove physical exit or duration.
                throw new IllegalStateException("A prior-day visit remains open. Record a reviewed safe exit before starting another visit.");
            }
        }

        String verifiedIdentifier = user.getVerifiedEnrollmentNo();
        if (verifiedIdentifier == null || verifiedIdentifier.isBlank()) {
            throw new IllegalStateException("LIBRARY_VERIFICATION_UNAVAILABLE: a verified university identifier is required");
        }

        LibraryGateLog logEntry = LibraryGateLog.builder()
                .user(user)
                .sessionType(type)
                .entryTime(now())
                .logDate(today())
                .status("ACTIVE")
                .academicLevel(user.getAcademicLevel() != null ? user.getAcademicLevel() : "UG")
                .departmentName(user.getDepartment() != null ? user.getDepartment() : "General")
                .rollNumberOrEmpId(verifiedIdentifier.trim())
                .build();

        return libraryGateLogRepository.save(logEntry);
    }

    @Transactional
    public LibraryGateLog checkOut(User user, String scannedSessionType) {
        featureFlags.requireEnabledForUpdate("MODULE_LIBRARY_TURNSTILE");
        featureFlags.requireEnabledForUpdate("ACTION_LIBRARY_EXIT");
        user = access.requireSelf(user.getId());
        String scannedType = normalizePurpose(scannedSessionType);

        LibraryGateLog active = libraryGateLogRepository.findActiveForUpdate(user, "ACTIVE", PageRequest.of(0, 1)).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Aapki koi active library entry nahi mili. Kripya pehle Entry QR scan karein."));

        // Cross-QR Mismatch Guard
        if (!active.getSessionType().equalsIgnoreCase(scannedType)) {
            // Persist mismatch evidence independently so the expected rejection cannot roll it back.
            libraryGateLogRepository.recordActivePurposeMismatch(active.getId());
            throw new IllegalArgumentException("Galt QR scan kiya hai! Aapne " + active.getSessionType()
                    + " ke liye entry li thi. Kripya wahi QR scan karke exit karein.");
        }

        LocalDateTime exit = now();
        active.setExitTime(exit);

        Duration duration = Duration.between(active.getEntryTime(), exit);
        long seconds = Math.max(1, duration.getSeconds());
        active.setDurationSeconds(seconds);

        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        active.setDurationFormatted(String.format("%02dh %02dm %02ds", h, m, s));
        active.setStatus("COMPLETED");

        return libraryGateLogRepository.save(active);
    }

    public Optional<LibraryGateLog> getActiveSession(User user) {
        featureFlags.requireEnabledForRead("MODULE_LIBRARY_TURNSTILE");
        access.requireSelf(user.getId());
        return libraryGateLogRepository.findTopByUserAndStatusOrderByIdDesc(user, "ACTIVE");
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<LibraryGateLog> getOwnHistory(User user, int page, int size) {
        featureFlags.requireEnabledForRead("MODULE_LIBRARY_TURNSTILE");
        user = access.requireSelf(user.getId());
        if (page < 0 || size < 1 || size > 50) throw new IllegalArgumentException("Invalid library history page");
        return libraryGateLogRepository.findByUserOrderByEntryTimeDesc(user,
                PageRequest.of(page, size));
    }

    public List<LibraryGateLog> getTodayLogs() {
        featureFlags.requireEnabledForRead("MODULE_LIBRARY_TURNSTILE");
        access.requireRole(com.college.events.model.Role.ADMIN, com.college.events.model.Role.LIBRARIAN);
        return scopedReportsUnavailable();
    }

    public byte[] exportDailyCsv(LocalDate date) {
        featureFlags.requireEnabledForRead("MODULE_LIBRARY_TURNSTILE");
        access.requireRole(com.college.events.model.Role.ADMIN, com.college.events.model.Role.LIBRARIAN);
        return scopedReportsUnavailable();
    }

    /** Private, styled workbook projection; raw credentials and internal fields are excluded. */
    @Transactional(readOnly = true)
    public byte[] exportDailyXlsx(LocalDate date) {
        featureFlags.requireEnabledForRead("MODULE_LIBRARY_TURNSTILE");
        access.requireRole(com.college.events.model.Role.ADMIN, com.college.events.model.Role.LIBRARIAN);
        return scopedReportsUnavailable();
    }

    /** Role-only checks are insufficient: never read visitor rows without a scoped durable report grant. */
    private static <T> T scopedReportsUnavailable() {
        throw new IllegalStateException("LIBRARY_VERIFICATION_UNAVAILABLE: scoped durable library reports are not configured");
    }

    private static String normalizePurpose(String raw) {
        String type = raw == null ? "STUDY" : raw.trim().toUpperCase(java.util.Locale.ROOT);
        if (!type.equals("STUDY") && !type.equals("BOOK_ISSUE_RETURN"))
            throw new IllegalArgumentException("Unsupported library QR purpose");
        return type;
    }

    private LocalDate today() {
        return java.time.ZonedDateTime.now(java.time.ZoneId.of(institutionTimezone)).toLocalDate();
    }

    private LocalDateTime now() {
        return java.time.ZonedDateTime.now(java.time.ZoneId.of(institutionTimezone)).toLocalDateTime();
    }
}

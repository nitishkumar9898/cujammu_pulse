package com.college.events.service;

import com.college.events.model.Attendance;
import com.college.events.model.Certificate;
import com.college.events.model.Event;
import com.college.events.model.Registration;
import com.college.events.model.RegistrationStatus;
import com.college.events.model.User;
import com.college.events.repository.AttendanceRepository;
import com.college.events.repository.CertificateRepository;
import com.college.events.repository.RegistrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class AttendanceService {

    private final RegistrationRepository registrationRepository;
    private final AttendanceRepository attendanceRepository;
    private final CertificateRepository certificateRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final AuditService auditService;

    @Transactional
    public Map<String, Object> markAttendance(String qrToken, Long eventId, User organizer) {
        Map<String, Object> result = new HashMap<>();

        // ── Token Normalization ──────────────────────────────────────────────
        // The QR code payload can arrive in 3 possible formats:
        //   1. Plain UUID token:   "550e8400-e29b-41d4-a716-446655440000"
        //   2. Verify URL:         "http://localhost:8080/attendance/verify/<token>"
        //   3. Rich JSON payload:  {"student":"...","token":"<uuid>","verify":"http://..."}
        // extractToken() normalizes all 3 formats to just the bare UUID token.
        String normalizedToken = extractToken(qrToken);
        log.debug("QR scan received raw='{}', extracted token='{}'", qrToken, normalizedToken);

        if (normalizedToken == null || normalizedToken.isBlank()) {
            result.put("success", false);
            result.put("message", "Invalid QR format. Could not extract token.");
            return result;
        }

        Registration registration = registrationRepository.findByQrToken(normalizedToken).orElse(null);

        if (registration == null) {
            result.put("success", false);
            result.put("message", "Invalid QR token. Ticket not found.");
            return result;
        }

        // Ensure this ticket belongs to the specific event being scanned
        Event event = registration.getEvent();
        if (eventId != null && !event.getId().equals(eventId)) {
            result.put("success", false);
            result.put("message", "This ticket is for a different event: " + event.getTitle());
            return result;
        }

        if (!event.getOrganizer().getId().equals(organizer.getId()) && organizer.getRole() != com.college.events.model.Role.ADMIN) {
            result.put("success", false);
            result.put("message", "You are not authorized to scan for this event.");
            return result;
        }

        // Check if already attended
        boolean alreadyRecorded = registration.getStatus() == RegistrationStatus.ATTENDED
            || attendanceRepository.existsByRegistration(registration);

        if (alreadyRecorded) {
            result.put("success", false);
            result.put("message", "⚠️ Already attended! " + registration.getUser().getName() +
                " was scanned earlier.");
            result.put("alreadyAttended", true);
            result.put("studentName", registration.getUser().getName());
            result.put("studentEmail", registration.getUser().getEmail());
            result.put("department", registration.getUser().getDepartment());
            result.put("certificateUrl", "/student/certificate/" + registration.getId());
            return result;
        }

        if (registration.getStatus() == RegistrationStatus.CANCELLED) {
            result.put("success", false);
            result.put("message", "This registration has been cancelled.");
            return result;
        }

        if (registration.getStatus() == RegistrationStatus.WAITLISTED) {
            result.put("success", false);
            result.put("message", "This student is on the waitlist and is not confirmed.");
            return result;
        }

        // ── Mark Attendance ──────────────────────────────────────────────────
        registration.setStatus(RegistrationStatus.ATTENDED);
        // Reward 50 gamification points for physical attendance
        User student = registration.getUser();
        if (student != null) {
            student.setGamificationPoints(student.getGamificationPoints() + 50);
        }
        registrationRepository.save(registration);

        Attendance attendance = attendanceRepository.findByRegistration(registration)
            .orElseGet(() -> {
                Attendance newAttendance = Attendance.builder()
                    .registration(registration)
                    .scannedAt(LocalDateTime.now())
                    .scannedBy(organizer != null ? organizer.getEmail() : "system")
                    .build();
                return attendanceRepository.save(newAttendance);
            });

        // ── Issue Certificate ────────────────────────────────────────────────
        if (attendance != null && !certificateRepository.existsByRegistration(registration)) {
            String certNo = "CUJ-" + event.getId() + "-" + registration.getId() + "-" + (System.currentTimeMillis() % 1000);
            Certificate certificate = Certificate.builder()
                .registration(registration)
                .event(event)
                .user(registration.getUser())
                .certificateNumber(certNo)
                .templateId("DEFAULT")
                .build();
            if (certificate != null) {
                certificateRepository.save(certificate);
                log.info("Issued certificate {} for user {} at event '{}'", certNo, registration.getUser().getEmail(), event.getTitle());
            }
        }

        // ── Post-scan notifications (all async/background) ───────────────────
        emailService.sendAttendanceConfirmation(registration);
        notificationService.createNotification(
            registration.getUser(),
            "Attendance verified for " + event.getTitle() + ". Your certificate is ready.",
            "/student/certificates"
        );
        notificationService.createNotification(
            organizer,
            registration.getUser().getName() + " checked in for " + event.getTitle() + ".",
            "/organizer/events/" + event.getId() + "/registrations"
        );
        auditService.logAction(
            organizer.getEmail(),
            organizer.getRole().name(),
            "ATTENDANCE_MARKED",
            "Registration ID: " + registration.getId() + ", Event ID: " + event.getId()
        );

        result.put("success", true);
        result.put("message", "✅ Attendance marked for " + registration.getUser().getName());
        result.put("studentName", registration.getUser().getName());
        result.put("studentEmail", registration.getUser().getEmail());
        result.put("department", registration.getUser().getDepartment());
        result.put("enrollmentNo", registration.getUser().getEnrollmentNo());
        result.put("eventTitle", event.getTitle());
        result.put("scannedAt", attendance.getScannedAt().toString());
        result.put("certificateUrl", "/student/certificates");

        log.info("Attendance marked for {} at event '{}'",
            registration.getUser().getEmail(), event.getTitle());
        return result;
    }


    /**
     * Extracts the bare UUID token from any QR payload format.
     * Handles: plain UUID, verify URL, and rich JSON payload.
     */
    private String extractToken(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();

        // 1. JSON Payload extraction
        if (trimmed.startsWith("{")) {
            try {
                // Try extracting "token" or "verify" URL from JSON
                Matcher m = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"").matcher(trimmed);
                if (m.find()) return m.group(1).toLowerCase();
                
                m = Pattern.compile("\"verif(?:y|icationUrl)\"\\s*:\\s*\"[^\"]*\\/verify\\/([^\"/?#]+)\"").matcher(trimmed);
                if (m.find()) return m.group(1).toLowerCase();
            } catch (Exception e) {
                log.warn("JSON QR parse fail: {}", e.getMessage());
            }
        }

        // 2. URL extraction (matches /verify/UUID)
        Matcher urlMatcher = Pattern.compile("/verify/([a-fA-F0-9\\-]{32,36})").matcher(trimmed);
        if (urlMatcher.find()) return urlMatcher.group(1).toLowerCase();

        // 3. Direct UUID or alphanumeric token
        // Matches UUID: 8-4-4-4-12 hex chars
        if (trimmed.matches("(?i)[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")) {
            return trimmed.toLowerCase();
        }
        
        // Matches shorter alphanumeric tokens if used
        if (trimmed.length() >= 8 && trimmed.matches("^[a-zA-Z0-9\\-]+$")) {
            return trimmed.toLowerCase();
        }

        log.warn("Unknown QR format: {}", trimmed);
        return trimmed.toLowerCase();
    }
}

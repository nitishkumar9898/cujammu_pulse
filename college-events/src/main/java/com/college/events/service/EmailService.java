package com.college.events.service;

import com.college.events.model.Event;
import com.college.events.model.Registration;
import com.college.events.model.User;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class EmailService {
    static final int MAX_SYNCHRONOUS_AUDIENCE = 500;

    private final JavaMailSender mailSender;
    private final EmailRetryService emailRetryService;
    @Autowired private MailOutboxService outbox;

    private void queueNotice(String recipient, com.college.events.model.MailDelivery.Kind kind) {
        outbox.enqueue(recipient, kind);
    }

    private void queueEventNotice(String recipient, com.college.events.model.MailDelivery.Kind kind, Event event) {
        outbox.enqueueEventNotice(recipient, kind, event.getId(), event.getEventDate(), event.getLifecycleVersion());
    }

    private void queueRegistrationNotice(Registration registration, com.college.events.model.MailDelivery.Kind kind) {
        if (registration.getId() == null) throw new IllegalArgumentException("Registration source must be persisted");
        outbox.enqueueRegistrationNotice(registration.getUser().getEmail(), kind, registration.getId());
    }

    @Value("${spring.mail.username:noreply@college.com}")
    private String fromEmail;

    @Value("${app.name:CUJAMMU EVENTHUB}")
    private String appName;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${app.security.reset-link-hosts:}")
    private String resetLinkAllowedHosts;

    private static final DateTimeFormatter DATE_FORMAT =
        DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a");

    // ─── REGISTRATION EMAILS ────────────────────────────────────────────────

    public void sendRegistrationConfirmation(Registration registration) {
        queueRegistrationNotice(registration, com.college.events.model.MailDelivery.Kind.REGISTRATION);
    }

    public void sendWaitlistConfirmation(Registration registration) {
        queueRegistrationNotice(registration, com.college.events.model.MailDelivery.Kind.WAITLIST);
    }

    public void sendWaitlistPromotion(Registration registration) {
        queueRegistrationNotice(registration, com.college.events.model.MailDelivery.Kind.PROMOTION);
    }

    public void sendCancellationConfirmation(Registration registration) {
        queueRegistrationNotice(registration, com.college.events.model.MailDelivery.Kind.WITHDRAWAL);
    }

    /** Call only after ATTENDED is persisted in the same domain transaction; the outbox rechecks that state at claim. */
    public void sendAttendanceConfirmation(Registration registration) {
        queueRegistrationNotice(registration, com.college.events.model.MailDelivery.Kind.ATTENDANCE_UPDATE);
    }

    public void sendEventApprovalNotification(Event event) {
        queueEventNotice(event.getOrganizer().getEmail(), com.college.events.model.MailDelivery.Kind.EVENT_APPROVED, event);
    }

    public void sendEventRejectionNotification(Event event) {
        queueEventNotice(event.getOrganizer().getEmail(), com.college.events.model.MailDelivery.Kind.EVENT_REJECTED, event);
    }

    @org.springframework.transaction.annotation.Transactional
    public void sendEventCancellationNotification(Event event, List<Registration> registrations) {
        requireBoundedAudience(registrations);
        for(Registration registration:registrations)
            queueEventNotice(registration.getUser().getEmail(), com.college.events.model.MailDelivery.Kind.EVENT_CANCELLED, event);
    }

    @org.springframework.transaction.annotation.Transactional
    public void sendEventPostponementNotification(Event event, List<Registration> registrations) {
        requireBoundedAudience(registrations);
        for(Registration registration:registrations)
            queueEventNotice(registration.getUser().getEmail(), com.college.events.model.MailDelivery.Kind.EVENT_POSTPONED, event);
    }

    @org.springframework.transaction.annotation.Transactional
    public void sendNewEventAnnouncement(Event event, List<User> students) {
        requireBoundedAudience(students);
        for(User student:students) {
            Long requiredDepartment="ALL".equalsIgnoreCase(event.getTargetDepartment())
                    ? null : student.getVerifiedDepartmentId();
            outbox.enqueueScopedEventAnnouncement(student.getEmail(),event.getId(),
                    event.getEventDate(),event.getLifecycleVersion(),requiredDepartment);
        }
    }

    @org.springframework.transaction.annotation.Transactional
    public void sendBulkAnnouncement(List<String> emails,String eventTitle,String announcementSubject,String message) {
        throw new UnsupportedOperationException("Custom organizer announcements are unavailable until their approved delivery workflow is implemented");
    }

    private static void requireBoundedAudience(List<?> audience) {
        if(audience==null || audience.size()>MAX_SYNCHRONOUS_AUDIENCE)throw new IllegalArgumentException("Audience exceeds bounded notification batch");
    }

    @Async
    public void sendPasswordResetEmail(String email, String resetLink) {
        try {
            if (!SafeResetLink.isAllowedResetLink(resetLink, resetLinkAllowedHosts))
                throw new IllegalArgumentException("Password recovery link origin is not approved");
            String subject = "🔑 Password Reset Request – " + appName;
            String body = buildPasswordResetHtml(resetLink);
            sendCredentialEmail(email, subject, body);
            log.info("Password recovery email accepted by provider");
        } catch (Exception e) {
            log.warn("Password recovery delivery failed; failure type={}", e.getClass().getSimpleName());
        }
    }

    @Value("${app.email.backend:smtp}")
    private String emailBackend;

    // ─── OTP EMAIL ────────────────────────────────────────────────────────────

    @Async
    public java.util.concurrent.CompletableFuture<Boolean> sendOtpEmail(String email, String otp, String userName) {
        log.debug("Attempting verification email");
        if ("console".equalsIgnoreCase(emailBackend)) {
            log.info("OTP delivery suppressed by console backend; credential omitted");
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }

        try {
            String subject = "🔐 Your Verification Code – " + appName;
            String body = emailWrapper("Email Verification 🔐",
                "<p>Hi <strong>" + escapeHtml(userName != null ? userName : "there") + "</strong>,</p>" +
                "<p>Your one-time verification code for <strong>" + appName + "</strong> is:</p>" +
                "<div style='text-align:center;margin:24px 0'>" +
                "<div style='display:inline-block;background:linear-gradient(135deg,#6C63FF,#3D35A0);color:#fff;" +
                "font-size:36px;font-weight:bold;letter-spacing:12px;padding:16px 32px;border-radius:12px;" +
                "font-family:monospace'>" + otp + "</div></div>" +
                "<p style='text-align:center;color:#dc3545;font-weight:bold'>⏱ This code expires in 10 minutes</p>" +
                "<div style='background:#fff3cd;border-left:4px solid #ffc107;padding:12px 16px;margin:16px 0;border-radius:4px'>" +
                "<strong>⚠️ Security Notice:</strong> Never share this code with anyone. " +
                appName + " staff will never ask for your OTP.</div>");
            sendCredentialEmail(email, subject, body);
            log.info("Verification email accepted by provider");
            return java.util.concurrent.CompletableFuture.completedFuture(true);
        } catch (Exception e) {
            log.error("OTP delivery failed; failure type={}", e.getClass().getSimpleName());
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }
    }

    // ─── CERTIFICATE EMAIL ───────────────────────────────────────────────────

    public void sendCertificateEmail(Registration registration, String downloadUrl) {
        queueNotice(registration.getUser().getEmail(),com.college.events.model.MailDelivery.Kind.CERTIFICATE);
    }

    private void sendCredentialEmail(String to, String subject, String htmlBody) throws Exception {
        if (!"smtp".equalsIgnoreCase(emailBackend) || fromEmail == null || fromEmail.isBlank() || fromEmail.contains("your_"))
            throw new IllegalStateException("Credential delivery is unavailable");
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(fromEmail, appName); helper.setTo(to); helper.setSubject(subject); helper.setText(htmlBody, true);
        mailSender.send(message);
    }

    private String buildRegistrationConfirmationHtml(Registration registration) {
        Event event = registration.getEvent();
        User user = registration.getUser();
        return emailWrapper(
            "Registration Confirmed",
            "<p>Hi <strong>" + escapeHtml(user.getName()) + "</strong>,</p>" +
            "<p>You have successfully registered for <strong>" + escapeHtml(event.getTitle()) + "</strong>.</p>" +
            buildEventInfoTable(event) +
            "<p>Manage your registration and event updates in My Tickets. Secure event-pass display is not enabled in this release.</p>" +
            "<a href='" + baseUrl + "/student/my-tickets' style='" + btnStyle("6C63FF") + "'>View My Registrations</a>"
        );
    }
    private String buildWaitlistHtml(Registration registration) {
        Event event = registration.getEvent();
        return emailWrapper(
            "You're on the Waitlist ⏳",
            "<p>Hi <strong>" + escapeHtml(registration.getUser().getName()) + "</strong>,</p>" +
            "<p>The event <strong>" + escapeHtml(event.getTitle()) + "</strong> is currently full, but you've been added to the waitlist.</p>" +
            buildEventInfoTable(event) +
            "<p>You'll be automatically notified and registered if a seat becomes available. No action needed!</p>"
        );
    }

    private String buildWaitlistPromotionHtml(Registration registration) {
        Event event = registration.getEvent();
        return emailWrapper(
            "Great News – You're In! 🎉",
            "<p>Hi <strong>" + escapeHtml(registration.getUser().getName()) + "</strong>,</p>" +
            "<p>A seat opened up for <strong>" + escapeHtml(event.getTitle()) + "</strong> and you've been automatically registered!</p>" +
            buildEventInfoTable(event) +
            "<p>Manage your registration and event updates in My Tickets. Secure event-pass display is not enabled in this release.</p>" +
            "<a href='" + baseUrl + "/student/my-tickets' style='" + btnStyle("4CAF50") + "'>View My Registrations</a>"
        );
    }

    private String buildCancellationHtml(Registration registration) {
        Event event = registration.getEvent();
        return emailWrapper(
            "Registration Cancelled",
            "<p>Hi <strong>" + escapeHtml(registration.getUser().getName()) + "</strong>,</p>" +
            "<p>Your registration for <strong>" + escapeHtml(event.getTitle()) + "</strong> has been successfully cancelled.</p>" +
            buildEventInfoTable(event) +
            "<p>We hope to see you at future events!</p>" +
            "<a href='" + baseUrl + "/student/events' style='" + btnStyle("607D8B") + "'>Browse Events</a>"
        );
    }

    private String buildEventApprovalHtml(Event event) {
        return emailWrapper(
            "Your Event Has Been Approved! ✅",
            "<p>Hi <strong>" + escapeHtml(event.getOrganizer().getName()) + "</strong>,</p>" +
            "<p>Your event <strong>" + escapeHtml(event.getTitle()) + "</strong> has been approved and is now visible to students.</p>" +
            buildEventInfoTable(event) +
            "<a href='" + baseUrl + "/organizer/events' style='" + btnStyle("2196F3") + "'>Manage Your Event</a>"
        );
    }

    private String buildEventRejectionHtml(Event event) {
        return emailWrapper(
            "Event Review Update",
            "<p>Hi <strong>" + escapeHtml(event.getOrganizer().getName()) + "</strong>,</p>" +
            "<p>Unfortunately, your event <strong>" + escapeHtml(event.getTitle()) + "</strong> was not approved at this time.</p>" +
            "<div style='background:#fff3cd;border-left:4px solid #ffc107;padding:12px 16px;margin:16px 0;border-radius:4px'>" +
            "<strong>Reason:</strong> " + escapeHtml(event.getRejectionReason() != null ? event.getRejectionReason() : "No reason provided") + "</div>" +
            "<p>You may revise and resubmit your event proposal after addressing the concerns mentioned above.</p>"
        );
    }

    private String buildEventCancellationHtml(Event event, User student) {
        return emailWrapper(
            "Event Cancelled ⚠️",
            "<p>Hi <strong>" + escapeHtml(student.getName()) + "</strong>,</p>" +
            "<p>We regret to inform you that the event <strong>" + escapeHtml(event.getTitle()) + "</strong> has been <strong style='color:#dc3545'>cancelled</strong>.</p>" +
            buildEventInfoTable(event) +
            "<p>If you paid registration fees, please contact the organizer at <strong>" + escapeHtml(event.getContactEmail()) + "</strong> for refund information.</p>" +
            "<p>We apologize for any inconvenience caused and invite you to explore other upcoming events.</p>" +
            "<a href='" + baseUrl + "/student/events' style='" + btnStyle("607D8B") + "'>Browse Events</a>"
        );
    }

    private String buildEventPostponementHtml(Event event, User student) {
        String newDate = event.getEventDate() != null ? event.getEventDate().format(DATE_FORMAT) : "TBD";
        return emailWrapper(
            "Event Postponed 📅",
            "<p>Hi <strong>" + escapeHtml(student.getName()) + "</strong>,</p>" +
            "<p>The event <strong>" + escapeHtml(event.getTitle()) + "</strong> has been <strong>postponed</strong> to a new date.</p>" +
            "<div style='background:#e3f2fd;border-left:4px solid #2196F3;padding:16px;margin:16px 0;border-radius:4px'>" +
            "<p><strong>📅 New Date:</strong> " + newDate + "</p>" +
            "</div>" +
            buildEventInfoTable(event) +
            "<p>Your registration remains valid. If you cannot attend the new date, please cancel your registration from your dashboard.</p>" +
            "<a href='" + baseUrl + "/student/my-tickets' style='" + btnStyle("FF6B35") + "'>View My Tickets</a>"
        );
    }

    private String buildNewEventAnnouncementHtml(Event event, User student) {
        return emailWrapper(
            "New Event Alert 🎉",
            "<p>Hi <strong>" + escapeHtml(student.getName()) + "</strong>,</p>" +
            "<p>A new event has just been added to <strong>" + appName + "</strong> that you might be interested in!</p>" +
            "<h3 style='color:#6C63FF;margin:16px 0 8px'>" + escapeHtml(event.getTitle()) + "</h3>" +
            buildEventInfoTable(event) +
            (event.getDescription() != null ? "<p style='color:#555'>" + escapeHtml(event.getDescription().substring(0, Math.min(event.getDescription().length(), 200))) + "...</p>" : "") +
            "<a href='" + baseUrl + "/student/events" + "' style='" + btnStyle("6C63FF") + "'>View & Register Now</a>"
        );
    }

    private String buildAnnouncementHtml(String eventTitle, String subject, String message) {
        return emailWrapper(
            escapeHtml(subject),
            "<p>This is an announcement from the organizers of <strong>" + escapeHtml(eventTitle) + "</strong>:</p>" +
            "<div style='background:#e3f2fd;border-left:4px solid #2196F3;padding:16px;margin:16px 0;border-radius:4px'>" +
            "<p style='margin:0'>" + escapeHtml(message).replace("\n", "<br>") + "</p></div>"
        );
    }

    private String buildPasswordResetHtml(String resetLink) {
        return emailWrapper(
            "Password Reset Request 🔑",
            "<p>We received a request to reset your password for <strong>" + escapeHtml(appName) + "</strong>.</p>" +
            "<p>Click the button below to set a new password. This link expires in <strong>30 minutes</strong>.</p>" +
            "<div style='text-align:center;margin:24px 0'>" +
            "<a href='" + escapeHtml(resetLink) + "' style='" + btnStyle("6C63FF") + "'>Reset My Password</a>" +
            "</div>" +
            "<p style='font-size:13px;color:#999'>If you did not request this, please ignore this email. Your password will remain unchanged.</p>"
        );
    }

    private String buildAttendanceHtml(Registration registration) {
        Event event = registration.getEvent();
        return emailWrapper(
            "Attendance Record Update",
            "<p>Hi <strong>" + escapeHtml(registration.getUser().getName()) + "</strong>,</p>" +
            "<p>An attendance record was updated for <strong>" + escapeHtml(event.getTitle()) + "</strong>.</p>" +
            buildEventInfoTable(event) +
            "<div style='background:#e8f7ef;border-left:4px solid #28a745;padding:16px;margin:16px 0;border-radius:4px'>" +
            "<p style='margin:0'>Certificate eligibility requires separate reviewed evidence and finalization. This message does not establish duration or issue a certificate.</p>" +
            "</div>" +
            "<div style='text-align:center;margin-top:20px'>" +
            "<a href='" + baseUrl + "/app/' style='" + btnStyle("28A745") + "'>View Dashboard</a>" +
            "</div>" +
            "<p style='font-size:13px;color:#777;margin-top:20px'>Only finalized certificates are available in your account.</p>"
        );
    }

    private String buildEventInfoTable(Event event) {
        String dateStr = event.getEventDate() != null ? event.getEventDate().format(DATE_FORMAT) : "TBD";
        return "<table style='width:100%;border-collapse:collapse;margin:16px 0'>" +
               "<tr><td style='padding:8px;background:#f8f9fa;font-weight:bold;width:30%'>📅 Date</td>" +
               "<td style='padding:8px'>" + dateStr + "</td></tr>" +
               "<tr><td style='padding:8px;background:#f8f9fa;font-weight:bold'>📍 Venue</td>" +
               "<td style='padding:8px'>" + escapeHtml(event.getVenue()) + "</td></tr>" +
               (event.getCategory() != null ? "<tr><td style='padding:8px;background:#f8f9fa;font-weight:bold'>🏷️ Category</td>" +
               "<td style='padding:8px'>" + escapeHtml(event.getCategory().getName()) + "</td></tr>" : "") +
               (event.getFees() != null && event.getFees() > 0
                   ? "<tr><td style='padding:8px;background:#f8f9fa;font-weight:bold'>💰 Fees</td>" +
                     "<td style='padding:8px'>₹" + event.getFees() + "</td></tr>" : "") +
               "</table>";
    }

    private static String escapeHtml(String value) {
        return org.springframework.web.util.HtmlUtils.htmlEscape(value == null ? "" : value);
    }

    private String btnStyle(String color) {
        return "display:inline-block;background:#" + color + ";color:#fff;padding:12px 28px;" +
               "text-decoration:none;border-radius:6px;font-weight:bold;margin-top:16px";
    }

    private String emailWrapper(String title, String content) {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'></head>" +
               "<body style='font-family:Segoe UI,Arial,sans-serif;background:#f4f4f4;margin:0;padding:20px'>" +
               "<div style='max-width:600px;margin:auto;background:#fff;border-radius:12px;overflow:hidden;box-shadow:0 4px 16px rgba(0,0,0,0.08)'>" +
               "<div style='background:linear-gradient(135deg,#6C63FF 0%,#3D35A0 100%);padding:28px 32px'>" +
               "<h1 style='color:#fff;margin:0;font-size:22px'>🎓 " + appName + "</h1>" +
               "<p style='color:rgba(255,255,255,0.8);margin:4px 0 0;font-size:13px'>Central University of Jammu</p></div>" +
               "<div style='padding:32px'>" +
               "<h2 style='color:#333;margin-top:0'>" + title + "</h2>" + content +
               "</div>" +
               "<div style='background:#f8f9fa;padding:16px 32px;text-align:center;color:#999;font-size:12px'>" +
               "© 2026 " + appName + " · Central University of Jammu · This is an automated email, please do not reply</div>" +
               "</div></body></html>";
    }
}

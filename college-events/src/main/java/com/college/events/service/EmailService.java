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

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class EmailService {

    private final JavaMailSender mailSender;
    private final EmailRetryService emailRetryService;
    private final QRService qrService;

    @Value("${spring.mail.username:noreply@college.com}")
    private String fromEmail;

    @Value("${app.name:CUJAMMU EVENTHUB}")
    private String appName;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    private static final DateTimeFormatter DATE_FORMAT =
        DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a");

    // ─── REGISTRATION EMAILS ────────────────────────────────────────────────

    @Async
    public void sendRegistrationConfirmation(Registration registration) {
        try {
            Event event = registration.getEvent();
            String to = registration.getUser().getEmail();
            String subject = "✅ Registration Confirmed – " + event.getTitle();
            String cid = "qrcode";
            byte[] qrBytes = qrService.getQrImageBytes(registration.getQrToken());
            String body = buildRegistrationConfirmationHtml(registration, cid);
            sendHtmlEmailWithInline(to, subject, body, cid, qrBytes, "image/png");
            log.info("Registration confirmation email sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send registration confirmation email", e);
        }
    }

    @Async
    public void sendWaitlistConfirmation(Registration registration) {
        try {
            Event event = registration.getEvent();
            String to = registration.getUser().getEmail();
            String subject = "⏳ You're on the Waitlist – " + event.getTitle();
            String body = buildWaitlistHtml(registration);
            sendHtmlEmail(to, subject, body);
        } catch (Exception e) {
            log.error("Failed to send waitlist email", e);
        }
    }

    @Async
    public void sendWaitlistPromotion(Registration registration) {
        try {
            Event event = registration.getEvent();
            String to = registration.getUser().getEmail();
            String subject = "🎉 You're In! Waitlist Promoted – " + event.getTitle();
            String body = buildWaitlistPromotionHtml(registration);
            sendHtmlEmail(to, subject, body);
        } catch (Exception e) {
            log.error("Failed to send waitlist promotion email", e);
        }
    }

    @Async
    public void sendCancellationConfirmation(Registration registration) {
        try {
            Event event = registration.getEvent();
            String to = registration.getUser().getEmail();
            String subject = "Registration Cancelled – " + event.getTitle();
            String body = buildCancellationHtml(registration);
            sendHtmlEmail(to, subject, body);
        } catch (Exception e) {
            log.error("Failed to send cancellation email", e);
        }
    }

    // ─── EVENT MANAGEMENT EMAILS ────────────────────────────────────────────

    @Async
    public void sendEventApprovalNotification(Event event) {
        try {
            String to = event.getOrganizer().getEmail();
            String subject = "✅ Event Approved – " + event.getTitle();
            String body = buildEventApprovalHtml(event);
            sendHtmlEmail(to, subject, body);
        } catch (Exception e) {
            log.error("Failed to send event approval email", e);
        }
    }

    @Async
    public void sendEventRejectionNotification(Event event) {
        try {
            String to = event.getOrganizer().getEmail();
            String subject = "❌ Event Not Approved – " + event.getTitle();
            String body = buildEventRejectionHtml(event);
            sendHtmlEmail(to, subject, body);
        } catch (Exception e) {
            log.error("Failed to send event rejection email", e);
        }
    }

    @Async
    public void sendEventCancellationNotification(Event event, List<Registration> registrations) {
        for (Registration reg : registrations) {
            try {
                String subject = "⚠️ Event Cancelled – " + event.getTitle();
                String body = buildEventCancellationHtml(event, reg.getUser());
                sendHtmlEmail(reg.getUser().getEmail(), subject, body);
            } catch (Exception e) {
                log.error("Failed to send cancellation notice to {}", reg.getUser().getEmail(), e);
            }
        }
        log.info("Event cancellation emails sent for '{}'", event.getTitle());
    }

    @Async
    public void sendAttendanceConfirmation(Registration registration) {
        try {
            Event event = registration.getEvent();
            String to = registration.getUser().getEmail();
            String subject = "You have successfully attended " + event.getTitle();
            String body = buildAttendanceHtml(registration);
            sendHtmlEmail(to, subject, body);
            log.info("Attendance confirmation email sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send attendance confirmation email to {}", registration.getUser().getEmail(), e);
        }
    }

    @Async
    public void sendEventPostponementNotification(Event event, List<Registration> registrations) {
        for (Registration reg : registrations) {
            try {
                String subject = "📅 Event Postponed – " + event.getTitle();
                String body = buildEventPostponementHtml(event, reg.getUser());
                sendHtmlEmail(reg.getUser().getEmail(), subject, body);
            } catch (Exception e) {
                log.error("Failed to send postponement notice to {}", reg.getUser().getEmail(), e);
            }
        }
        log.info("Event postponement emails sent for '{}'", event.getTitle());
    }

    @Async
    public void sendNewEventAnnouncement(Event event, List<User> students) {
        for (User student : students) {
            try {
                String subject = "🎉 New Event: " + event.getTitle() + " | " + appName;
                String body = buildNewEventAnnouncementHtml(event, student);
                sendHtmlEmail(student.getEmail(), subject, body);
            } catch (Exception e) {
                log.error("Failed to send new event announcement to {}", student.getEmail(), e);
            }
        }
        log.info("New event announcement sent for '{}' to {} students", event.getTitle(), students.size());
    }

    @Async
    public void sendBulkAnnouncement(List<String> emails, String eventTitle,
                                     String announcementSubject, String message) {
        for (String email : emails) {
            try {
                String subject = "📢 " + announcementSubject + " – " + eventTitle;
                String body = buildAnnouncementHtml(eventTitle, announcementSubject, message);
                sendHtmlEmail(email, subject, body);
            } catch (Exception e) {
                log.error("Failed to send announcement to {}", email, e);
            }
        }
    }

    @Async
    public void sendPasswordResetEmail(String email, String resetLink, String token) {
        try {
            String subject = "🔑 Password Reset Request – " + appName;
            String body = buildPasswordResetHtml(resetLink, token);
            sendHtmlEmail(email, subject, body);
            log.info("Password reset email sent to {}", email);
        } catch (Exception e) {
            log.warn("Could not send password reset email to {} (SMTP not configured?): {}", email, e.getMessage());
        }
    }

    @Value("${app.email.backend:smtp}")
    private String emailBackend;

    // ─── OTP EMAIL ────────────────────────────────────────────────────────────

    @Async
    public java.util.concurrent.CompletableFuture<Boolean> sendOtpEmail(String email, String otp, String userName) {
        log.info("Sending OTP to {}", email);
        if ("console".equalsIgnoreCase(emailBackend)) {
            log.info("\n=========================================\n" +
                     "CONSOLE EMAIL BACKEND - OTP GENERATED\n" +
                     "To: {}\n" +
                     "OTP Code: {}\n" +
                     "=========================================\n", email, otp);
            return java.util.concurrent.CompletableFuture.completedFuture(true);
        }

        try {
            String subject = "🔐 Your Verification Code – " + appName;
            String body = emailWrapper("Email Verification 🔐",
                "<p>Hi <strong>" + (userName != null ? userName : "there") + "</strong>,</p>" +
                "<p>Your one-time verification code for <strong>" + appName + "</strong> is:</p>" +
                "<div style='text-align:center;margin:24px 0'>" +
                "<div style='display:inline-block;background:linear-gradient(135deg,#6C63FF,#3D35A0);color:#fff;" +
                "font-size:36px;font-weight:bold;letter-spacing:12px;padding:16px 32px;border-radius:12px;" +
                "font-family:monospace'>" + otp + "</div></div>" +
                "<p style='text-align:center;color:#dc3545;font-weight:bold'>⏱ This code expires in 10 minutes</p>" +
                "<div style='background:#fff3cd;border-left:4px solid #ffc107;padding:12px 16px;margin:16px 0;border-radius:4px'>" +
                "<strong>⚠️ Security Notice:</strong> Never share this code with anyone. " +
                appName + " staff will never ask for your OTP.</div>");
            sendHtmlEmail(email, subject, body);
            log.info("OTP sent successfully to {}", email);
            return java.util.concurrent.CompletableFuture.completedFuture(true);
        } catch (Exception e) {
            log.error("Failed to send OTP: {}", e.getMessage(), e);
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }
    }

    // ─── CERTIFICATE EMAIL ───────────────────────────────────────────────────

    @Async
    public void sendCertificateEmail(Registration registration, String downloadUrl) {
        try {
            Event event = registration.getEvent();
            String to = registration.getUser().getEmail();
            String subject = "🏆 Your Certificate – " + event.getTitle();
            String body = emailWrapper("Certificate Issued! 🏆",
                "<p>Hi <strong>" + registration.getUser().getName() + "</strong>,</p>" +
                "<p>Congratulations! Your participation certificate for <strong>" +
                event.getTitle() + "</strong> has been issued.</p>" +
                buildEventInfoTable(event) +
                "<div style='text-align:center;margin:24px 0'>" +
                "<a href='" + baseUrl + downloadUrl + "' style='" + btnStyle("6C63FF") +
                "'>📥 Download Certificate</a></div>" +
                "<p>You can also view and share your certificate from your dashboard.</p>");
            sendHtmlEmail(to, subject, body);
            log.info("Certificate email sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send certificate email", e);
        }
    }

    // ─── CORE EMAIL SENDER (with retry) ──────────────────────────────────────

    private void sendHtmlEmail(String to, String subject, String htmlBody) throws Exception {
        sendHtmlEmailWithInline(to, subject, htmlBody, null, null, null);
    }

    private void sendHtmlEmailWithInline(String to, String subject, String htmlBody, 
                                         String cid, byte[] inlineBytes, String contentType) throws Exception {
        if ("console".equalsIgnoreCase(emailBackend)) {
            log.info("\n=== CONSOLE EMAIL (Subject: {}) ===\nTo: {}\n==================================", subject, to);
            return;
        }

        if (fromEmail == null || fromEmail.isBlank() || fromEmail.contains("your_")) {
            log.warn("📧 Email not configured (EMAIL_HOST_USER not set). Skipping email to {}: {}", to, subject);
            return;
        }

        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(fromEmail, appName);
                helper.setTo(to);
                helper.setSubject(subject);
                helper.setText(htmlBody, true);
                
                if (cid != null && inlineBytes != null) {
                    // Anonymous subclass overriding getFilename() is REQUIRED.
                    // Without a filename, Gmail/Outlook reject the inline attachment
                    // and display a broken image icon instead of the QR code.
                    final byte[] finalBytes = inlineBytes;
                    helper.addInline(cid, new org.springframework.core.io.ByteArrayResource(finalBytes) {
                        @Override
                        public String getFilename() {
                            return cid + ".png";
                        }
                    }, contentType);
                }
                
                mailSender.send(message);
                return; // Success
            } catch (Exception e) {
                if (attempt == maxRetries) {
                    log.error("Email failed after {} retries, queuing for background retry: to={}", maxRetries, to);
                    emailRetryService.enqueueForRetry(to, subject, htmlBody, fromEmail, appName);
                    throw e; // throw up to caller so they know it failed synchronously
                }
                log.warn("Email attempt {}/{} failed for {}: {}", attempt, maxRetries, to, e.getMessage());
                try { TimeUnit.SECONDS.sleep(attempt); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }
    }

    // ─── HTML TEMPLATES ───────────────────────────────────────────────────────

    private String buildRegistrationConfirmationHtml(Registration registration, String cid) {
        Event event = registration.getEvent();
        User user = registration.getUser();
        String enrollNo = (user.getEnrollmentNo() != null && !user.getEnrollmentNo().isBlank())
            ? user.getEnrollmentNo() : "N/A";
        String dept = (user.getDepartment() != null) ? user.getDepartment() : "N/A";

        return emailWrapper(
            "Registration Confirmed! 🎉",
            "<p>Hi <strong>" + user.getName() + "</strong>,</p>" +
            "<p>You have successfully registered for <strong>" + event.getTitle() + "</strong>.</p>" +
            buildEventInfoTable(event) +
            "<table style='width:100%;border-collapse:collapse;margin:16px 0;background:#f8f9ff;border-radius:8px'>" +
            "<tr><td style='padding:10px;font-weight:bold;width:40%'>👤 Student Name</td><td style='padding:10px'>" + user.getName() + "</td></tr>" +
            "<tr><td style='padding:10px;font-weight:bold'>🎓 Enrollment No</td><td style='padding:10px'>" + enrollNo + "</td></tr>" +
            "<tr><td style='padding:10px;font-weight:bold'>🏫 Department</td><td style='padding:10px'>" + dept + "</td></tr>" +
            "<tr><td style='padding:10px;font-weight:bold'>🔑 QR Token</td><td style='padding:10px;font-family:monospace;font-size:12px'>" + registration.getQrToken() + "</td></tr>" +
            "</table>" +
            "<div style='text-align:center;margin:24px 0;padding:24px;background:#fff;border:2px solid #e2e8f0;border-radius:12px;box-shadow:0 4px 6px -1px rgba(0,0,0,0.1)'>" +
            "<p style='margin-top:0;color:#64748b;font-weight:bold'>YOUR DIGITAL TICKET</p>" +
            "<img src='cid:" + cid + "' alt='QR Code Ticket' style='max-width:280px;height:auto;display:inline-block;border-radius:8px'/>" +
            "</div>" +
            "<p>Show your QR code at the event entrance for attendance. Log in to view your digital ticket.</p>" +
            "<div style='text-align:center'>" +
            "<a href='" + baseUrl + "/student/my-tickets' style='" + btnStyle("6C63FF") + "'>View My QR Ticket</a>" +
            "</div>"
        );
    }

    private String buildWaitlistHtml(Registration registration) {
        Event event = registration.getEvent();
        return emailWrapper(
            "You're on the Waitlist ⏳",
            "<p>Hi <strong>" + registration.getUser().getName() + "</strong>,</p>" +
            "<p>The event <strong>" + event.getTitle() + "</strong> is currently full, but you've been added to the waitlist.</p>" +
            buildEventInfoTable(event) +
            "<p>You'll be automatically notified and registered if a seat becomes available. No action needed!</p>"
        );
    }

    private String buildWaitlistPromotionHtml(Registration registration) {
        Event event = registration.getEvent();
        return emailWrapper(
            "Great News – You're In! 🎉",
            "<p>Hi <strong>" + registration.getUser().getName() + "</strong>,</p>" +
            "<p>A seat opened up for <strong>" + event.getTitle() + "</strong> and you've been automatically registered!</p>" +
            buildEventInfoTable(event) +
            "<p><strong>Your QR Token:</strong> <code style='background:#f0f0f0;padding:4px 10px;border-radius:4px;font-size:14px'>" + registration.getQrToken() + "</code></p>" +
            "<a href='" + baseUrl + "/student/my-tickets' style='" + btnStyle("4CAF50") + "'>View My QR Ticket</a>"
        );
    }

    private String buildCancellationHtml(Registration registration) {
        Event event = registration.getEvent();
        return emailWrapper(
            "Registration Cancelled",
            "<p>Hi <strong>" + registration.getUser().getName() + "</strong>,</p>" +
            "<p>Your registration for <strong>" + event.getTitle() + "</strong> has been successfully cancelled.</p>" +
            buildEventInfoTable(event) +
            "<p>We hope to see you at future events!</p>" +
            "<a href='" + baseUrl + "/student/events' style='" + btnStyle("607D8B") + "'>Browse Events</a>"
        );
    }

    private String buildEventApprovalHtml(Event event) {
        return emailWrapper(
            "Your Event Has Been Approved! ✅",
            "<p>Hi <strong>" + event.getOrganizer().getName() + "</strong>,</p>" +
            "<p>Your event <strong>" + event.getTitle() + "</strong> has been approved and is now visible to students.</p>" +
            buildEventInfoTable(event) +
            "<a href='" + baseUrl + "/organizer/events' style='" + btnStyle("2196F3") + "'>Manage Your Event</a>"
        );
    }

    private String buildEventRejectionHtml(Event event) {
        return emailWrapper(
            "Event Review Update",
            "<p>Hi <strong>" + event.getOrganizer().getName() + "</strong>,</p>" +
            "<p>Unfortunately, your event <strong>" + event.getTitle() + "</strong> was not approved at this time.</p>" +
            "<div style='background:#fff3cd;border-left:4px solid #ffc107;padding:12px 16px;margin:16px 0;border-radius:4px'>" +
            "<strong>Reason:</strong> " + (event.getRejectionReason() != null ? event.getRejectionReason() : "No reason provided") + "</div>" +
            "<p>You may revise and resubmit your event proposal after addressing the concerns mentioned above.</p>"
        );
    }

    private String buildEventCancellationHtml(Event event, User student) {
        return emailWrapper(
            "Event Cancelled ⚠️",
            "<p>Hi <strong>" + student.getName() + "</strong>,</p>" +
            "<p>We regret to inform you that the event <strong>" + event.getTitle() + "</strong> has been <strong style='color:#dc3545'>cancelled</strong>.</p>" +
            buildEventInfoTable(event) +
            "<p>If you paid registration fees, please contact the organizer at <strong>" + event.getContactEmail() + "</strong> for refund information.</p>" +
            "<p>We apologize for any inconvenience caused and invite you to explore other upcoming events.</p>" +
            "<a href='" + baseUrl + "/student/events' style='" + btnStyle("607D8B") + "'>Browse Events</a>"
        );
    }

    private String buildEventPostponementHtml(Event event, User student) {
        String newDate = event.getEventDate() != null ? event.getEventDate().format(DATE_FORMAT) : "TBD";
        return emailWrapper(
            "Event Postponed 📅",
            "<p>Hi <strong>" + student.getName() + "</strong>,</p>" +
            "<p>The event <strong>" + event.getTitle() + "</strong> has been <strong>postponed</strong> to a new date.</p>" +
            "<div style='background:#e3f2fd;border-left:4px solid #2196F3;padding:16px;margin:16px 0;border-radius:4px'>" +
            "<p><strong>📅 New Date:</strong> " + newDate + "</p>" +
            (event.getPostponeReason() != null ? "<p><strong>📝 Reason:</strong> " + event.getPostponeReason() + "</p>" : "") +
            "</div>" +
            buildEventInfoTable(event) +
            "<p>Your registration remains valid. If you cannot attend the new date, please cancel your registration from your dashboard.</p>" +
            "<a href='" + baseUrl + "/student/my-tickets' style='" + btnStyle("FF6B35") + "'>View My Tickets</a>"
        );
    }

    private String buildNewEventAnnouncementHtml(Event event, User student) {
        return emailWrapper(
            "New Event Alert 🎉",
            "<p>Hi <strong>" + student.getName() + "</strong>,</p>" +
            "<p>A new event has just been added to <strong>" + appName + "</strong> that you might be interested in!</p>" +
            "<h3 style='color:#6C63FF;margin:16px 0 8px'>" + event.getTitle() + "</h3>" +
            buildEventInfoTable(event) +
            (event.getDescription() != null ? "<p style='color:#555'>" + event.getDescription().substring(0, Math.min(event.getDescription().length(), 200)) + "...</p>" : "") +
            "<a href='" + baseUrl + "/student/events" + "' style='" + btnStyle("6C63FF") + "'>View & Register Now</a>"
        );
    }

    private String buildAnnouncementHtml(String eventTitle, String subject, String message) {
        return emailWrapper(
            subject,
            "<p>This is an announcement from the organizers of <strong>" + eventTitle + "</strong>:</p>" +
            "<div style='background:#e3f2fd;border-left:4px solid #2196F3;padding:16px;margin:16px 0;border-radius:4px'>" +
            "<p style='margin:0'>" + message.replace("\n", "<br>") + "</p></div>"
        );
    }

    private String buildPasswordResetHtml(String resetLink, String token) {
        return emailWrapper(
            "Password Reset Request 🔑",
            "<p>We received a request to reset your password for <strong>" + appName + "</strong>.</p>" +
            "<p>Click the button below to set a new password. This link expires in <strong>30 minutes</strong>.</p>" +
            "<div style='text-align:center;margin:24px 0'>" +
            "<a href='" + resetLink + "' style='" + btnStyle("6C63FF") + "'>Reset My Password</a>" +
            "</div>" +
            "<p style='font-size:13px;color:#999'>Or copy and paste this URL: <br>" +
            "<code style='font-size:11px;word-break:break-all'>" + resetLink + "</code></p>" +
            "<p style='font-size:13px;color:#999'><strong>Reset Token:</strong> <code>" + token + "</code></p>" +
            "<p style='font-size:13px;color:#999'>If you did not request this, please ignore this email. Your password will remain unchanged.</p>"
        );
    }

    private String buildAttendanceHtml(Registration registration) {
        Event event = registration.getEvent();
        String certificateUrl = baseUrl + "/student/certificate/" + registration.getId();
        String verificationUrl = baseUrl + "/attendance/certificate/" + registration.getQrToken();

        return emailWrapper(
            "Attendance Confirmed",
            "<p>Hi <strong>" + registration.getUser().getName() + "</strong>,</p>" +
            "<p><strong>You have successfully attended " + event.getTitle() + ".</strong></p>" +
            buildEventInfoTable(event) +
            "<div style='background:#e8f7ef;border-left:4px solid #28a745;padding:16px;margin:16px 0;border-radius:4px'>" +
            "<p style='margin:0 0 8px'><strong>Status:</strong> Verified attendance</p>" +
            "<p style='margin:0'><strong>Certificate:</strong> Your participation certificate is ready.</p>" +
            "</div>" +
            "<div style='text-align:center;margin-top:20px'>" +
            "<a href='" + certificateUrl + "' style='" + btnStyle("28A745") + "'>Download Certificate</a>" +
            "</div>" +
            "<p style='font-size:13px;color:#777;margin-top:20px'>Public verification link: <a href='" + verificationUrl + "'>" + verificationUrl + "</a></p>"
        );
    }

    private String buildEventInfoTable(Event event) {
        String dateStr = event.getEventDate() != null ? event.getEventDate().format(DATE_FORMAT) : "TBD";
        return "<table style='width:100%;border-collapse:collapse;margin:16px 0'>" +
               "<tr><td style='padding:8px;background:#f8f9fa;font-weight:bold;width:30%'>📅 Date</td>" +
               "<td style='padding:8px'>" + dateStr + "</td></tr>" +
               "<tr><td style='padding:8px;background:#f8f9fa;font-weight:bold'>📍 Venue</td>" +
               "<td style='padding:8px'>" + event.getVenue() + "</td></tr>" +
               (event.getCategory() != null ? "<tr><td style='padding:8px;background:#f8f9fa;font-weight:bold'>🏷️ Category</td>" +
               "<td style='padding:8px'>" + event.getCategory().getName() + "</td></tr>" : "") +
               (event.getFees() != null && event.getFees() > 0
                   ? "<tr><td style='padding:8px;background:#f8f9fa;font-weight:bold'>💰 Fees</td>" +
                     "<td style='padding:8px'>₹" + event.getFees() + "</td></tr>" : "") +
               "</table>";
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

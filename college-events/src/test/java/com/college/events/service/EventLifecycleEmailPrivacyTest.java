package com.college.events.service;

import com.college.events.model.Event;
import com.college.events.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class EventLifecycleEmailPrivacyTest {
    private final EmailService email = new EmailService(mock(JavaMailSender.class),
            mock(EmailRetryService.class));

    @Test void postponeEmailEscapesHtmlAndNeverDisclosesPrivateOperationalReason() {
        Event event = Event.builder().title("<img src=x onerror=alert(1)>")
                .venue("Hall <script>alert(2)</script>")
                .eventDate(LocalDateTime.parse("2030-05-02T10:00:00"))
                .postponeReason("private staffing details").build();
        User student = User.builder().name("<svg onload=alert(3)>").build();
        String html = ReflectionTestUtils.invokeMethod(email, "buildEventPostponementHtml", event, student);

        assertNotNull(html);
        assertTrue(html.contains("&lt;img"));
        assertTrue(html.contains("&lt;script&gt;"));
        assertTrue(html.contains("&lt;svg"));
        assertFalse(html.contains("private staffing details"));
        assertFalse(html.contains("<img"));
        assertFalse(html.contains("<script"));
        assertFalse(html.contains("<svg"));
    }
}

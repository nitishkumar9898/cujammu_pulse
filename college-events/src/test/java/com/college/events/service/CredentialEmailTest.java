package com.college.events.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CredentialEmailTest {
    @Test void providerErrorsCannotLeakSecretsOrQueueRecoveryAndOtpMail() throws Exception {
        var sender = mock(JavaMailSender.class);
        var retries = mock(EmailRetryService.class);
        var service = new EmailService(sender, retries);
        ReflectionTestUtils.setField(service, "emailBackend", "smtp");
        ReflectionTestUtils.setField(service, "fromEmail", "sender@example.invalid");
        ReflectionTestUtils.setField(service, "appName", "Synthetic Test");
        ReflectionTestUtils.setField(service, "resetLinkAllowedHosts", "example.invalid");
        when(sender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
        String secret = "A".repeat(43);
        doThrow(new MailSendException(secret)).when(sender).send(any(MimeMessage.class));
        Logger logger = (Logger) LoggerFactory.getLogger(EmailService.class);
        var appender = new ListAppender<ILoggingEvent>(); appender.start(); logger.addAppender(appender);
        try {
            service.sendPasswordResetEmail("student@example.invalid", "https://example.invalid/reset-password?token=" + secret);
            assertFalse(service.sendOtpEmail("student@example.invalid", "123456", "Synthetic").get());
            verifyNoInteractions(retries);
            var captured=org.mockito.ArgumentCaptor.forClass(MimeMessage.class);
            verify(sender, times(2)).send(captured.capture());
            String recoveryBody=extractText(captured.getAllValues().getFirst());
            assertEquals(1,recoveryBody.split(java.util.regex.Pattern.quote(secret),-1).length-1);
            assertFalse(recoveryBody.contains("Reset Token"));
            assertFalse(recoveryBody.contains("<script"));
            assertTrue(appender.list.stream().noneMatch(entry -> entry.getFormattedMessage().contains(secret)
                    || entry.getFormattedMessage().contains("123456") || entry.getThrowableProxy() != null));
        } finally { logger.detachAppender(appender); appender.stop(); }
    }

    private static String extractText(Part part) throws Exception {
        Object content=part.getContent();
        if(content instanceof String text)return text;
        if(content instanceof jakarta.mail.Multipart multipart) {
            StringBuilder result=new StringBuilder();
            for(int i=0;i<multipart.getCount();i++)result.append(extractText(multipart.getBodyPart(i)));
            return result.toString();
        }
        return "";
    }

    @Test void consoleNeverExposesOrClaimsDeliveryOfOtp() throws Exception {
        var sender = mock(JavaMailSender.class);
        var retries = mock(EmailRetryService.class);
        var service = new EmailService(sender, retries);
        ReflectionTestUtils.setField(service, "emailBackend", "console");
        Logger logger = (Logger) LoggerFactory.getLogger(EmailService.class);
        var appender = new ListAppender<ILoggingEvent>(); appender.start(); logger.addAppender(appender);
        try {
            assertFalse(service.sendOtpEmail("student@example.invalid", "876543", "Synthetic").get());
            assertTrue(appender.list.stream().noneMatch(entry -> entry.getFormattedMessage().contains("876543")));
            verifyNoInteractions(sender, retries);
        } finally { logger.detachAppender(appender); appender.stop(); }
    }

    @Test void otpGenerationNeverLogsTheCredential() {
        Logger logger = (Logger) LoggerFactory.getLogger(OtpService.class);
        var appender = new ListAppender<ILoggingEvent>(); appender.start(); logger.addAppender(appender);
        try {
            String otp = new OtpService().generateOtp("student@example.invalid");
            assertNotNull(otp);
            assertTrue(appender.list.stream().noneMatch(entry -> entry.getFormattedMessage().contains(otp)));
        } finally { logger.detachAppender(appender); appender.stop(); }
    }
}

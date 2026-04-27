package com.college.events.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;


/**
 * Manages a retry queue for failed email deliveries.
 * Failed emails are retried every 30 seconds, up to 3 attempts.
 * After 3 failures, the email is dead-lettered (logged and discarded).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class EmailRetryService {

    private final JavaMailSender mailSender;

    private static final int MAX_RETRIES = 3;

    private final Queue<RetryEmail> retryQueue = new ConcurrentLinkedQueue<>();

    /**
     * Add a failed email to the retry queue.
     */
    public void enqueueForRetry(String to, String subject, String htmlBody,
                                 String fromEmail, String fromName) {
        RetryEmail email = new RetryEmail(to, subject, htmlBody, fromEmail, fromName, 0);
        retryQueue.offer(email);
        log.info("Email queued for retry: to={}, subject='{}', queueSize={}",
            to, subject, retryQueue.size());
    }

    /**
     * Process the retry queue every 30 seconds.
     */
    @Scheduled(fixedRate = 30_000)
    public void processRetryQueue() {
        if (retryQueue.isEmpty()) return;

        int processed = 0;
        int succeeded = 0;
        int deadLettered = 0;

        int queueSize = retryQueue.size();
        for (int i = 0; i < queueSize; i++) {
            RetryEmail email = retryQueue.poll();
            if (email == null) break;

            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(email.fromEmail, email.fromName);
                helper.setTo(email.to);
                helper.setSubject(email.subject);
                helper.setText(email.htmlBody, true);
                mailSender.send(message);

                succeeded++;
                log.info("Retry succeeded: to={}, subject='{}', attempt={}",
                    email.to, email.subject, email.retryCount + 1);
            } catch (Exception e) {
                email.retryCount++;
                if (email.retryCount >= MAX_RETRIES) {
                    deadLettered++;
                    log.error("DEAD-LETTER: Email permanently failed after {} retries: to={}, subject='{}', error={}",
                        MAX_RETRIES, email.to, email.subject, e.getMessage());
                } else {
                    retryQueue.offer(email);
                    log.warn("Retry failed (attempt {}/{}): to={}, subject='{}', error={}",
                        email.retryCount, MAX_RETRIES, email.to, email.subject, e.getMessage());
                }
            }
            processed++;
        }

        if (processed > 0) {
            log.info("Email retry batch: processed={}, succeeded={}, deadLettered={}, remaining={}",
                processed, succeeded, deadLettered, retryQueue.size());
        }
    }

    /**
     * Get current retry queue size for monitoring.
     */
    public int getQueueSize() {
        return retryQueue.size();
    }

    private static class RetryEmail {
        final String to;
        final String subject;
        final String htmlBody;
        final String fromEmail;
        final String fromName;
        int retryCount;

        RetryEmail(String to, String subject, String htmlBody,
                   String fromEmail, String fromName, int retryCount) {
            this.to = to;
            this.subject = subject;
            this.htmlBody = htmlBody;
            this.fromEmail = fromEmail;
            this.fromName = fromName;
            this.retryCount = retryCount;
        }
    }
}

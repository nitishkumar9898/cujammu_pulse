package com.college.events.service;

import com.college.events.model.MailDelivery.Status;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.net.URI;
import java.util.Arrays;

/** Restart-safe bounded mail worker. Provider acceptance is never called delivery/read. */
@Service @RequiredArgsConstructor @Slf4j
public class EmailRetryService {
    private final JavaMailSender mailSender;
    private final MailOutboxService outbox;
    private final Environment environment;
    @Value("${app.email.backend:smtp}") private String backend;
    @Value("${spring.mail.username:}") private String fromEmail;
    @Value("${app.base-url:http://localhost:8080}") private String baseUrl;

    @Scheduled(fixedDelayString="${app.mail-outbox.poll-ms:30000}", initialDelayString="${app.mail-outbox.initial-delay-ms:30000}")
    public void processRetryQueue() {
        // Fail instead of accidentally contacting a provider inside caller transaction.
        if(TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Mail worker must run outside domain transactions");
        outbox.scrubExpired();
        for(String id:outbox.due()) {
            var claim=outbox.claim(id);
            if(claim.isEmpty())continue;
            dispatch(claim.get());
        }
    }

    private void dispatch(MailOutboxService.Claim claim) {
        if(!"smtp".equalsIgnoreCase(backend) || Arrays.asList(environment.getActiveProfiles()).contains("professor-demo")){
            outbox.complete(claim,Status.SUPPRESSED,"LOCAL_OR_DISABLED_BACKEND");return;
        }
        if(fromEmail==null || fromEmail.isBlank() || fromEmail.contains("your_") || fromEmail.contains("\r") || fromEmail.contains("\n")){
            outbox.complete(claim,Status.SUPPRESSED,"SENDER_UNCONFIGURED");return;
        }
        String link=dashboardLink();
        if(link==null){outbox.complete(claim,Status.SUPPRESSED,"LINK_UNCONFIGURED");return;}
        try {
            MimeMessage message=mailSender.createMimeMessage();
            MimeMessageHelper helper=new MimeMessageHelper(message,false,"UTF-8");
            helper.setFrom(fromEmail,"CUJAMMU Pulse");helper.setTo(claim.recipient());
            helper.setSubject("CUJAMMU Pulse account update");
            // Only fixed type and authenticated link leave the platform. No title, reason,
            // credential, academic content or mutable free-text announcement is replayed.
            helper.setText("An update is available in your CUJAMMU Pulse account ("+
                    claim.kind().name().toLowerCase(java.util.Locale.ROOT).replace('_',' ')+").\n"+
                    "Sign in to view the current details: "+link+"\n"+
                    "Your dashboard shows the latest status.",false);
            message.setHeader("Message-ID","<"+claim.id()+"@pulse.invalid>");
            mailSender.send(message);
            outbox.complete(claim,Status.PROVIDER_ACCEPTED,"SMTP_ACCEPTED");
        } catch(Exception failure){
            // Never log provider exception text: providers may echo addresses or credentials.
            log.warn("Mail delivery attempt failed; deliveryId={}, failureType={}",claim.id(),failure.getClass().getSimpleName());
            outbox.complete(claim,Status.RETRY,"PROVIDER_FAILURE");
        }
    }

    private String dashboardLink(){
        try {
            URI uri=URI.create(baseUrl);
            if(!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null ||
                    uri.getQuery()!=null || uri.getFragment()!=null)return null;
            return baseUrl.replaceAll("/+$","")+"/app/";
        }catch(IllegalArgumentException failure){return null;}
    }
    public int getQueueSize(){return (int)Math.min(Integer.MAX_VALUE,outbox.pendingCount());}
}

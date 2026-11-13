package com.college.events.service;

import com.college.events.model.Registration;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;

@Service
@RequiredArgsConstructor
@Slf4j
public class CertificateService {

    private final TemplateEngine templateEngine;
    private final PointCertificateIssuanceService issuance;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public byte[] generateCertificatePdf(Registration registration) {
        com.college.events.model.Certificate cert = issuance.owned(registration.getId());
        try {

            Context context = new Context();
            context.setVariable("eventDate", 
                cert.getEventDateSnapshot().format(DateTimeFormatter.ofPattern("dd MMMM, yyyy")));
            context.setVariable("userName", cert.getRecipientNameSnapshot());
            context.setVariable("eventTitle", cert.getEventTitleSnapshot());
            context.setVariable("enrollmentNo", cert.getVerifiedIdentifierSnapshot());
            context.setVariable("certNo", cert.getCertificateNumber());
            context.setVariable("verificationUrl", baseUrl + "/certificates/verify/" + cert.getCertificateNumber());
            context.setVariable("universityLogoDataUri", universityLogoDataUri());
            
            // Render HTML template to String
            String html = templateEngine.process("point-certificate-pdf-template", context);

            // Convert HTML to PDF using OpenHTMLtoPDF
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, "/");
            builder.toStream(os);
            builder.run();

            return os.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate PDF certificate", e);
            throw new IllegalStateException("Certificate PDF generation failed", e);
        }
    }

    private String universityLogoDataUri() {
        try (InputStream logo = new ClassPathResource("static/images/cujammu_logo.png").getInputStream()) {
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(logo.readAllBytes());
        } catch (IOException e) {
            log.warn("University seal unavailable while rendering certificate");
            return null;
        }
    }

    public java.util.List<com.college.events.model.Certificate> findByEvent(com.college.events.model.Event event) {
        return issuance.managed(event);
    }

    public void issueCertificatesForEvent(com.college.events.model.Event event, String templateId, EmailService emailService) {
        throw new IllegalStateException("Use reviewed point-attendance finalization; legacy certificate email issuance is unavailable");
    }
}

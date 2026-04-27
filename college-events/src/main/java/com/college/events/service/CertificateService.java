package com.college.events.service;

import com.college.events.model.Registration;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

import com.college.events.repository.CertificateRepository;
import org.springframework.beans.factory.annotation.Value;

@Service
@RequiredArgsConstructor
@Slf4j
public class CertificateService {

    private final TemplateEngine templateEngine;
    private final CertificateRepository certificateRepository;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public byte[] generateCertificatePdf(Registration registration) {
        try {
            com.college.events.model.Certificate cert = certificateRepository.findByRegistration(registration)
                .orElseThrow(() -> new RuntimeException("Certificate not found for registration."));

            Context context = new Context();
            context.setVariable("reg", registration);
            context.setVariable("eventDate", 
                registration.getEvent().getEventDate() != null ? registration.getEvent().getEventDate().format(DateTimeFormatter.ofPattern("dd MMMM, yyyy")) : "TBD");
            context.setVariable("userName", registration.getUser().getName());
            context.setVariable("eventTitle", registration.getEvent().getTitle());
            context.setVariable("enrollmentNo", registration.getUser().getEnrollmentNo());
            context.setVariable("department", registration.getUser().getDepartment());
            context.setVariable("certNo", cert.getCertificateNumber());
            context.setVariable("verificationUrl", baseUrl + "/verify/" + cert.getCertificateNumber());
            
            // Render HTML template to String
            String html = templateEngine.process("certificate-pdf-template", context);

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
            throw new RuntimeException("PDF generation failed: " + e.getMessage());
        }
    }

    public java.util.List<com.college.events.model.Certificate> findByEvent(com.college.events.model.Event event) {
        return certificateRepository.findByEventOrderByIssuedAtDesc(event);
    }

    public void issueCertificatesForEvent(com.college.events.model.Event event, String templateId, EmailService emailService) {
        java.util.List<com.college.events.model.Certificate> certificates = certificateRepository.findByEventOrderByIssuedAtDesc(event);
        for (com.college.events.model.Certificate cert : certificates) {
            cert.setTemplateId(templateId);
            cert.setEmailed(true);
            certificateRepository.save(cert);
            emailService.sendCertificateEmail(cert.getRegistration(), "/student/certificates/" + cert.getRegistration().getId() + "/download");
        }
    }
}

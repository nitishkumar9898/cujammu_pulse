package com.college.events.service;

import com.college.events.model.Certificate;
import com.college.events.model.Registration;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CertificatePdfRenderingTest {
    @Test void actualGovernedCertificateTemplateRendersWithoutUnusedSvgRuntime() throws Exception {
        var templates=new SpringTemplateEngine();
        var resolver=new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/"); resolver.setSuffix(".html"); resolver.setTemplateMode("HTML");
        templates.setTemplateResolver(resolver);
        var issuance=mock(PointCertificateIssuanceService.class);
        when(issuance.owned(7L)).thenReturn(Certificate.builder().certificateNumber("SYNTHETIC-7")
                .recipientNameSnapshot("Synthetic Recipient").verifiedIdentifierSnapshot("000001")
                .eventTitleSnapshot("Synthetic Workshop").eventDateSnapshot(LocalDateTime.of(2026,10,19,10,0)).build());
        byte[] pdf=new CertificateService(templates,issuance).generateCertificatePdf(Registration.builder().id(7L).build());
        try(var document=PDDocument.load(pdf)) {
            assertEquals(1,document.getNumberOfPages());
            String text=new PDFTextStripper().getText(document);
            assertTrue(text.contains("Synthetic Recipient")); assertTrue(text.contains("000001"));
            assertTrue(text.contains("Synthetic Workshop")); assertTrue(text.contains("does not certify attendance duration"));
        }
        verify(issuance).owned(7L);
    }
}

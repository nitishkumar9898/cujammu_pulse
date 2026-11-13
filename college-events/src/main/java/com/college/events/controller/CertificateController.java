package com.college.events.controller;

import com.college.events.model.Registration;
import com.college.events.model.RegistrationStatus;
import com.college.events.model.User;
import com.college.events.service.CertificateService;
import com.college.events.service.RegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/student/certificates")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class CertificateController {

    private final RegistrationService registrationService;
    private final CertificateService certificateService;

    @GetMapping("/{registrationId}/download")
    public ResponseEntity<byte[]> downloadCertificate(@PathVariable Long registrationId,
                                                      @AuthenticationPrincipal User user) {
        Registration reg = registrationService.findById(registrationId)
            .orElseThrow(() -> new RuntimeException("Registration not found"));

        // Security check
        if (!reg.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).build();
        }

        byte[] pdfBytes = certificateService.generateCertificatePdf(reg);
        String filename = "point-attendance-certificate.pdf";

        return ResponseEntity.ok()
            .cacheControl(org.springframework.http.CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdfBytes);
    }
}

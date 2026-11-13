package com.college.events.controller;
import com.college.events.dto.CertificateDtos;
import com.college.events.service.PointCertificateIssuanceService;
import com.college.events.service.CertificateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/cujammu") @RequiredArgsConstructor
public class CertificateApiController {
    private final PointCertificateIssuanceService issuance;
    private final CertificateService rendering;
    @GetMapping("/certificates/own")
    public ResponseEntity<CertificateDtos.Own> own(@RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(issuance.own(page));
    }
    @PostMapping("/events/{eventId}/certificates/finalize")
    public ResponseEntity<CertificateDtos.Finalization> finalizeEvent(@PathVariable long eventId, @Valid @RequestBody CertificateDtos.Finalize command) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(issuance.finalizeEvent(eventId, command));
    }
    @GetMapping("/certificates/{registrationId}/download")
    public ResponseEntity<byte[]> download(@PathVariable long registrationId) {
        var certificate = issuance.owned(registrationId);
        byte[] pdf = rendering.generateCertificatePdf(certificate.getRegistration());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"point-attendance-certificate.pdf\"").body(pdf);
    }
}

package com.college.events.controller;
import com.college.events.dto.CertificateDtos;
import com.college.events.service.PointCertificateIssuanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor
public class CertificateVerificationController {
    private final PointCertificateIssuanceService issuance;
    @GetMapping("/certificates/verify/{opaqueNumber}")
    public ResponseEntity<CertificateDtos.Verification> verify(@PathVariable String opaqueNumber) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(issuance.verify(opaqueNumber));
    }
}

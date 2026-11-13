package com.college.events.controller;

import com.college.events.dto.FeatureControlAuditDtos;
import com.college.events.model.Role;
import com.college.events.security.DomainAccess;
import com.college.events.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cujammu/admin/feature-controls/audit")
@RequiredArgsConstructor
public class FeatureControlAuditApiController {
    private final DomainAccess access;
    private final AuditService audit;

    @GetMapping
    public ResponseEntity<FeatureControlAuditDtos.PageView> history(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        access.requireRole(Role.ADMIN);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(audit.featureControlHistory(page, size));
    }
}

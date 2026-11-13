package com.college.events.controller;

import com.college.events.dto.FeatureControlDtos;
import com.college.events.model.FeatureFlag;
import com.college.events.service.FeatureFlagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Shared control-plane API. Authority is also checked inside the service. */
@RestController
@RequestMapping("/api/v1/cujammu/admin/feature-controls")
@RequiredArgsConstructor
public class FeatureControlApiController {
    private final FeatureFlagService controls;

    @GetMapping
    public ResponseEntity<FeatureControlDtos.Catalogue> catalogue() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(controls.catalogue());
    }

    @PutMapping("/{key}")
    public ResponseEntity<FeatureControlDtos.Item> change(@PathVariable String key,
                                                           @Valid @RequestBody FeatureControlDtos.Change command) {
        FeatureFlag saved = controls.setFeatureEnabled(key, command.enabled(), command.expectedRevision(), command.reason());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(controls.view(saved));
    }
}

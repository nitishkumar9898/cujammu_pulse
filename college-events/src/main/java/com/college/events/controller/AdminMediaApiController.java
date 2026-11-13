package com.college.events.controller;

import com.college.events.dto.MediaStorageDtos;
import com.college.events.model.Role;
import com.college.events.security.DomainAccess;
import com.college.events.service.MediaStorageInventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cujammu/admin/media")
@RequiredArgsConstructor
public class AdminMediaApiController {
    private final DomainAccess access;
    private final MediaStorageInventoryService inventory;

    @GetMapping("/storage")
    public ResponseEntity<MediaStorageDtos.Inventory> storage() {
        access.requireRole(Role.ADMIN);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(inventory.measure());
    }
}

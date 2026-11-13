package com.college.events.controller;

import com.college.events.dto.StudentIdentifierDtos.*;
import com.college.events.service.StudentIdentifierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Administrative verification endpoint; only the opaque student id and verified value are returned. */
@RestController
@RequestMapping("/api/v1/admin/students")
@RequiredArgsConstructor
public class StudentIdentifierApiController {
    private final StudentIdentifierService identifiers;
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public org.springframework.http.ResponseEntity<java.util.Map<String,String>> identityConflict() {
        return org.springframework.http.ResponseEntity.status(409).cacheControl(org.springframework.http.CacheControl.noStore())
                .body(java.util.Map.of("error", "IDENTITY_CONFLICT"));
    }
    @PostMapping("/lookup")
    public org.springframework.http.ResponseEntity<Candidate> lookup(@Valid @RequestBody Lookup command) {
        return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(identifiers.lookup(command));
    }
    @PutMapping("/{studentId}/verified-identifier")
    public org.springframework.http.ResponseEntity<Result> verify(@PathVariable long studentId, @Valid @RequestBody Verify command) {
        return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(identifiers.verify(studentId, command));
    }
}

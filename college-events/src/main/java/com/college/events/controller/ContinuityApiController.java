package com.college.events.controller;

import com.college.events.service.LibraryContinuityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** Protected recovery namespace stays available while ordinary library entry points are OFF. */
@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/cujammu/recovery/library")
public class ContinuityApiController {
    private final LibraryContinuityService library;
    @GetMapping("/active") public ResponseEntity<?> active() {return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(library.active());}
    @PostMapping("/{id}/safe-exit") public ResponseEntity<?> close(@PathVariable long id,@Valid @RequestBody LibraryContinuityService.Close command) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(library.close(id,command));
    }
}

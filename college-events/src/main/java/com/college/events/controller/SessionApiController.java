package com.college.events.controller;

import com.college.events.security.DomainAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/session")
@RequiredArgsConstructor
public class SessionApiController {
    private final DomainAccess access;

    @GetMapping
    public ResponseEntity<SessionView> session() {
        var user = access.currentUser();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new SessionView(user.getId(), user.getName(), user.getRole().name()));
    }

    @GetMapping("/csrf")
    public ResponseEntity<Map<String, String>> csrf(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("token", token.getToken(), "headerName", token.getHeaderName()));
    }

    public record SessionView(Long id, String name, String role) {}
}

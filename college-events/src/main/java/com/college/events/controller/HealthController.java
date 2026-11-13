package com.college.events.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HealthController {

    private final DataSource dataSource;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(Map.of("status", "UP"));
    }

    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> readiness() {
        boolean ready;
        try (Connection conn = dataSource.getConnection()) {
            ready = conn.isValid(2);
        } catch (Exception e) {
            ready = false;
        }
        return ResponseEntity.status(ready ? 200 : 503).cacheControl(org.springframework.http.CacheControl.noStore())
                .body(Map.of("status", ready ? "UP" : "DOWN"));
    }
}

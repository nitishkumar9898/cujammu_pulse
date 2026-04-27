package com.college.events.controller;

import com.college.events.service.EmailRetryService;
import com.college.events.service.GeminiAIService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HealthController {

    private final DataSource dataSource;
    private final EmailRetryService emailRetryService;
    private final GeminiAIService geminiAIService;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now().toString());
        health.put("application", "CUJAMMU EVENTHUB");
        health.put("version", "1.0.0");

        // Database
        try (Connection conn = dataSource.getConnection()) {
            health.put("database", conn.isValid(2) ? "UP" : "DOWN");
        } catch (Exception e) {
            health.put("database", "DOWN");
            health.put("database_error", e.getMessage());
        }

        // Email
        boolean emailConfigured = mailUsername != null && !mailUsername.isBlank()
            && !mailUsername.contains("your_");
        health.put("email", emailConfigured ? "CONFIGURED" : "NOT_CONFIGURED");
        health.put("email_retry_queue", emailRetryService.getQueueSize());

        // AI Chatbot
        health.put("chatbot_ai", geminiAIService.isAvailable() ? "AVAILABLE" : "FALLBACK_ONLY");

        return ResponseEntity.ok(health);
    }
}

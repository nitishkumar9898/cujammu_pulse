package com.college.events.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

@RestControllerAdvice(annotations = org.springframework.web.bind.annotation.RestController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiFailureHandler {
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> statusFailure(org.springframework.web.server.ResponseStatusException failure) {
        // Preserve intentional HTTP failures instead of rendering a generic 500 HTML view.
        // Exception reasons may contain resource details, so keep the public body fixed.
        return ResponseEntity.status(failure.getStatusCode()).cacheControl(org.springframework.http.CacheControl.noStore())
                .body(Map.of("error", "REQUEST_UNAVAILABLE"));
    }
    @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            jakarta.validation.ConstraintViolationException.class,
            org.springframework.web.method.annotation.HandlerMethodValidationException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, String>> invalidPayload() {
        // Do not log rejected values or expose parser/database internals.
        return ResponseEntity.badRequest().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(Map.of("error", "INVALID_REQUEST"));
    }
    @ExceptionHandler(com.college.events.service.ChatRateLimitException.class)
    public ResponseEntity<Map<String, Object>> chatRateLimit() {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "60").cacheControl(org.springframework.http.CacheControl.noStore())
                .body(Map.of("error", "CHAT_RATE_LIMITED", "retryAfterSeconds", 60));
    }
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> denied() {
        return ResponseEntity.status(403).cacheControl(org.springframework.http.CacheControl.noStore()).body(Map.of("error", "ACCESS_DENIED"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalidRequest() {
        return ResponseEntity.badRequest().cacheControl(org.springframework.http.CacheControl.noStore()).body(Map.of("error", "INVALID_REQUEST"));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> conflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT).cacheControl(org.springframework.http.CacheControl.noStore()).body(Map.of("error", "STATE_CONFLICT"));
    }

    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> staleRevision() {
        return ResponseEntity.status(HttpStatus.CONFLICT).cacheControl(org.springframework.http.CacheControl.noStore())
                .body(Map.of("error", "STALE_REVISION"));
    }
}

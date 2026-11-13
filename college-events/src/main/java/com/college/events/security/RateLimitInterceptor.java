package com.college.events.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.web.servlet.HandlerMapping;

@Component
@SuppressWarnings("null")
public class RateLimitInterceptor implements HandlerInterceptor {

    private final Cache<String, AtomicInteger> counters = Caffeine.newBuilder()
            .maximumSize(20000).expireAfterWrite(Duration.ofMinutes(1)).build();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String actor = request.getUserPrincipal() == null ? "peer:" + request.getRemoteAddr()
                : "account:" + request.getUserPrincipal().getName();
        Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String key = actor + ":" + (route == null ? "api" : route.toString());
        int limit = request.getRequestURI() != null && request.getRequestURI().endsWith("/attendance/manual-entry") ? 20 : 120;
        if (counters.get(key, ignored -> new AtomicInteger()).incrementAndGet() <= limit) return true;
        response.setStatus(429);
        response.setHeader("Retry-After", "60");
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"RATE_LIMIT_EXCEEDED\"}");
        return false;
    }
}

package com.college.events.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple in-memory rate limiting filter for login and forgot-password endpoints.
 * Limits to 10 requests per minute per IP address.
 */
@Component
@Order(1)
@Slf4j
@SuppressWarnings("null")
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final int MAX_REQUESTS = 5;
    private static final long WINDOW_MS = 60_000; // 1 minute

    private final Map<String, RateBucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        String uri = request.getRequestURI();
        boolean isRateLimited = (uri.equals("/login") || uri.equals("/forgot-password"))
                                && "POST".equalsIgnoreCase(request.getMethod());

        if (!isRateLimited) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(request);
        String key = clientIp + ":" + uri;
        RateBucket bucket = buckets.computeIfAbsent(key, k -> new RateBucket());

        if (bucket.isAllowed()) {
            filterChain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded for {} on {}", clientIp, uri);
            response.setStatus(429);
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().write(
                "<html><body style='text-align:center;padding:60px;font-family:sans-serif'>"
                + "<h2 style='color:#dc3545'>⚠️ Too Many Requests</h2>"
                + "<p>You have exceeded the rate limit. Please try again in 1 minute.</p>"
                + "<a href='/' style='color:#6C63FF'>Back to Home</a></body></html>"
            );
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static class RateBucket {
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile long windowStart = System.currentTimeMillis();

        boolean isAllowed() {
            long now = System.currentTimeMillis();
            if (now - windowStart > WINDOW_MS) {
                // Reset window
                synchronized (this) {
                    if (now - windowStart > WINDOW_MS) {
                        count.set(0);
                        windowStart = now;
                    }
                }
            }
            return count.incrementAndGet() <= MAX_REQUESTS;
        }
    }
}

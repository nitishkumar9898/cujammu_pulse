package com.college.events.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded, per-instance defense before Spring's login filter. Edge/distributed limits are still required. */
public final class AuthenticationRateLimitFilter extends OncePerRequestFilter {
    private static final Set<String> PATHS = Set.of("/login", "/register", "/register/verify-otp",
            "/register/resend-otp", "/forgot-password", "/reset-password");
    private final Cache<String, AtomicInteger> attempts = Caffeine.newBuilder()
            .maximumSize(20000).expireAfterWrite(Duration.ofMinutes(1)).build();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if ("POST".equals(request.getMethod()) && PATHS.contains(path)) {
            // Never trust arbitrary X-Forwarded-For headers as the peer identity.
            boolean allowed = increment("ip:" + request.getRemoteAddr()) <= 60;
            String account = request.getParameter("username");
            if (account == null) account = request.getParameter("email");
            if (account != null && account.length() > 254) {
                response.sendError(400, "Invalid request"); return;
            }
            if (account != null && !account.isBlank()) {
                allowed &= increment("account:" + digest(account.trim().toLowerCase(Locale.ROOT))) <= 10;
            }
            if (!allowed) {
                response.setStatus(429);
                response.setHeader("Retry-After", "60");
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"TOO_MANY_ATTEMPTS\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private int increment(String key) { return attempts.get(key, ignored -> new AtomicInteger()).incrementAndGet(); }
    private String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}

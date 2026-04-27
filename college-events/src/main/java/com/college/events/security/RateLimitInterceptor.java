package com.college.events.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@SuppressWarnings("null")
public class RateLimitInterceptor implements HandlerInterceptor {

    private final Map<String, UserRate> cache = new ConcurrentHashMap<>();
    // Limits
    private static final int MAX_AUTH_REQUESTS = 5; // Strict limit for auth
    private static final int MAX_API_REQUESTS = 60; // Standard limit
    private static final long TIME_WINDOW_MS = 60 * 1000; 

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String clientIp = request.getRemoteAddr();
        String path = request.getRequestURI();
        if (clientIp == null) {
            clientIp = "unknown";
        }
        
        // Use a composite key to rate limit per path per IP
        String cacheKey = clientIp + "_" + path;
        
        long currentTime = System.currentTimeMillis();

        cache.compute(cacheKey, (key, rate) -> {
            if (rate == null || (currentTime - rate.startTime) > TIME_WINDOW_MS) {
                return new UserRate(currentTime, 1);
            }
            rate.requestCount++;
            return rate;
        });

        UserRate rate = cache.get(cacheKey);
        
        // Determine limit based on path
        int limit = isAuthPath(path) ? MAX_AUTH_REQUESTS : MAX_API_REQUESTS;

        if (rate.requestCount > limit) {
            response.setStatus(429); // HTTP 429 Too Many Requests
            response.getWriter().write("429 Too Many Requests - Rate Limit Exceeded. Please try again later.");
            return false;
        }

        return true;
    }

    private boolean isAuthPath(String path) {
        return path.contains("/login") || path.contains("/register") || path.contains("/forgot-password") || path.contains("/otp");
    }

    private static class UserRate {
        long startTime;
        int requestCount;

        UserRate(long startTime, int requestCount) {
            this.startTime = startTime;
            this.requestCount = requestCount;
        }
    }
}

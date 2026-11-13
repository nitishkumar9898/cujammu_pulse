package com.college.events.service;

import com.college.events.security.AuthenticationRateLimitFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthenticationRateLimitTest {
    @Test void differentIpsCannotBypassAccountThrottleByChangingCaseOrForwardedHeader() throws Exception {
        var filter = new AuthenticationRateLimitFilter();
        for (int i = 0; i < 11; i++) {
            var request = new MockHttpServletRequest("POST", "/login");
            request.setRemoteAddr("192.0.2." + i);
            request.addHeader("X-Forwarded-For", "198.51.100." + i);
            request.setParameter("username", i % 2 == 0 ? " USER@example.invalid " : "user@example.invalid");
            var response = new MockHttpServletResponse();
            var chain = mock(FilterChain.class);
            filter.doFilter(request, response, chain);
            if (i < 10) verify(chain).doFilter(request, response);
            else { assertEquals(429, response.getStatus()); verifyNoInteractions(chain); }
        }
    }
    @Test void viewingLoginDoesNotConsumeAttemptQuota() throws Exception {
        var filter = new AuthenticationRateLimitFilter();
        for (int i = 0; i < 70; i++) {
            var request = new MockHttpServletRequest("GET", "/login");
            var response = new MockHttpServletResponse();
            var chain = mock(FilterChain.class);
            filter.doFilter(request, response, chain);
            verify(chain).doFilter(request, response);
        }
    }
}

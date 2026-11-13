package com.college.events.config;

import com.college.events.security.BrowserSecurityHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class SecurityHeaderRegressionTest {
    @Test void universalClientBlocksInlineScriptsEvalPluginsAndFraming() {
        for (String path : new String[]{"/app", "/app/", "/app/_expo/static/js/web/index.js"}) {
            var request = new MockHttpServletRequest("GET", path);
            var response = new MockHttpServletResponse();
            new BrowserSecurityHeaders().writeHeaders(request, response);
            String policy = response.getHeader("Content-Security-Policy");
            assertTrue(policy.contains("script-src 'self';"));
            assertFalse(policy.contains("unsafe-eval"));
            assertTrue(policy.contains("object-src 'none'"));
            assertTrue(policy.contains("base-uri 'none'"));
            assertTrue(policy.contains("frame-ancestors 'none'"));
            assertTrue(policy.contains("form-action 'self'"));
            assertEquals("no-referrer", response.getHeader("Referrer-Policy"));
        }
    }

    @Test void legacyCompatibilityNeverAllowsEvalOrCrossOriginFormSubmission() {
        var response = new MockHttpServletResponse();
        new BrowserSecurityHeaders().writeHeaders(new MockHttpServletRequest("GET", "/login"), response);
        String policy = response.getHeader("Content-Security-Policy");
        assertFalse(policy.contains("unsafe-eval"));
        assertTrue(policy.contains("form-action 'self'"));
        assertTrue(policy.contains("connect-src 'self';"));
    }

    @RestController static class FailingController {
        @GetMapping("/synthetic-failure") String fail() {
            throw new IllegalStateException("SYNTHETIC_DATABASE_PASSWORD_AND_SQL");
        }
        @GetMapping("/synthetic-invalid") String invalid() {
            throw new IllegalArgumentException("SYNTHETIC_PRIVATE_RECORD");
        }
    }

    @Test void unexpectedExceptionsDoNotExposeInternalDetailsInErrorModel() throws Exception {
        MockMvc mvc = standaloneSetup(new FailingController()).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/synthetic-failure")).andExpect(status().isInternalServerError())
                .andExpect(model().attribute("message", "The request could not be completed. Please contact support if it persists."));
        mvc.perform(get("/synthetic-invalid")).andExpect(status().isBadRequest())
                .andExpect(model().attribute("message", "The request contains invalid values."));
    }
}

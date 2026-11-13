package com.college.events.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.util.Locale;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationsTemplateTest {
    @Test
    void emptyInboxShowsUniversityBrandAndHonestEmptyState() {
        String html = render(List.of());

        assertTrue(html.contains("Central University of Jammu seal"));
        assertTrue(html.contains("You’re all caught up."));
        assertFalse(html.contains("Mark all as read"));
    }

    @Test
    void populatedInboxKeepsReadActionsAndEscapesMessageText() {
        String html = render(List.of(Map.of("id", "notice-1", "message", "Campus <update>", "read", false)));

        assertTrue(html.contains("Campus &lt;update&gt;"));
        assertTrue(html.contains("Unread"));
        assertTrue(html.contains("Mark all as read"));
        assertTrue(html.contains("method=\"post\""));
        assertTrue(html.contains("/api/notifications/notice-1/open"));
    }

    private String render(List<?> notifications) {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");

        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        var servletContext = new MockServletContext();
        var request = new MockHttpServletRequest(servletContext);
        var response = new MockHttpServletResponse();
        var application = JakartaServletWebApplication.buildApplication(servletContext);
        var context = new WebContext(application.buildExchange(request, response), Locale.ENGLISH);
        context.setVariable("notifications", notifications);
        return engine.process("notifications/index", context);
    }
}

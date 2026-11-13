package com.college.events.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import static org.junit.jupiter.api.Assertions.*;

class AlumniTemplateTest {
    @Test void memberTemplateRendersCsrfAndPrivateDefaultsWithoutAdminControls() {
        String rendered=render(false);
        assertTrue(rendered.contains("synthetic-csrf-token"));
        assertTrue(rendered.contains("name=\"directoryVisible\""));
        assertFalse(rendered.contains("id=\"reviewSection\""));
        assertFalse(rendered.contains("checked"));
        assertTrue(rendered.contains("/js/alumni.js"));
    }
    @Test void adminTemplateOffersIndependentReviewControls() {
        String rendered=render(true);
        assertTrue(rendered.contains("id=\"reviewSection\""));
        assertTrue(rendered.contains("id=\"reviewLookup\""));
        assertTrue(rendered.contains("cannot verify your own profile"));
    }
    private String render(boolean admin) {
        var resolver=new ClassLoaderTemplateResolver();resolver.setPrefix("templates/");resolver.setSuffix(".html");resolver.setTemplateMode("HTML");
        var engine=new SpringTemplateEngine();engine.setTemplateResolver(resolver);
        var context=new Context();context.setVariable("canVerify",admin);
        context.setVariable("_csrf",new DefaultCsrfToken("X-CSRF-TOKEN","_csrf","synthetic-csrf-token"));
        return engine.process("alumni/index",context);
    }
}

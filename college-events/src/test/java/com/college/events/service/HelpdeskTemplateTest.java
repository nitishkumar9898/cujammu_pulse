package com.college.events.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import static org.junit.jupiter.api.Assertions.*;

class HelpdeskTemplateTest {
    @Test void memberTemplateRendersPrivateFormsWithoutStaffQueues(){String html=render(false,false);assertTrue(html.contains("synthetic-csrf-token"));assertFalse(html.contains("data-view=\"all\""));assertFalse(html.contains("data-view=\"assigned\""));assertTrue(html.contains("Response times depend on university staffing"));}
    @Test void adminTemplateRendersStaffQueues(){String html=render(true,true);assertTrue(html.contains("data-view=\"all\""));assertTrue(html.contains("data-view=\"assigned\""));}
    private String render(boolean admin,boolean staff){var resolver=new ClassLoaderTemplateResolver();resolver.setPrefix("templates/");resolver.setSuffix(".html");resolver.setTemplateMode("HTML");var engine=new SpringTemplateEngine();engine.setTemplateResolver(resolver);var ctx=new Context();ctx.setVariable("canAdmin",admin);ctx.setVariable("canSupport",staff);ctx.setVariable("_csrf",new DefaultCsrfToken("X-CSRF-TOKEN","_csrf","synthetic-csrf-token"));return engine.process("helpdesk/index",ctx);}
}

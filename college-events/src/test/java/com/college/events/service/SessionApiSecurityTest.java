package com.college.events.service;

import com.college.events.config.SecurityConfig;
import com.college.events.controller.SessionApiController;
import com.college.events.model.*;
import com.college.events.model.Role;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(SessionApiSecurityTest.Config.class)
@WebAppConfiguration
class SessionApiSecurityTest {
    @Configuration @EnableWebMvc
    @Import({SecurityConfig.class, SessionApiController.class, DomainAccess.class})
    static class Config {
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean EventRepository events() { return mock(EventRepository.class); }
        @Bean UserService usersService() { return mock(UserService.class); }
        @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    }

    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    MockMvc mvc;
    User student;

    @BeforeEach void setup() {
        reset(users);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        student = User.builder().id(7L).name("Synthetic Student").role(Role.STUDENT)
                .email("session@example.invalid").password("private-hash").enabled(true).build();
        when(users.findById(7L)).thenReturn(Optional.of(student));
    }

    @Test void unauthenticatedApiReturnsJson401WithoutLoginRedirect() throws Exception {
        mvc.perform(get("/api/v1/session")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("AUTHENTICATION_REQUIRED"))
                .andExpect(header().doesNotExist("Location"));
    }

    @Test void securityChainEmitsBrowserDefensesOnPublicAndDeniedResponses() throws Exception {
        mvc.perform(get("/api/v1/session/csrf")).andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("frame-ancestors 'none'")));
        mvc.perform(get("/api/v1/session")).andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    @Test void csrfBootstrapIsPublicAndNeverCached() throws Exception {
        mvc.perform(get("/api/v1/session/csrf")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"));
    }

    @Test void sessionResponseExposesOnlyApprovedIdentityFields() throws Exception {
        mvc.perform(get("/api/v1/session").with(user(student))).andExpect(status().isOk())
                .andExpect(content().json("{\"id\":7,\"name\":\"Synthetic Student\",\"role\":\"STUDENT\"}", true))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test void writesNeedBothSessionAndCsrf() throws Exception {
        mvc.perform(post("/api/v1/cujammu/departments").with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/cujammu/departments").with(user(student))).andExpect(status().isForbidden());
        mvc.perform(post("/api/notifications/read-all").with(user(student)))
                .andExpect(status().isForbidden()).andExpect(forwardedUrl(null))
                .andExpect(content().json("{\"error\":\"ACCESS_DENIED\"}", true));
    }

    @Test void ordinaryAccountCannotListAllHelpdeskTicketsOrOpenDatabaseConsole() throws Exception {
        mvc.perform(get("/api/v1/cujammu/helpdesk/all").with(user(student))).andExpect(status().isForbidden());
        mvc.perform(get("/h2-console/").with(user(student))).andExpect(status().isForbidden());
    }

    @Test void accountRevocationIsEnforcedInRealSecurityChain() throws Exception {
        User disabled = User.builder().id(7L).role(Role.STUDENT).enabled(false).build();
        when(users.findById(7L)).thenReturn(Optional.of(disabled));
        mvc.perform(get("/api/v1/session").with(user(student))).andExpect(status().isUnauthorized());
    }
}

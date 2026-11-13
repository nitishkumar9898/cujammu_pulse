package com.college.events.service;

import com.college.events.config.SecurityConfig;
import com.college.events.controller.SessionApiController;
import com.college.events.model.Role;
import com.college.events.model.User;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(SessionLifecycleSecurityTest.Config.class)
@WebAppConfiguration
class SessionLifecycleSecurityTest {
    @Configuration @EnableWebMvc
    @Import({SecurityConfig.class, SessionApiController.class, DomainAccess.class})
    static class Config {
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean EventRepository events() { return mock(EventRepository.class); }
        @Bean UserService userService() { return mock(UserService.class); }
        @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    }
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired UserService service;
    @Autowired PasswordEncoder encoder;
    MockMvc mvc;
    User original;

    @BeforeEach void setup() {
        reset(users, service);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        original = User.builder().id(47L).name("Synthetic account").email("lifecycle@example.invalid")
                .password(encoder.encode("SyntheticPassword1!")).role(Role.STUDENT).enabled(true).build();
        when(service.loadUserByUsername(original.getEmail())).thenReturn(original);
        when(users.findById(47L)).thenReturn(Optional.of(original));
    }

    @Test void successfulLoginRotatesSessionAndRejectsSavedExternalRedirect() throws Exception {
        var before = new MockHttpSession();
        String fixedId = before.getId();
        before.setAttribute("synthetic", "preserved");
        var savedRequest = new MockHttpServletRequest("GET", "/external-target");
        savedRequest.setScheme("https");
        savedRequest.setServerName("attacker.invalid");
        savedRequest.setServerPort(443);
        savedRequest.setSession(before);
        new HttpSessionRequestCache().saveRequest(savedRequest, new MockHttpServletResponse());
        var result = login(before, "https://attacker.invalid/");
        var after = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(after);
        assertNotEquals(fixedId, after.getId());
        assertTrue(before.isInvalid());
        assertEquals("preserved", after.getAttribute("synthetic"));
        assertEquals("/student/dashboard", result.getResponse().getRedirectedUrl());
        mvc.perform(get("/api/v1/session").session(after)).andExpect(status().isOk());
    }

    @Test void logoutNeedsCsrfAndInvalidatesAuthenticatedSession() throws Exception {
        var session = (MockHttpSession) login(new MockHttpSession(), null).getRequest().getSession(false);
        mvc.perform(post("/logout").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/session").session(session)).andExpect(status().isOk());
        mvc.perform(post("/logout").session(session).with(csrf())).andExpect(status().is3xxRedirection())
                .andExpect(cookie().maxAge("JSESSIONID", 0));
        assertTrue(session.isInvalid());
        mvc.perform(get("/api/v1/session")).andExpect(status().isUnauthorized());
    }

    @Test void passwordRotationRevokesExistingSessionAtNextRequest() throws Exception {
        assertRevoked(User.builder().id(47L).email(original.getEmail()).password("rotated-hash")
                .role(Role.STUDENT).enabled(true).build());
    }
    @Test void changedRoleCannotReuseOriginalPrivilegeSnapshot() throws Exception {
        assertRevoked(User.builder().id(47L).email(original.getEmail()).password(original.getPassword())
                .role(Role.ADMIN).enabled(true).build());
    }
    @Test void changedEmailOrDeletedAccountRevokesSession() throws Exception {
        assertRevoked(User.builder().id(47L).email("replacement@example.invalid").password(original.getPassword())
                .role(Role.STUDENT).enabled(true).build());
        when(users.findById(47L)).thenReturn(Optional.of(original));
        var session = (MockHttpSession) login(new MockHttpSession(), null).getRequest().getSession(false);
        when(users.findById(47L)).thenReturn(Optional.empty());
        mvc.perform(get("/api/v1/session").session(session)).andExpect(status().isUnauthorized());
        assertTrue(session.isInvalid());
    }
    private void assertRevoked(User replacement) throws Exception {
        var session = (MockHttpSession) login(new MockHttpSession(), null).getRequest().getSession(false);
        when(users.findById(47L)).thenReturn(Optional.of(replacement));
        mvc.perform(get("/api/v1/session").session(session)).andExpect(status().isUnauthorized());
        assertTrue(session.isInvalid());
    }
    private MvcResult login(MockHttpSession session, String target) throws Exception {
        var request = post("/login").session(session).with(csrf())
                .param("username", original.getEmail()).param("password", "SyntheticPassword1!");
        if (target != null) request.param("continue", target).param("redirect", target).header("Referer", target);
        return mvc.perform(request).andExpect(status().is3xxRedirection()).andReturn();
    }
}

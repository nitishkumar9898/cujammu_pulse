package com.college.events.config;

import com.college.events.model.InstitutionalIdentity;
import com.college.events.model.Role;
import com.college.events.model.User;
import com.college.events.repository.InstitutionalIdentityRepository;
import com.college.events.repository.UserRepository;
import com.college.events.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(properties = {"app.identity.enabled=true", "app.identity.issuer=https://sso.example.invalid/realms/university",
        "app.identity.jwks-uri=https://sso.example.invalid/jwks", "app.identity.audience=university-api",
        "logging.file.name=target/test-logs/bearer-chain.log"})
@ContextConfiguration(classes = {SecurityConfig.class, OidcResourceServerConfig.class, InstitutionalBearerChainTest.Endpoint.class})
class InstitutionalBearerChainTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserService userService;
    @MockitoBean UserRepository users;
    @MockitoBean PasswordEncoder encoder;
    @MockitoBean InstitutionalIdentityRepository identities;
    @MockitoBean(name = "institutionalJwtDecoder") JwtDecoder decoder;
    User user;

    @BeforeEach void setup() {
        user = User.builder().id(9L).name("Synthetic Student").email("student@example.invalid")
                .password("hash").role(Role.STUDENT).enabled(true).build();
        when(users.findById(9L)).thenReturn(Optional.of(user));
        when(decoder.decode("valid")).thenReturn(InstitutionalTokenTest.jwt().build());
        when(decoder.decode("invalid")).thenThrow(new BadJwtException("Invalid synthetic bearer"));
        when(identities.findByIssuerAndSubjectAndEnabledTrue(InstitutionalTokenTest.ISSUER, "subject-123"))
                .thenReturn(Optional.of(InstitutionalIdentity.builder().issuer(InstitutionalTokenTest.ISSUER)
                        .subject("subject-123").user(user).enabled(true).build()));
    }

    @Test void bearerUsesMappedUserAndMutationDoesNotNeedCookieCsrf() throws Exception {
        mvc.perform(post("/api/test-identity").header("Authorization", "Bearer valid"))
                .andExpect(status().isOk()).andExpect(content().string("9:STUDENT"));
    }
    @Test void invalidOrMalformedBearerCannotFallBackToAuthenticatedSession() throws Exception {
        for (String header : new String[]{"Bearer invalid", "Bearer", "Bearer invalid extra", " Bearer valid"}) {
            mvc.perform(get("/api/test-identity").session(session()).header("Authorization", header))
                    .andExpect(status().isUnauthorized());
        }
    }
    @Test void disabledOrUnmappedAccountCannotAuthenticate() throws Exception {
        user.setEnabled(false);
        mvc.perform(get("/api/test-identity").header("Authorization", "Bearer valid")).andExpect(status().isUnauthorized());
        user.setEnabled(true);
        when(identities.findByIssuerAndSubjectAndEnabledTrue(InstitutionalTokenTest.ISSUER, "subject-123"))
                .thenReturn(Optional.empty());
        mvc.perform(get("/api/test-identity").header("Authorization", "Bearer valid")).andExpect(status().isUnauthorized());
    }
    @Test void cookieSessionFallbackStillRequiresCsrfWhenNoBearerIsPresented() throws Exception {
        mvc.perform(post("/api/test-identity").session(session())).andExpect(status().isForbidden());
        mvc.perform(post("/api/test-identity").session(session()).with(csrf()))
                .andExpect(status().isOk()).andExpect(content().string("9:STUDENT"));
    }
    @Test void bearerDoesNotPersistAuthenticationInSessionAndCannotBypassAdminRoute() throws Exception {
        var session = new MockHttpSession();
        mvc.perform(get("/api/test-identity").session(session).header("Authorization", "Bearer valid"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/test-identity").session(session)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/cujammu/helpdesk/all").header("Authorization", "Bearer valid"))
                .andExpect(status().isForbidden());
    }
    @Test void ambiguousMultipleAuthorizationHeadersAreRejected() throws Exception {
        mvc.perform(get("/api/test-identity").session(session()).header("Authorization", "Basic ignored", "Bearer valid"))
                .andExpect(status().isUnauthorized());
    }
    @Test void bodyAndQueryTokensNeverAuthenticateRequests() throws Exception {
        mvc.perform(get("/api/test-identity").param("access_token", "valid"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/test-identity").with(csrf()).contentType("application/x-www-form-urlencoded")
                        .content("access_token=valid"))
                .andExpect(status().isUnauthorized());
        verify(decoder, never()).decode(anyString());
    }
    private MockHttpSession session() {
        var session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities())));
        return session;
    }
    @RestController static class Endpoint {
        @RequestMapping(value = "/api/test-identity", method = {RequestMethod.GET, RequestMethod.POST})
        String identity(@AuthenticationPrincipal User user) { return user.getId() + ":" + user.getRole().name(); }
        @GetMapping("/api/v1/cujammu/helpdesk/all") String admin() { return "admin"; }
    }
}

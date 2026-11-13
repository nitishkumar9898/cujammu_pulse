package com.college.events.service;

import com.college.events.config.AccountSessionFilter;
import com.college.events.dto.UserRegistrationDto;
import com.college.events.model.Role;
import com.college.events.model.User;
import com.college.events.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import jakarta.servlet.FilterChain;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdentityBoundaryTest {
    private final UserRepository users = mock(UserRepository.class);

    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"STUDENT", "ALUMNI"}, mode = EnumSource.Mode.EXCLUDE)
    void forgedPrivilegedRegistrationCannotWriteAccount(Role role) {
        var dto = new UserRegistrationDto();
        dto.setRole(role);
        var service = new UserService(users, mock(PasswordEncoder.class), mock(FileStorageService.class));
        assertThrows(IllegalArgumentException.class, () -> service.registerUser(dto));
        verifyNoInteractions(users);
    }

    @Test void nestedUserSerializationAndLoggingExcludeSecrets() throws Exception {
        var user = account();
        user.setPasswordResetToken("secret-reset-value");
        String json = new ObjectMapper().writeValueAsString(java.util.Map.of("student", user));
        assertFalse(json.contains("password"));
        assertFalse(json.contains("secret-reset-value"));
        assertFalse(user.toString().contains("secret-reset-value"));
        assertFalse(user.toString().contains("password-hash"));
    }

    @Test void disabledAccountInvalidatesExistingSession() throws Exception {
        var current = account(); current.setEnabled(false);
        assertRevoked(current);
    }

    @Test void changedRoleInvalidatesExistingSession() throws Exception {
        var current = account(); current.setRole(Role.ADMIN);
        assertRevoked(current);
    }

    @Test void passwordResetInvalidatesExistingSession() throws Exception {
        var current = account(); current.setPassword("replacement-hash");
        assertRevoked(current);
    }

    @Test void deletedAccountInvalidatesExistingSession() throws Exception { assertRevoked(null); }

    @Test void unchangedAccountCanProceed() throws Exception {
        authenticate();
        when(users.findById(1L)).thenReturn(Optional.of(account()));
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);
        new AccountSessionFilter(users).doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }

    private void assertRevoked(User current) throws Exception {
        authenticate();
        when(users.findById(1L)).thenReturn(Optional.ofNullable(current));
        var request = new MockHttpServletRequest();
        var session = request.getSession();
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);
        new AccountSessionFilter(users).doFilter(request, response, chain);
        assertEquals(401, response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertThrows(IllegalStateException.class, () -> session.getAttribute("anything"));
        verifyNoInteractions(chain);
    }

    private void authenticate() {
        var user = account();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private User account() {
        return User.builder().id(1L).name("Synthetic Student").email("student@example.invalid")
                .role(Role.STUDENT).password("password-hash").enabled(true).build();
    }
}

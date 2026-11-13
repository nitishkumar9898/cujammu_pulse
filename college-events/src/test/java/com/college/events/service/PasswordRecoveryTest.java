package com.college.events.service;

import com.college.events.model.Role;
import com.college.events.model.User;
import com.college.events.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PasswordRecoveryTest {
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final UserService service = new UserService(users, encoder, mock(FileStorageService.class));

    @Test void issuesDigestRatherThanBearerAndUsesNormalizedEmail() {
        User account = User.builder().id(7L).role(Role.STUDENT).enabled(true).build();
        when(users.findByEmail("student@example.invalid")).thenReturn(Optional.of(account));
        when(users.issuePasswordReset(eq(7L), anyString(), any())).thenReturn(1);
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC);
        String token = service.generatePasswordResetToken(" Student@Example.invalid ");
        assertNotNull(token);
        assertEquals(43, token.length());
        verify(users).issuePasswordReset(eq(7L), eq(PasswordResetTokens.digest(token)),
                argThat(expiry -> expiry.isAfter(before.plusMinutes(29)) && expiry.isBefore(before.plusMinutes(31))));
        verify(users, never()).save(any());
    }

    @Test void disabledAccountCannotIssueOrValidateToken() {
        User account = User.builder().enabled(false).passwordResetTokenExpiry(LocalDateTime.now(ZoneOffset.UTC).plusHours(1)).build();
        when(users.findByEmail("student@example.invalid")).thenReturn(Optional.of(account));
        assertNull(service.generatePasswordResetToken("student@example.invalid"));
        verify(users, never()).issuePasswordReset(any(), any(), any());
        String token = PasswordResetTokens.generate();
        when(users.findByPasswordResetToken(PasswordResetTokens.digest(token))).thenReturn(Optional.of(account));
        assertTrue(service.validatePasswordResetToken(token).isEmpty());
    }

    @Test void legacyAndDigestStringsAreNotBearerCredentials() {
        String legacy = "12345678-1234-1234-1234-123456789abc";
        assertFalse(service.resetPasswordWithToken(legacy, "GoodPassword1!"));
        assertTrue(service.validatePasswordResetToken(legacy).isEmpty());
        assertNull(PasswordResetTokens.digest(PasswordResetTokens.digest(PasswordResetTokens.generate())));
        verifyNoInteractions(users, encoder);
    }

    @Test void passwordPolicyPreventsWeakOrBcryptTruncatedPasswordWrites() {
        for (String invalid : new String[]{"short", "alllowercase123!", "Password!", "Password1", "Aa1!" + "x".repeat(69), "Aa1!" + "é".repeat(35)}) {
            assertThrows(IllegalArgumentException.class, () -> service.resetPasswordWithToken(PasswordResetTokens.generate(), invalid));
        }
        verifyNoInteractions(users, encoder);
        assertDoesNotThrow(() -> PasswordPolicy.validate("Aa1!" + "x".repeat(68)));
    }

    @Test void passwordChangeRevokesOutstandingRecoveryCredential() {
        User account = User.builder().id(7L).password("old-hash").build();
        when(encoder.encode("GoodPassword1!")).thenReturn("hash");
        when(users.replacePasswordAndRevokeReset(7L, "old-hash", "hash")).thenReturn(1);
        service.changePassword(account, "GoodPassword1!");
        verify(users).replacePasswordAndRevokeReset(7L, "old-hash", "hash");
        verify(users, never()).save(any());
    }
}

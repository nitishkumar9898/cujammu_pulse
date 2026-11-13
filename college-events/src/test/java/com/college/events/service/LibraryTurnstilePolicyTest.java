package com.college.events.service;

import com.college.events.model.LibraryGateLog;
import com.college.events.model.Role;
import com.college.events.model.User;
import com.college.events.repository.LibraryGateLogRepository;
import com.college.events.security.DomainAccess;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


class LibraryTurnstilePolicyTest {
    LibraryGateLogRepository logs = mock(LibraryGateLogRepository.class);
    DomainAccess access = mock(DomainAccess.class);
    FeatureFlagService flags = mock(FeatureFlagService.class);
    LibraryTurnstileService service = new LibraryTurnstileService(logs, access, flags);

    @Test
    void unsupportedPurposeIsRejectedBeforeAVisitIsWritten() {
        User user = user();
        when(access.requireSelf(1L)).thenReturn(user);

        assertThrows(IllegalArgumentException.class, () -> service.checkIn(user, "EXIT", null, null));
        verify(logs, never()).save(any());
    }

    @Test
    void duplicateEntryReturnsTheLockedActiveVisit() {
        User user = user();
        LibraryGateLog active = visit(user, "STUDY", LocalDate.now());
        when(access.requireSelf(1L)).thenReturn(user);
        when(logs.findActiveForUpdate(eq(user), eq("ACTIVE"), any())).thenReturn(List.of(active));

        assertSame(active, service.checkIn(user, "BOOK_ISSUE_RETURN", null, null));
        verify(logs, never()).save(any());
    }

    @Test
    void entryRequiresVerifiedIdentifierAndNeverFallsBackToEmail() {
        User unverified = User.builder().id(1L).name("Synthetic student")
                .email("library@example.invalid").role(Role.STUDENT).enrollmentNo("typed-0001").build();
        when(access.requireSelf(1L)).thenReturn(unverified);
        when(logs.findActiveForUpdate(eq(unverified), eq("ACTIVE"), any())).thenReturn(List.of());

        assertThrows(IllegalStateException.class, () -> service.checkIn(unverified, "STUDY", null, null));
        verify(logs, never()).save(any());

        User verified = User.builder().id(1L).name("Synthetic student")
                .email("library@example.invalid").role(Role.STUDENT).enrollmentNo("typed-0001")
                .verifiedEnrollmentNo("0001").build();
        when(access.requireSelf(1L)).thenReturn(verified);
        when(logs.findActiveForUpdate(eq(verified), eq("ACTIVE"), any())).thenReturn(List.of());
        when(logs.save(any(LibraryGateLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LibraryGateLog created = service.checkIn(verified, "STUDY", null, null);

        assertEquals("0001", created.getRollNumberOrEmpId());
        assertNotEquals(verified.getEmail(), created.getRollNumberOrEmpId());
    }

    @Test
    void priorDayActiveVisitIsNotAutoClosedAtTheReportBoundary() {
        User user = user();
        LibraryGateLog active = visit(user, "STUDY", LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).minusDays(1));
        when(access.requireSelf(1L)).thenReturn(user);
        when(logs.findActiveForUpdate(eq(user), eq("ACTIVE"), any())).thenReturn(List.of(active));

        assertThrows(IllegalStateException.class, () -> service.checkIn(user, "STUDY", null, null));
        assertEquals("ACTIVE", active.getStatus());
        assertNull(active.getExitTime());
        assertNull(active.getDurationSeconds());
        verify(logs, never()).save(any());
    }

    @Test
    void mismatchedPurposeRecordsEvidenceAndLeavesVisitActive() {
        User user = user();
        LibraryGateLog active = visit(user, "STUDY", LocalDate.now());
        when(access.requireSelf(1L)).thenReturn(user);
        when(logs.findActiveForUpdate(eq(user), eq("ACTIVE"), any())).thenReturn(List.of(active));

        assertThrows(IllegalArgumentException.class, () -> service.checkOut(user, "BOOK_ISSUE_RETURN"));
        verify(logs).recordActivePurposeMismatch(active.getId());
        assertEquals("ACTIVE", active.getStatus());
        verify(logs, never()).save(any());
    }

    @Test
    void checkoutUsesPersistedEntryTimeAndCompletesTheVisit() {
        User user = user();
        LibraryGateLog active = visit(user, "STUDY", LocalDate.now());
        active.setEntryTime(LocalDateTime.now().minusMinutes(2));
        when(access.requireSelf(1L)).thenReturn(user);
        when(logs.findActiveForUpdate(eq(user), eq("ACTIVE"), any())).thenReturn(List.of(active));
        when(logs.save(active)).thenReturn(active);

        LibraryGateLog result = service.checkOut(user, "STUDY");

        assertSame(active, result);
        assertEquals("COMPLETED", result.getStatus());
        assertNotNull(result.getExitTime());
        assertTrue(result.getDurationSeconds() >= 1);
        assertTrue(result.getDurationSeconds() <= Duration.ofMinutes(3).getSeconds());
    }

    @Test
    void disabledModuleStopsCommandsBeforeRepositoryAccess() {
        User user = user();
        doThrow(new IllegalStateException("Required feature is disabled"))
                .when(flags).requireEnabledForUpdate("MODULE_LIBRARY_TURNSTILE");

        assertThrows(IllegalStateException.class, () -> service.checkIn(user, "STUDY", null, null));
        verifyNoInteractions(logs, access);
    }

    private User user() {
        return User.builder().id(1L).name("Synthetic student").email("library@example.invalid")
                .role(Role.STUDENT).enrollmentNo("0001").verifiedEnrollmentNo("0001").academicLevel("UG").build();
    }

    private LibraryGateLog visit(User user, String purpose, LocalDate date) {
        return LibraryGateLog.builder().id(9L).user(user).sessionType(purpose)
                .entryTime(date.atTime(10, 0)).logDate(date).status("ACTIVE")
                .rollNumberOrEmpId("0001").build();
    }
}

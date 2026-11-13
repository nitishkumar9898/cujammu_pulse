package com.college.events.service;

import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventAttendanceExportAuthorizationTest {
    private final EventOperationsRepository operations = mock(EventOperationsRepository.class);
    private final EventOperatorGrantRepository grants = mock(EventOperatorGrantRepository.class);
    private final DomainAccess access = mock(DomainAccess.class);
    private final AuditService audit = mock(AuditService.class);
    private final Event event = Event.builder().id(42L).status(EventStatus.COMPLETED).build();
    private final EventOperations aggregate = EventOperations.builder().id(9L).event(event).scannerEpoch(7L).manualEpoch(4L).build();
    private final EventOperationsService service = new EventOperationsService(mock(EventRepository.class), operations,
            mock(VolunteerMembershipRepository.class), grants, mock(RegistrationRepository.class),
            mock(ManualAttendanceEvidenceRepository.class), mock(UserRepository.class), access, audit,
            mock(NotificationService.class), mock(FeatureFlagService.class));

    @Test void ownerCanExportWithoutBorrowingVolunteerCapabilities() {
        User owner = user(5L, Role.FACULTY);
        when(access.currentUser()).thenReturn(owner);
        when(access.isEventOwner(owner, event)).thenReturn(true);
        when(operations.findForUpdate(42L)).thenReturn(Optional.of(aggregate));

        assertDoesNotThrow(() -> service.requireAttendanceExportCapability(42L, "CSV"));
        verify(grants, never()).findForUpdate(anyLong(), anyLong(), anyString());
    }

    @Test void activeEventScopedExportGrantAuthorizesFacultyButQrOrManualGrantDoesNot() {
        User delegate = user(8L, Role.FACULTY);
        when(access.currentUser()).thenReturn(delegate);
        when(access.isEventOwner(delegate, event)).thenReturn(false);
        when(operations.findForUpdate(42L)).thenReturn(Optional.of(aggregate));
        EventOperatorGrant grant = EventOperatorGrant.builder().event(event).user(delegate).grantType("EXPORT")
                .actions("CSV,XLSX,SCAN_LOG").enabled(true).expiresAt(LocalDateTime.now().plusMinutes(10)).build();
        when(grants.findForUpdate(42L, 8L, "EXPORT")).thenReturn(Optional.of(grant));

        assertDoesNotThrow(() -> service.requireAttendanceExportCapability(42L, "SCAN_LOG"));

        when(grants.findForUpdate(42L, 8L, "EXPORT")).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class, () -> service.requireAttendanceExportCapability(42L, "CSV"));
        verify(grants, never()).findForUpdate(42L, 8L, "QR");
        verify(grants, never()).findForUpdate(42L, 8L, "MANUAL");
    }

    @Test void expiredGrantAndStudentRoleAreDenied() {
        User delegate = user(8L, Role.ORGANIZER);
        when(access.currentUser()).thenReturn(delegate);
        when(access.isEventOwner(delegate, event)).thenReturn(false);
        when(operations.findForUpdate(42L)).thenReturn(Optional.of(aggregate));
        when(grants.findForUpdate(42L, 8L, "EXPORT")).thenReturn(Optional.of(EventOperatorGrant.builder()
                .event(event).user(delegate).grantType("EXPORT").actions("SCAN_LOG").enabled(true)
                .expiresAt(LocalDateTime.now().minusSeconds(1)).build()));
        assertThrows(AccessDeniedException.class, () -> service.requireAttendanceExportCapability(42L, "SCAN_LOG"));

        User student = user(11L, Role.STUDENT);
        when(access.currentUser()).thenReturn(student);
        assertThrows(AccessDeniedException.class, () -> service.requireAttendanceExportCapability(42L, "CSV"));
        verify(grants, never()).findForUpdate(42L, 11L, "EXPORT");
    }

    @Test void eachExportFormatRequiresItsOwnExplicitGrantAction() {
        User delegate = user(8L, Role.FACULTY);
        when(access.currentUser()).thenReturn(delegate);
        when(access.isEventOwner(delegate, event)).thenReturn(false);
        when(operations.findForUpdate(42L)).thenReturn(Optional.of(aggregate));
        when(grants.findForUpdate(42L, 8L, "EXPORT")).thenReturn(Optional.of(EventOperatorGrant.builder()
                .event(event).user(delegate).grantType("EXPORT").actions("SCAN_LOG").enabled(true)
                .expiresAt(LocalDateTime.now().plusMinutes(10)).build()));

        assertDoesNotThrow(() -> service.requireAttendanceExportCapability(42L, "SCAN_LOG"));
        assertThrows(AccessDeniedException.class, () -> service.requireAttendanceExportCapability(42L, "CSV"));
    }

    @Test void completedEventExportGrantCanBeRevokedUnderTheSameEventLock() {
        User owner = user(5L, Role.FACULTY);
        User delegate = user(8L, Role.ORGANIZER);
        when(access.requireEventManager(42L)).thenReturn(event);
        when(access.currentUser()).thenReturn(owner);
        when(operations.findForUpdate(42L)).thenReturn(Optional.of(aggregate));
        EventOperatorGrant grant = EventOperatorGrant.builder()
                .event(event).user(delegate).grantType("EXPORT").actions("CSV,XLSX,SCAN_LOG").enabled(true).epoch(2).build();
        when(grants.findForUpdate(42L, 8L, "EXPORT")).thenReturn(Optional.of(grant));

        assertDoesNotThrow(() -> service.revoke(42L, 8L, "EXPORT", 0));

        assertFalse(grant.isEnabled());
        assertEquals(3, grant.getEpoch());
        assertEquals(7L, aggregate.getScannerEpoch(), "revoking an export grant must not invalidate QR scanners");
        assertEquals(4L, aggregate.getManualEpoch());
        verify(operations).flush();
        verify(audit).logAction(eq(owner.getEmail()), eq(owner.getRole().name()), eq("EXPORT_GRANT_REVOKED"), contains("userId=8"));
    }

    private static User user(long id, Role role) {
        return User.builder().id(id).role(role).enabled(true).build();
    }
}

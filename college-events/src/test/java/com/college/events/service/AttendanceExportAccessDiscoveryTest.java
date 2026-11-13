package com.college.events.service;

import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AttendanceExportAccessDiscoveryTest {
    private final EventOperationsRepository operations = mock(EventOperationsRepository.class);
    private final EventOperatorGrantRepository grants = mock(EventOperatorGrantRepository.class);
    private final FeatureFlagService flags = mock(FeatureFlagService.class);
    private final DomainAccess access = mock(DomainAccess.class);
    private final EventOperationsService service = new EventOperationsService(mock(EventRepository.class), operations,
            mock(VolunteerMembershipRepository.class), grants, mock(RegistrationRepository.class),
            mock(ManualAttendanceEvidenceRepository.class), mock(UserRepository.class), access, mock(AuditService.class),
            mock(NotificationService.class), flags);

    @Test
    void delegatedFacultySeesOnlyActiveExportAssignedEventsAndNoManagementAuthorityIsAdded() {
        User faculty = User.builder().id(17L).role(Role.FACULTY).enabled(true).build();
        Event approved = event(1L, EventStatus.APPROVED);
        Event completed = event(2L, EventStatus.COMPLETED);
        EventOperatorGrant first = grant(faculty, approved, "CSV,XLSX,SCAN_LOG", LocalDateTime.now().plusMinutes(20));
        EventOperatorGrant expired = grant(faculty, completed, "CSV", LocalDateTime.now().minusSeconds(1));
        when(access.currentUser()).thenReturn(faculty);
        when(grants.findEnabledExportGrantsForUser(eq(17L), any(), any())).thenReturn(new PageImpl<>(List.of(first, expired), PageRequest.of(0, 25), 26));
        when(operations.findByEventId(1L)).thenReturn(Optional.of(EventOperations.builder().revision(4L).build()));

        var events = service.myAttendanceExportEvents(0);

        assertEquals(1, events.items().size());
        assertEquals(1L, events.items().get(0).id());
        assertEquals(4L, events.items().get(0).operationsRevision());
        assertEquals(0, events.page());
        assertTrue(events.hasNext());
        verify(flags).requireEnabledForRead("MODULE_EVENTS");
        verify(operations, never()).findForUpdate(anyLong());
    }

    @Test
    void studentsCannotEnumerateExportGrants() {
        when(access.currentUser()).thenReturn(User.builder().id(18L).role(Role.STUDENT).enabled(true).build());

        assertThrows(AccessDeniedException.class, () -> service.myAttendanceExportEvents(0));
        verify(grants, never()).findEnabledExportGrantsForUser(anyLong(), any(), any());
    }

    private static Event event(long id, EventStatus status) {
        return Event.builder().id(id).title("Synthetic " + id).status(status).eventDate(LocalDateTime.now().plusDays(id)).build();
    }

    private static EventOperatorGrant grant(User user, Event event, String actions, LocalDateTime expiresAt) {
        return EventOperatorGrant.builder().event(event).user(user).grantType("EXPORT").actions(actions)
                .enabled(true).expiresAt(expiresAt).build();
    }
}

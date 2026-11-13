package com.college.events.service;

import com.college.events.dto.ReadinessTaskDtos;
import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReadinessTaskServiceTest {
    private final ReadinessTaskRepository tasks = mock(ReadinessTaskRepository.class);
    private final EventRepository events = mock(EventRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final DomainAccess access = mock(DomainAccess.class);
    private final FeatureFlagService flags = mock(FeatureFlagService.class);
    private final AuditService audit = mock(AuditService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final ReadinessTaskService service = new ReadinessTaskService(tasks, events, users, access, flags, audit, notifications);
    private final User organizer = User.builder().id(3L).name("Organizer").email("organizer@example.test").password("x").role(Role.FACULTY).enabled(true).build();
    private final Event event = Event.builder().id(7L).organizer(organizer).status(EventStatus.APPROVED).build();
    private final Instant dueAt = Instant.parse("2030-01-02T04:30:00Z");

    @Test
    void createPersistsEventScopedTaskAndAudit() {
        when(access.requireEventManager(7L)).thenReturn(event);
        when(events.findForApproval(7L)).thenReturn(Optional.of(event));
        when(access.currentUser()).thenReturn(organizer);
        when(users.findForUpdate(3L)).thenReturn(Optional.of(organizer));
        when(tasks.findByEventIdAndRequestKey(7L, "00000000-0000-4000-8000-000000000007")).thenReturn(Optional.empty());
        when(tasks.saveAndFlush(any())).thenAnswer(call -> {
            ReadinessTask row = call.getArgument(0);
            row.setId(15L); row.setCreatedAt(Instant.parse("2030-01-01T00:00:00Z")); row.setUpdatedAt(row.getCreatedAt());
            return row;
        });

        var result = service.create(7L, create("00000000-0000-4000-8000-000000000007"));

        assertEquals(7L, result.eventId());
        assertEquals(3L, result.ownerUserId());
        assertEquals("OPEN", result.status());
        verify(audit).logAction("organizer@example.test", "FACULTY", "READINESS_TASK_CREATED",
                "eventId=7;taskId=15;ownerUserId=3;category=ACCESSIBILITY");
    }

    @Test
    void sameCreateKeyReturnsExistingTaskAndChangedReplayConflicts() {
        ReadinessTask prior = ReadinessTask.builder().id(15L).event(event).owner(organizer).createdBy(organizer)
                .title("Check access").description("Confirm step-free access").category("ACCESSIBILITY")
                .status("OPEN").dueAt(dueAt).requestKey("00000000-0000-4000-8000-000000000007").build();
        when(access.requireEventManager(7L)).thenReturn(event);
        when(events.findForApproval(7L)).thenReturn(Optional.of(event));
        when(access.currentUser()).thenReturn(organizer);
        when(users.findForUpdate(3L)).thenReturn(Optional.of(organizer));
        when(tasks.findByEventIdAndRequestKey(7L, prior.getRequestKey())).thenReturn(Optional.of(prior));

        assertEquals(15L, service.create(7L, create(prior.getRequestKey())).id());
        assertThrows(IllegalStateException.class, () -> service.create(7L,
                new ReadinessTaskDtos.Create("Different task", "Confirm step-free access", "ACCESSIBILITY", 3L, dueAt, prior.getRequestKey())));
        verify(tasks, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @Test
    void ownerMustBeEnabledAdminOrThisEventsOrganizer() {
        User unrelated = User.builder().id(9L).name("Unrelated").email("unrelated@example.test").password("x").role(Role.FACULTY).enabled(true).build();
        when(access.requireEventManager(7L)).thenReturn(event);
        when(events.findForApproval(7L)).thenReturn(Optional.of(event));
        when(access.currentUser()).thenReturn(organizer);
        when(users.findForUpdate(9L)).thenReturn(Optional.of(unrelated));

        assertThrows(AccessDeniedException.class, () -> service.create(7L,
                new ReadinessTaskDtos.Create("Check access", "Confirm step-free access", "ACCESSIBILITY", 9L, dueAt,
                        "00000000-0000-4000-8000-000000000008")));
        verify(tasks, never()).saveAndFlush(any());
    }

    @Test
    void newAssignmentNotifiesDifferentOwnerWithSafeGenericMessage() {
        User admin = User.builder().id(8L).name("Admin").email("admin@example.test").password("x").role(Role.ADMIN).enabled(true).build();
        when(access.requireEventManager(7L)).thenReturn(event);
        when(events.findForApproval(7L)).thenReturn(Optional.of(event));
        when(access.currentUser()).thenReturn(organizer);
        when(users.findForUpdate(8L)).thenReturn(Optional.of(admin));
        when(tasks.findByEventIdAndRequestKey(7L, "00000000-0000-4000-8000-000000000007")).thenReturn(Optional.empty());
        when(tasks.saveAndFlush(any())).thenAnswer(call -> {
            ReadinessTask row = call.getArgument(0);
            row.setId(15L); row.setCreatedAt(Instant.parse("2030-01-01T00:00:00Z")); row.setUpdatedAt(row.getCreatedAt());
            return row;
        });

        service.create(7L, new ReadinessTaskDtos.Create("Check access", "Confirm step-free access", "ACCESSIBILITY", 8L, dueAt,
                "00000000-0000-4000-8000-000000000007"));

        verify(notifications).createNotification(admin,
                "A readiness task has been assigned to you. Sign in to review your authorized event tasks.", "/app/");
    }

    private ReadinessTaskDtos.Create create(String key) {
        return new ReadinessTaskDtos.Create("Check access", "Confirm step-free access", "ACCESSIBILITY", 3L, dueAt, key);
    }
}

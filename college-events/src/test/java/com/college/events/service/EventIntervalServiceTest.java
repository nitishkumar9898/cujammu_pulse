package com.college.events.service;

import com.college.events.dto.EventDto;
import com.college.events.model.*;
import com.college.events.repository.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EventIntervalServiceTest {
    private final EventRepository events = mock(EventRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final FileStorageService files = mock(FileStorageService.class);
    private final EmailService email = mock(EmailService.class);
    private final RegistrationRepository registrations = mock(RegistrationRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final EventOperationsService operations = mock(EventOperationsService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final EventOperationsRepository locks = mock(EventOperationsRepository.class);
    private final FeatureFlagService featureFlags = mock(FeatureFlagService.class);
    private final EventService service = new EventService(events, categories, files, email,
            registrations, users, operations, notifications, locks);
    private final User owner = User.builder().id(2L).role(Role.ORGANIZER)
            .email("synthetic-organizer@example.invalid").build();
    private final LocalDateTime start = LocalDateTime.now().plusDays(20).withNano(0);

    @BeforeEach void configureRuntimeControls() {
        ReflectionTestUtils.setField(service, "completionFlags", featureFlags);
        var access = mock(com.college.events.security.DomainAccess.class);
        when(access.requireRole(Role.FACULTY, Role.ORGANIZER)).thenReturn(owner);
        ReflectionTestUtils.setField(service, "completionAccess", access);
        ReflectionTestUtils.setField(service, "completionAudit", mock(AuditService.class));
    }

    @Test void creationStoresExplicitEndAndLeavesUnknownEndNull() {
        when(categories.findById(1L)).thenReturn(Optional.of(new Category()));
        when(events.save(any(Event.class))).thenAnswer(invocation -> {
            Event event = invocation.getArgument(0); event.setId(7L); return event;
        });
        EventDto command = command();
        assertNull(service.createEvent(command, owner).getEventEndsAt());
        command.setEventEndsAt(start.plusHours(3));
        assertEquals(start.plusHours(3), service.createEvent(command, owner).getEventEndsAt());
    }

    @Test void invalidCreationIntervalFailsBeforeAnyUploadOrPersistence() {
        for (LocalDateTime end : List.of(start.minusMinutes(1), start)) {
            EventDto command = command(); command.setEventEndsAt(end);
            assertThrows(IllegalArgumentException.class, () -> service.createEvent(command, owner));
        }
        verifyNoInteractions(files, categories, events, email, notifications);
    }

    @Test void invalidEditDoesNotMutateExistingRecordOrTouchFiles() {
        Event event = event(); event.setStatus(EventStatus.PENDING); event.setEventEndsAt(start.plusHours(1));
        when(events.findForApproval(7L)).thenReturn(Optional.of(event));
        EventDto command = command(); command.setEventEndsAt(start);
        assertThrows(IllegalArgumentException.class, () -> service.updateEvent(7L, command, owner));
        assertEquals(start.plusHours(1), event.getEventEndsAt());
        assertEquals(EventStatus.PENDING, event.getStatus());
        verify(events, never()).saveAndFlush(any());
        verifyNoInteractions(files, categories, email, notifications);
    }

    @Test void acceptedEditStoresEndAndResubmitsForApproval() {
        Event event = event(); event.setStatus(EventStatus.REJECTED);
        when(events.findForApproval(7L)).thenReturn(Optional.of(event));
        when(categories.findById(1L)).thenReturn(Optional.of(new Category()));
        when(events.saveAndFlush(event)).thenReturn(event);
        EventDto command = command(); command.setEventEndsAt(start.plusMinutes(90));
        Event updated = service.updateEvent(7L, command, owner);
        assertEquals(start.plusMinutes(90), updated.getEventEndsAt());
        assertEquals(EventStatus.PENDING, updated.getStatus());
    }

    @Test void postponementPreservesExplicitMultiDayDuration() {
        Event event = event(); event.setEventEndsAt(start.plusDays(2).plusMinutes(15)); prepare(event);
        LocalDateTime replacement = start.plusMonths(1);
        service.postponeEvent(7L, replacement, "Synthetic schedule change", owner);
        service.postponeEvent(7L, replacement, "Synthetic schedule change", owner);
        assertEquals(replacement, event.getEventDate());
        assertEquals(Duration.ofDays(2).plusMinutes(15), Duration.between(event.getEventDate(), event.getEventEndsAt()));
        verify(events).saveAndFlush(event);
        verify(email).sendEventPostponementNotification(event, List.of());
    }

    @Test void postponementKeepsUnknownEndUnknown() {
        Event event = event(); prepare(event);
        service.postponeEvent(7L, start.plusDays(1), "Synthetic schedule change", owner);
        assertNull(event.getEventEndsAt());
    }

    @Test void malformedStoredIntervalIsRejectedBeforeMutationEvenOnReplay() {
        Event event = event(); event.setEventEndsAt(start); event.setPostponeReason("Same request"); prepare(event);
        var rejection = assertThrows(IllegalStateException.class, () -> service.postponeEvent(7L, start, "Same request", owner));
        assertTrue(rejection.getMessage().contains("stored event interval"));
        assertEquals(start, event.getEventDate());
        verify(events, never()).saveAndFlush(any());
        verifyNoInteractions(email, notifications, registrations);
    }

    @Test void intervalValidationIsAvailableToOrganizerForms() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            EventDto command = command(); command.setEventEndsAt(start.minusMinutes(1));
            assertTrue(factory.getValidator().validate(command).stream()
                    .anyMatch(violation -> violation.getPropertyPath().toString().equals("eventIntervalValid")));
            command.setEventEndsAt(null);
            assertTrue(factory.getValidator().validate(command).isEmpty());
        }
    }

    private EventDto command() {
        EventDto dto = new EventDto(); dto.setTitle("Synthetic event");
        dto.setExpectedLifecycleVersion(0L);
        dto.setDescription("Synthetic event interval test description"); dto.setCategoryId(1L);
        dto.setVenue("Synthetic hall"); dto.setEventDate(start); dto.setMaxSeats(20); return dto;
    }

    private Event event() {
        return Event.builder().id(7L).organizer(owner).title("Synthetic event").description("Synthetic")
                .venue("Synthetic hall").eventDate(start).status(EventStatus.APPROVED).maxSeats(20).build();
    }

    private void prepare(Event event) {
        when(locks.findForUpdate(7L)).thenReturn(Optional.of(EventOperations.builder().event(event).build()));
        when(events.findForApproval(7L)).thenReturn(Optional.of(event));
        when(registrations.findNotifiableByEvent(eq(event), any())).thenReturn(List.of());
    }
}

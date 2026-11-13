package com.college.events.service;

import com.college.events.model.*;
import com.college.events.repository.*;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class RegistrationServiceWaitlistLifecycleTest {
    @Test
    void closedEventCancellationDoesNotPromoteOrNotifyWaitlistedStudent() throws Exception {
        var registrations = mock(RegistrationRepository.class);
        var events = mock(EventRepository.class);
        var email = mock(EmailService.class);
        var notifications = mock(NotificationService.class);
        var audit = mock(AuditService.class);
        var operations = mock(EventOperationsRepository.class);
        var teamMembers = mock(TeamMemberRepository.class);
        var flags = mock(FeatureFlagService.class);

        User attendee = User.builder().id(20L).name("Synthetic attendee").email("attendee@example.invalid")
                .role(Role.STUDENT).build();
        User organizer = User.builder().id(21L).name("Synthetic organizer").email("organizer@example.invalid")
                .role(Role.ORGANIZER).build();
        Event event = Event.builder().id(30L).organizer(organizer).title("Synthetic cancelled event")
                .eventDate(LocalDateTime.now().plusDays(2)).status(EventStatus.CANCELLED).build();
        Registration registration = Registration.builder().id(40L).event(event).user(attendee)
                .status(RegistrationStatus.REGISTERED).build();

        when(registrations.findOwnedEventId(40L, 20L)).thenReturn(Optional.of(30L));
        when(operations.findForUpdate(30L)).thenReturn(Optional.of(EventOperations.builder().event(event).build()));
        when(registrations.findForUpdate(40L)).thenReturn(Optional.of(registration));
        when(teamMembers.existsByUserIdAndEventId(20L, 30L)).thenReturn(false);

        var service = new RegistrationService(registrations, events, email, notifications, audit, operations, teamMembers);
        var featureField = RegistrationService.class.getDeclaredField("featureFlags");
        featureField.setAccessible(true);
        featureField.set(service, flags);

        assertDoesNotThrow(() -> service.cancelRegistration(40L, attendee));
        assertEquals(RegistrationStatus.CANCELLED, registration.getStatus());
        verify(registrations, never()).findFirstByEventAndStatusAndUser_EnabledTrueOrderByIdAsc(event, RegistrationStatus.WAITLISTED);
        verify(email, never()).sendWaitlistPromotion(any());
        verify(notifications, never()).createNotification(any(User.class), contains("seat opened"), anyString());
    }
}

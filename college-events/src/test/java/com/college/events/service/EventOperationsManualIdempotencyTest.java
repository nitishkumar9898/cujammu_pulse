package com.college.events.service;

import com.college.events.dto.EventOperationsDtos.ManualCheckIn;
import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventOperationsManualIdempotencyTest {
    @Mock private EventRepository events;
    @Mock private EventOperationsRepository operations;
    @Mock private VolunteerMembershipRepository memberships;
    @Mock private EventOperatorGrantRepository grants;
    @Mock private RegistrationRepository registrations;
    @Mock private ManualAttendanceEvidenceRepository manualEvidence;
    @Mock private UserRepository users;
    @Mock private DomainAccess access;
    @Mock private AuditService audit;
    @Mock private NotificationService notifications;
    @Mock private FeatureFlagService featureFlags;

    private EventOperationsService service;
    private Event event;
    private User owner;
    private ManualAttendanceEvidence prior;
    private final long eventId = 73L;
    private final String identifier = "CS-0012";
    private final String reason = "PHONE_UNAVAILABLE";
    private final String key = "7b2e3460-c69f-4c71-a618-5426313d2b2c";

    @BeforeEach
    void setUp() {
        service = new EventOperationsService(events, operations, memberships, grants,
                registrations, manualEvidence, users, access, audit, notifications, featureFlags);
        owner = User.builder().id(5L).name("Organizer").role(Role.FACULTY).build();
        event = Event.builder().id(eventId).title("Campus event").status(EventStatus.APPROVED)
                .organizer(owner).build();
        EventOperations control = EventOperations.builder().event(event).manualWindowOpen(true)
                .manualOpensAt(LocalDateTime.now().minusMinutes(5))
                .manualClosesAt(LocalDateTime.now().plusMinutes(5)).build();
        when(operations.findForUpdate(eventId)).thenReturn(Optional.of(control));
        when(access.currentUser()).thenReturn(owner);
        when(access.isEventOwner(owner, event)).thenReturn(true);

        User attendee = User.builder().id(12L).name("Student Example").build();
        Registration registration = Registration.builder().id(901L).user(attendee).event(event).build();
        prior = ManualAttendanceEvidence.builder().id(44L).event(event).registration(registration)
                .operator(owner).action("ENTRY").idempotencyKey(key)
                .verifiedIdentifierSnapshot(identifier).reasonCode(reason).occurredAt(Instant.parse("2026-10-02T10:00:00Z"))
                .build();
    }

    @Test
    void retryReturnsStoredReceiptBeforeRosterLookup() {
        when(manualEvidence.findByEventIdAndIdempotencyKey(eventId, key)).thenReturn(Optional.of(prior));

        var receipt = service.manualCheckIn(eventId, new ManualCheckIn(identifier, reason, key));

        assertEquals(44L, receipt.evidenceId());
        assertEquals("DUPLICATE", receipt.status());
        assertEquals(identifier, receipt.identifier());
        verifyNoInteractions(registrations);
        verify(manualEvidence, never()).save(any());
    }

    @Test
    void reusedKeyWithChangedPayloadIsRejectedBeforeRosterLookup() {
        when(manualEvidence.findByEventIdAndIdempotencyKey(eventId, key)).thenReturn(Optional.of(prior));

        assertThrows(IllegalArgumentException.class,
                () -> service.manualCheckIn(eventId, new ManualCheckIn("CS-0099", reason, key)));

        verifyNoInteractions(registrations);
        verify(manualEvidence, never()).save(any());
    }
}

package com.college.events.service;

import com.college.events.dto.EventAttendanceDtos;
import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EventQrAttendanceServiceTest {
    RegistrationRepository registrations = mock(RegistrationRepository.class);
    EventOperationsRepository operations = mock(EventOperationsRepository.class);
    EventQrCredentialRepository credentials = mock(EventQrCredentialRepository.class);
    EventScanIntentRepository intents = mock(EventScanIntentRepository.class);
    EventQrAttendanceEvidenceRepository evidence = mock(EventQrAttendanceEvidenceRepository.class);
    DomainAccess access = mock(DomainAccess.class);
    EventOperationsService eventOperations = mock(EventOperationsService.class);
    FeatureFlagService features = mock(FeatureFlagService.class);
    AuditService audit = mock(AuditService.class);
    NotificationService notifications = mock(NotificationService.class);
    EventQrAttendanceService service = new EventQrAttendanceService(registrations, operations, credentials, intents,
            evidence, access, eventOperations, features, audit, notifications);

    @Test
    void issuanceReturnsOpaqueCredentialAndPersistsOnlyDigest() {
        User student = user(1L, Role.STUDENT);
        Event event = event(7L, student);
        Registration registration = registration(9L, event, student);
        EventOperations operation = EventOperations.builder().event(event).attendanceMode("DUAL_QR").build();
        when(access.currentUser()).thenReturn(student);
        when(registrations.findOwnedEventId(9L, 1L)).thenReturn(Optional.of(7L));
        when(registrations.findForUpdate(9L)).thenReturn(Optional.of(registration));
        when(operations.findForUpdate(7L)).thenReturn(Optional.of(operation));
        when(credentials.findForUpdateByRegistrationAndAction(9L, "ENTRY")).thenReturn(Optional.empty());
        when(credentials.save(any())).thenAnswer(invocation -> {
            EventQrCredential saved = invocation.getArgument(0); saved.setId(12L); return saved;
        });

        Instant issuedBefore = Instant.now();
        EventAttendanceDtos.CredentialView result = service.issueCredential(7L, 9L,
                new EventAttendanceDtos.IssueCredential("ENTRY"));

        assertNotNull(result.credential());
        assertTrue(result.credential().length() > 30);
        assertTrue(result.expiresAt().isAfter(issuedBefore));
        assertFalse(result.expiresAt().isAfter(issuedBefore.plusSeconds(91)));
        var issued = org.mockito.ArgumentCaptor.forClass(EventQrCredential.class);
        verify(credentials).save(issued.capture());
        verify(intents).revokePendingByCredentialId(12L);
        assertNotEquals(result.credential(), issued.getValue().getTokenDigest());
        assertEquals(0L, issued.getValue().getScannerEpoch());
        verify(audit).logAction(contains("@"), eq("STUDENT"), eq("EVENT_QR_CREDENTIAL_ISSUED"), contains("eventId=7"));
    }

    @Test
    void checkpointCredentialExpiresAtEarlierApprovedWindowBoundary() {
        User student = user(1L, Role.STUDENT);
        Event event = event(7L, student);
        Registration registration = registration(9L, event, student);
        Instant windowClosesAt = Instant.now().plusSeconds(30);
        EventOperations operation = EventOperations.builder().event(event).attendanceMode("DUAL_QR")
                .scannerMasterEnabled(true).scannerActions("ATTENDANCE_CHECKPOINT")
                .scannerClosesAt(LocalDateTime.ofInstant(windowClosesAt, java.time.ZoneId.of("Asia/Kolkata"))).build();
        when(access.currentUser()).thenReturn(student);
        when(registrations.findOwnedEventId(9L, 1L)).thenReturn(Optional.of(7L));
        when(registrations.findForUpdate(9L)).thenReturn(Optional.of(registration));
        when(operations.findForUpdate(7L)).thenReturn(Optional.of(operation));
        when(credentials.findForUpdateByRegistrationAndAction(9L, "ATTENDANCE_CHECKPOINT")).thenReturn(Optional.empty());
        when(credentials.save(any())).thenAnswer(invocation -> {
            EventQrCredential saved = invocation.getArgument(0); saved.setId(12L); return saved;
        });

        EventAttendanceDtos.CredentialView result = service.issueCredential(7L, 9L,
                new EventAttendanceDtos.IssueCredential("ATTENDANCE_CHECKPOINT"));

        assertTrue(result.expiresAt().isAfter(Instant.now()));
        assertFalse(result.expiresAt().isAfter(windowClosesAt));
        verify(intents).revokePendingByCredentialId(12L);
    }

    @Test
    void checkpointCredentialCannotBeIssuedBeforeApprovedWindow() {
        User student = user(1L, Role.STUDENT);
        Event event = event(7L, student);
        Registration registration = registration(9L, event, student);
        EventOperations operation = EventOperations.builder().event(event).attendanceMode("DUAL_QR")
                .scannerMasterEnabled(false).scannerActions("ENTRY").build();
        when(access.currentUser()).thenReturn(student);
        when(registrations.findOwnedEventId(9L, 1L)).thenReturn(Optional.of(7L));
        when(registrations.findForUpdate(9L)).thenReturn(Optional.of(registration));
        when(operations.findForUpdate(7L)).thenReturn(Optional.of(operation));

        assertThrows(IllegalStateException.class, () -> service.issueCredential(7L, 9L,
                new EventAttendanceDtos.IssueCredential("ATTENDANCE_CHECKPOINT")));
        verify(credentials, never()).save(any());
    }

    @Test
    void ownerConfirmationAppendsEvidenceAndConsumesCredential() {
        User owner = user(1L, Role.STUDENT);
        User operator = user(2L, Role.STUDENT);
        Event event = event(7L, operator);
        Registration registration = registration(9L, event, owner);
        EventQrCredential credential = EventQrCredential.builder().id(11L).event(event).registration(registration)
                .tokenDigest(digest("opaque-token")).action("ENTRY").scannerEpoch(0L).status("ACTIVE")
                .expiresAt(Instant.now().plusSeconds(60)).build();
        String intentKey = UUID.randomUUID().toString();
        EventScanIntent intent = EventScanIntent.builder().id(15L).intentKey(intentKey).requestKey(UUID.randomUUID().toString())
                .event(event).registration(registration).credential(credential).operator(operator).action("ENTRY")
                .credentialVersionAtScan(0L).scannerEpochAtScan(0L)
                .challengeDigest(digest("123456")).status("PENDING").expiresAt(Instant.now().plusSeconds(60)).build();
        when(access.currentUser()).thenReturn(owner);
        when(intents.findOwnedEventId(intentKey, owner.getId())).thenReturn(Optional.of(event.getId()));
        when(operations.findForUpdate(event.getId())).thenReturn(Optional.of(EventOperations.builder().event(event).attendanceMode("DUAL_QR").build()));
        when(intents.findForUpdateByIntentKey(intentKey)).thenReturn(Optional.of(intent));
        when(evidence.findByEventIdAndRegistrationIdAndAction(7L, 9L, "ENTRY")).thenReturn(Optional.empty());
        when(credentials.findForUpdateByDigest(credential.getTokenDigest())).thenReturn(Optional.of(credential));
        when(evidence.save(any())).thenAnswer(invocation -> {
            EventQrAttendanceEvidence saved = invocation.getArgument(0);
            return EventQrAttendanceEvidence.builder().id(21L).event(saved.getEvent()).registration(saved.getRegistration())
                    .operator(saved.getOperator()).intent(saved.getIntent()).action(saved.getAction())
                    .source(saved.getSource()).occurredAt(saved.getOccurredAt()).build();
        });

        EventAttendanceDtos.Receipt result = service.confirm(intentKey,
                new EventAttendanceDtos.ConfirmIntent("123456", UUID.randomUUID().toString()));

        assertEquals(21L, result.evidenceId());
        assertEquals("RECORDED", result.status());
        assertEquals("CONSUMED", credential.getStatus());
        assertEquals("CONFIRMED", intent.getStatus());
        assertEquals(RegistrationStatus.ATTENDED, registration.getStatus(), "Legacy status preserves the admission cancellation barrier and is not certificate evidence");
    }

    @Test
    void retryAfterCommittedOwnerConfirmationReturnsOriginalRecordedReceipt() {
        User owner = user(1L, Role.STUDENT);
        User operator = user(2L, Role.STUDENT);
        Event event = event(7L, operator);
        Registration registration = registration(9L, event, owner);
        String intentKey = UUID.randomUUID().toString();
        EventScanIntent intent = EventScanIntent.builder().id(15L).intentKey(intentKey)
                .event(event).registration(registration).operator(operator).action("ENTRY")
                .status("CONFIRMED").expiresAt(Instant.now().minusSeconds(2)).build();
        Instant acceptedAt = Instant.now().minusSeconds(1);
        EventQrAttendanceEvidence accepted = EventQrAttendanceEvidence.builder().id(33L).event(event)
                .registration(registration).operator(operator).intent(intent).action("ENTRY")
                .source("QR_OWNER_CONFIRMED").occurredAt(acceptedAt).build();
        when(access.currentUser()).thenReturn(owner);
        when(intents.findOwnedEventId(intentKey, owner.getId())).thenReturn(Optional.of(event.getId()));
        when(operations.findForUpdate(event.getId())).thenReturn(Optional.of(EventOperations.builder()
                .event(event).attendanceMode("DUAL_QR").build()));
        when(intents.findForUpdateByIntentKey(intentKey)).thenReturn(Optional.of(intent));
        when(evidence.findByEventIdAndRegistrationIdAndAction(7L, 9L, "ENTRY")).thenReturn(Optional.of(accepted));

        EventAttendanceDtos.Receipt retry = service.confirm(intentKey,
                new EventAttendanceDtos.ConfirmIntent("123456", UUID.randomUUID().toString()));

        assertEquals(33L, retry.evidenceId());
        assertEquals("RECORDED", retry.status());
        assertEquals(acceptedAt, retry.occurredAt());
        verifyNoInteractions(credentials);
        verify(evidence, never()).save(any());
    }

    @Test
    void staleScannerEpochCannotCreateAnIntent() {
        User owner = user(1L, Role.STUDENT);
        User operator = user(2L, Role.STUDENT);
        Event event = event(7L, operator);
        Registration registration = registration(9L, event, owner);
        EventOperations operation = EventOperations.builder().event(event).scannerEpoch(2L).build();
        EventQrCredential credential = EventQrCredential.builder().id(11L).event(event).registration(registration)
                .tokenDigest(digest("opaque-token")).scannerEpoch(1L).action("ENTRY").status("ACTIVE")
                .expiresAt(Instant.now().plusSeconds(60)).build();
        when(access.currentUser()).thenReturn(operator);
        when(operations.findForUpdate(7L)).thenReturn(Optional.of(operation));
        when(intents.findByEventIdAndRequestKey(eq(7L), anyString())).thenReturn(Optional.empty());
        when(credentials.findForUpdateByDigest(digest("opaque-token"))).thenReturn(Optional.of(credential));

        assertThrows(IllegalStateException.class, () -> service.createIntent(7L,
                new EventAttendanceDtos.CreateIntent("opaque-token", "ENTRY", UUID.randomUUID().toString())));
        verify(intents, never()).save(any());
        verifyNoInteractions(notifications, evidence);
    }

    @Test
    void repeatedScanCannotCreateAnotherLiveIntentOrNotifyOwnerAgain() {
        User owner = user(1L, Role.STUDENT);
        User operator = user(2L, Role.STUDENT);
        Event event = event(7L, operator);
        Registration registration = registration(9L, event, owner);
        EventOperations operation = EventOperations.builder().event(event).scannerEpoch(2L).build();
        EventQrCredential credential = EventQrCredential.builder().id(11L).event(event).registration(registration)
                .tokenDigest(digest("opaque-token")).scannerEpoch(2L).action("ENTRY").status("ACTIVE")
                .expiresAt(Instant.now().plusSeconds(60)).build();
        when(access.currentUser()).thenReturn(operator);
        when(operations.findForUpdate(7L)).thenReturn(Optional.of(operation));
        when(intents.findByEventIdAndRequestKey(eq(7L), anyString())).thenReturn(Optional.empty());
        when(credentials.findForUpdateByDigest(digest("opaque-token"))).thenReturn(Optional.of(credential));
        when(intents.existsLivePendingByCredentialId(eq(11L), any(Instant.class))).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.createIntent(7L,
                new EventAttendanceDtos.CreateIntent("opaque-token", "ENTRY", UUID.randomUUID().toString())));
        verify(intents, never()).save(any());
        verifyNoInteractions(notifications, evidence);
    }

    @Test
    void staleScannerEpochCannotConfirmAnExistingIntent() {
        User owner = user(1L, Role.STUDENT);
        User operator = user(2L, Role.STUDENT);
        Event event = event(7L, operator);
        Registration registration = registration(9L, event, owner);
        EventQrCredential credential = EventQrCredential.builder().id(11L).event(event).registration(registration)
                .tokenDigest(digest("opaque-token")).scannerEpoch(1L).action("ENTRY").status("ACTIVE")
                .expiresAt(Instant.now().plusSeconds(60)).build();
        String intentKey = UUID.randomUUID().toString();
        EventScanIntent intent = EventScanIntent.builder().id(15L).intentKey(intentKey).requestKey(UUID.randomUUID().toString())
                .event(event).registration(registration).credential(credential).operator(operator).action("ENTRY")
                .credentialVersionAtScan(0L).scannerEpochAtScan(1L)
                .challengeDigest(digest("123456")).status("PENDING").expiresAt(Instant.now().plusSeconds(60)).build();
        when(access.currentUser()).thenReturn(owner);
        when(intents.findOwnedEventId(intentKey, owner.getId())).thenReturn(Optional.of(event.getId()));
        when(operations.findForUpdate(event.getId())).thenReturn(Optional.of(EventOperations.builder()
                .event(event).attendanceMode("DUAL_QR").scannerEpoch(2L).build()));
        when(intents.findForUpdateByIntentKey(intentKey)).thenReturn(Optional.of(intent));
        when(evidence.findByEventIdAndRegistrationIdAndAction(7L, 9L, "ENTRY")).thenReturn(Optional.empty());
        when(credentials.findForUpdateByDigest(credential.getTokenDigest())).thenReturn(Optional.of(credential));

        assertThrows(IllegalStateException.class, () -> service.confirm(intentKey,
                new EventAttendanceDtos.ConfirmIntent("123456", UUID.randomUUID().toString())));
        verify(evidence, never()).save(any());
        assertEquals("ACTIVE", credential.getStatus());
        assertEquals("PENDING", intent.getStatus());
    }

    @Test
    void wrongOwnerCannotConfirmPendingIntent() {
        User owner = user(1L, Role.STUDENT);
        User stranger = user(3L, Role.STUDENT);
        Event event = event(7L, stranger);
        Registration registration = registration(9L, event, owner);
        EventScanIntent intent = EventScanIntent.builder().intentKey(UUID.randomUUID().toString()).event(event)
                .registration(registration).action("ENTRY").status("PENDING").expiresAt(Instant.now().plusSeconds(60)).build();
        when(access.currentUser()).thenReturn(stranger);
        when(intents.findForUpdateByIntentKey(intent.getIntentKey())).thenReturn(Optional.of(intent));

        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.confirm(intent.getIntentKey(),
                new EventAttendanceDtos.ConfirmIntent("123456", UUID.randomUUID().toString())));
        verifyNoInteractions(evidence);
    }

    @Test
    void reissuedCredentialCannotConfirmAnIntentFromThePriorCredentialVersion() {
        User owner = user(1L, Role.STUDENT);
        User operator = user(2L, Role.STUDENT);
        Event event = event(7L, operator);
        Registration registration = registration(9L, event, owner);
        EventQrCredential credential = EventQrCredential.builder().id(11L).event(event).registration(registration)
                .tokenDigest(digest("new-token")).action("ENTRY").credentialVersion(2L).scannerEpoch(0L)
                .status("ACTIVE").expiresAt(Instant.now().plusSeconds(60)).build();
        String intentKey = UUID.randomUUID().toString();
        EventScanIntent intent = EventScanIntent.builder().id(15L).intentKey(intentKey).requestKey(UUID.randomUUID().toString())
                .event(event).registration(registration).credential(credential).operator(operator).action("ENTRY")
                .credentialVersionAtScan(1L).scannerEpochAtScan(0L)
                .challengeDigest(digest("123456")).status("PENDING").expiresAt(Instant.now().plusSeconds(60)).build();
        when(access.currentUser()).thenReturn(owner);
        when(intents.findOwnedEventId(intentKey, owner.getId())).thenReturn(Optional.of(event.getId()));
        when(operations.findForUpdate(event.getId())).thenReturn(Optional.of(EventOperations.builder()
                .event(event).attendanceMode("DUAL_QR").scannerEpoch(0L).build()));
        when(intents.findForUpdateByIntentKey(intentKey)).thenReturn(Optional.of(intent));
        when(evidence.findByEventIdAndRegistrationIdAndAction(7L, 9L, "ENTRY")).thenReturn(Optional.empty());
        when(credentials.findForUpdateByDigest(credential.getTokenDigest())).thenReturn(Optional.of(credential));

        assertThrows(IllegalStateException.class, () -> service.confirm(intentKey,
                new EventAttendanceDtos.ConfirmIntent("123456", UUID.randomUUID().toString())));
        verify(evidence, never()).save(any());
        assertEquals("ACTIVE", credential.getStatus());
        assertEquals("PENDING", intent.getStatus());
    }

    private User user(long id, Role role) {
        return User.builder().id(id).name("Synthetic " + id).email("u" + id + "@example.invalid")
                .role(role).verifiedEnrollmentNo("ENR-" + id).enabled(true).build();
    }
    private Event event(long id, User organizer) {
        return Event.builder().id(id).title("Synthetic event").description("Description")
                .organizer(organizer).venue("Hall").eventDate(LocalDateTime.now().plusDays(1))
                .maxSeats(100).status(EventStatus.APPROVED).build();
    }
    private Registration registration(long id, Event event, User user) {
        return Registration.builder().id(id).event(event).user(user).status(RegistrationStatus.REGISTERED).build();
    }
    private String digest(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : hash) out.append(String.format("%02x", b));
            return out.toString();
        } catch (Exception ex) { throw new AssertionError(ex); }
    }
}

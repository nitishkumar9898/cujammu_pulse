package com.college.events.service;

import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.access.AccessDeniedException;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventCompletionGuardTest {
    final EventRepository events=mock(EventRepository.class);
    final EventOperationsRepository locks=mock(EventOperationsRepository.class);
    final RegistrationRepository registrations=mock(RegistrationRepository.class);
    final DomainAccess access=mock(DomainAccess.class);
    final AuditService audit=mock(AuditService.class);
    final FeatureFlagService flags=mock(FeatureFlagService.class);
    final EventService service=new EventService(events,mock(CategoryRepository.class),mock(FileStorageService.class),
            mock(EmailService.class),registrations,mock(UserRepository.class),mock(EventOperationsService.class),
            mock(NotificationService.class),locks);
    final User owner=User.builder().id(1L).role(Role.ORGANIZER).email("owner@example.invalid").build();
    final Event event=Event.builder().id(10L).organizer(owner).status(EventStatus.APPROVED)
            .approvedAt(LocalDateTime.now().minusDays(1)).eventDate(LocalDateTime.now().minusHours(1)).build();
    final EventOperations operation=EventOperations.builder().event(event).scannerMasterEnabled(true).scannerScope("ALL")
            .manualWindowOpen(true).scannerEpoch(5L).manualEpoch(8L).build();
    @BeforeEach void setup() {
        ReflectionTestUtils.setField(service,"completionAccess",access);ReflectionTestUtils.setField(service,"completionAudit",audit);
        ReflectionTestUtils.setField(service,"completionFlags",flags); when(access.currentUser()).thenReturn(owner);
        when(access.isEventOwner(owner,event)).thenReturn(true);when(locks.findForUpdate(10L)).thenReturn(Optional.of(operation));
        when(events.findForApproval(10L)).thenReturn(Optional.of(event));
        when(registrations.findNotifiableByEvent(any(),any())).thenReturn(List.of());
    }
    @Test void suppliedOrganizerCannotImpersonateAuthenticatedActor() {
        var impostor=User.builder().id(99L).role(Role.ADMIN).build();
        assertThrows(AccessDeniedException.class,()->service.markCompleted(10L,impostor));
        verifyNoInteractions(events,locks,audit);
    }
    @Test void completionFencesBothWindowsAndEpochsThenRecordsActor() {
        service.markCompleted(10L,owner);
        assertEquals(EventStatus.COMPLETED,event.getStatus());assertNotNull(event.getCompletedAt());assertEquals(1L,event.getCompletedByUserId());
        assertFalse(operation.isScannerMasterEnabled());assertEquals("OFF",operation.getScannerScope());assertFalse(operation.isManualWindowOpen());
        assertEquals(6,operation.getScannerEpoch());assertEquals(9,operation.getManualEpoch());
        assertNotNull(operation.getScannerClosesAt());assertNotNull(operation.getManualClosesAt());
        verify(flags).requireEnabledForUpdate("MODULE_EVENTS");verify(locks).saveAndFlush(operation);
    }
    @Test void earlyPendingAndCancelledCannotComplete() {
        event.setEventDate(LocalDateTime.now().plusDays(1)); assertThrows(IllegalStateException.class,()->service.markCompleted(10L,owner));
        event.setEventDate(LocalDateTime.now().minusHours(1));event.setStatus(EventStatus.PENDING);
        assertThrows(IllegalStateException.class,()->service.markCompleted(10L,owner));
        event.setStatus(EventStatus.CANCELLED);event.setCancelledAt(LocalDateTime.now());
        assertThrows(IllegalStateException.class,()->service.markCompleted(10L,owner));
        verify(events,never()).save(any());verify(locks,never()).saveAndFlush(any());verifyNoInteractions(audit);
    }
    @Test void repeatCompletionDoesNotFenceAgainOrRepeatAudit() {
        service.markCompleted(10L,owner);service.markCompleted(10L,owner);
        assertEquals(6,operation.getScannerEpoch());verify(events,times(1)).saveAndFlush(event);
        verify(audit,times(1)).logAction(anyString(),anyString(),eq("EVENT_COMPLETED"),anyString());
    }
    @Test void startTimestampAloneCannotAutomaticallyCloseEvent() {
        service.autoCompleteExpiredEvents();assertEquals(EventStatus.APPROVED,event.getStatus());verifyNoInteractions(events,locks,audit);
    }
    @Test void unavailableAuthoritativeControlsFailClosed() {
        ReflectionTestUtils.setField(service,"completionAccess",null);
        assertThrows(IllegalStateException.class,()->service.markCompleted(10L,owner));verifyNoInteractions(events,locks,audit);
    }
}

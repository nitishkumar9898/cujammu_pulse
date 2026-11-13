package com.college.events.service;

import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.InOrder;

class EventScheduleServiceTest {
    private final DomainAccess access = mock(DomainAccess.class);
    private final FacilityRepository facilities = mock(FacilityRepository.class);
    private final FacilityBookingRepository bookings = mock(FacilityBookingRepository.class);
    private final EventOperationsRepository operations = mock(EventOperationsRepository.class);
    private final EventService eventService = mock(EventService.class);
    private final CUJammuMasterService campusFacilities = mock(CUJammuMasterService.class);
    private final AuditService audit = mock(AuditService.class);
    private final FeatureFlagService featureFlags = mock(FeatureFlagService.class);
    private final EventScheduleService service = new EventScheduleService(access, facilities, bookings, operations, eventService, campusFacilities, audit);
    private final Event event = mock(Event.class);
    private final User actor = User.builder().id(7L).name("Synthetic organizer").email("organizer@example.invalid")
            .password("hash").role(Role.ORGANIZER).enabled(true).build();
    private final Facility facility = Facility.builder().id(3L).name("Synthetic Hall").facilityType("AUDITORIUM").active(true).build();
    private final FacilityBooking reservation = mock(FacilityBooking.class);
    private final EventOperations eventOperations = mock(EventOperations.class);
    private final LocalDateTime newStart = LocalDateTime.of(2030, 5, 2, 10, 0);

    @BeforeEach void setup() {
        ReflectionTestUtils.setField(service, "featureFlags", featureFlags);
        when(operations.findForUpdate(11L)).thenReturn(Optional.of(eventOperations));
        when(access.requireEventManager(11L)).thenReturn(event);
        when(access.currentUser()).thenReturn(actor);
        when(event.getId()).thenReturn(11L);
        when(event.getStatus()).thenReturn(EventStatus.APPROVED);
        when(event.getVenue()).thenReturn("Synthetic Hall");
        when(reservation.getId()).thenReturn(19L);
        when(reservation.getEvent()).thenReturn(event);
        when(reservation.getFacility()).thenReturn(facility);
        when(reservation.getStatus()).thenReturn("CONFIRMED");
        when(reservation.getStartTime()).thenReturn(LocalDateTime.of(2030, 5, 1, 10, 0));
        when(reservation.getEndTime()).thenReturn(LocalDateTime.of(2030, 5, 1, 12, 0));
        when(bookings.findActiveByEventIdOrderByStartTimeAsc(11L)).thenReturn(List.of(reservation));
        when(facilities.findForBooking(3L)).thenReturn(Optional.of(facility));
        when(bookings.findForUpdate(19L)).thenReturn(Optional.of(reservation));
        when(bookings.findConflictingBookingsExcept(eq(3L), any(), any(), eq(19L))).thenReturn(List.of());
        when(bookings.saveAndFlush(reservation)).thenReturn(reservation);
    }

    @Test void cancelAcquiresModuleAndActionControlsBeforeLifecycleLocks() {
        service.cancel(11L);
        InOrder order = inOrder(featureFlags, operations);
        order.verify(featureFlags).requireEnabledForUpdate("MODULE_EVENTS");
        order.verify(featureFlags).requireEnabledForUpdate("ACTION_EVENT_CANCEL");
        order.verify(operations).findForUpdate(11L);
    }

    @Test void rescheduleAcquiresModuleAndActionControlsBeforeLifecycleLocks() {
        service.reschedule(11L, newStart, "Weather disruption");
        InOrder order = inOrder(featureFlags, operations);
        order.verify(featureFlags).requireEnabledForUpdate("MODULE_EVENTS");
        order.verify(featureFlags).requireEnabledForUpdate("ACTION_EVENT_POSTPONE");
        order.verify(operations).findForUpdate(11L);
    }

    @Test void disabledEventModuleFailsBeforeLifecycleOrResourceAccess() {
        doThrow(new IllegalStateException("disabled"))
                .when(featureFlags).requireEnabledForUpdate("MODULE_EVENTS");
        assertThrows(IllegalStateException.class, () -> service.cancel(11L));
        assertThrows(IllegalStateException.class, () -> service.reschedule(11L, newStart, "Weather disruption"));
        verify(operations, never()).findForUpdate(anyLong());
        verify(access, never()).requireEventManager(anyLong());
        verify(bookings, never()).saveAndFlush(any());
        verify(eventService, never()).cancelEvent(anyLong(), any());
        verify(eventService, never()).postponeEvent(anyLong(), any(), anyString(), any());
    }

    @Test void disabledLifecycleActionFailsBeforeLifecycleOrResourceAccess() {
        doThrow(new IllegalStateException("disabled"))
                .when(featureFlags).requireEnabledForUpdate("ACTION_EVENT_POSTPONE");
        assertThrows(IllegalStateException.class, () -> service.reschedule(11L, newStart, "Weather disruption"));
        verify(operations, never()).findForUpdate(anyLong());
        verify(access, never()).requireEventManager(anyLong());
        verify(bookings, never()).saveAndFlush(any());
        verify(eventService, never()).postponeEvent(anyLong(), any(), anyString(), any());
    }

    @Test void disabledCancelActionFailsBeforeLifecycleOrResourceAccess() {
        doThrow(new IllegalStateException("disabled"))
                .when(featureFlags).requireEnabledForUpdate("ACTION_EVENT_CANCEL");
        assertThrows(IllegalStateException.class, () -> service.cancel(11L));
        verify(operations, never()).findForUpdate(anyLong());
        verify(access, never()).requireEventManager(anyLong());
        verify(bookings, never()).saveAndFlush(any());
        verify(eventService, never()).cancelEvent(anyLong(), any());
    }

    @Test void rescheduleMovesEventAndSingleLinkedReservationInsideTheGuardedCommand() {
        service.reschedule(11L, newStart, "Weather disruption");
        verify(reservation).setStartTime(newStart);
        verify(reservation).setEndTime(newStart.plusHours(2));
        verify(bookings).saveAndFlush(reservation);
        verify(eventService).postponeEvent(11L, newStart, "Weather disruption", actor);
        verify(audit).logAction(actor.getEmail(), actor.getRole().name(), "EVENT_RESCHEDULED_WITH_RESOURCE_MOVE",
                "eventId=11;reservationId=19;start=2030-05-02T10:00;end=2030-05-02T12:00");
    }

    @Test void aConcurrentConflictingBookingPreventsBothReservationAndEventChanges() {
        when(bookings.findConflictingBookingsExcept(eq(3L), any(), any(), eq(19L))).thenReturn(List.of(mock(FacilityBooking.class)));
        assertThrows(IllegalStateException.class, () -> service.reschedule(11L, newStart, "Weather disruption"));
        verify(bookings, never()).saveAndFlush(any());
        verify(eventService, never()).postponeEvent(anyLong(), any(), anyString(), any());
    }

    @Test void missingOrAmbiguousLinkedReservationFailsClosed() {
        when(bookings.findActiveByEventIdOrderByStartTimeAsc(11L)).thenReturn(List.of());
        assertThrows(IllegalStateException.class, () -> service.reschedule(11L, newStart, "Weather disruption"));
        verify(eventService, never()).postponeEvent(anyLong(), any(), anyString(), any());
    }

    @Test void cancellationReleasesTheSingleActiveReservationThenNotifiesThroughEventLifecycle() {
        service.cancel(11L);
        verify(reservation).setStatus("CANCELLED");
        verify(reservation).setReviewedBy(actor);
        verify(bookings).saveAndFlush(reservation);
        verify(eventService).cancelEvent(11L, actor);
        verify(audit).logAction(actor.getEmail(), actor.getRole().name(), "EVENT_CANCELLED_WITH_RESOURCE_RELEASE", "eventId=11");
    }

    @Test void cancellationWithAmbiguousReservationsFailsWithoutCancellingEitherResourceOrEvent() {
        var another = mock(FacilityBooking.class);
        when(bookings.findActiveByEventIdOrderByStartTimeAsc(11L)).thenReturn(List.of(reservation, another));
        assertThrows(IllegalStateException.class, () -> service.cancel(11L));
        verify(bookings, never()).saveAndFlush(any());
        verify(eventService, never()).cancelEvent(anyLong(), any());
    }

    @Test void repeatedCancelWithTheSameRequestKeyReplaysWithoutRepeatingSideEffects() {
        var receipts = mock(EventLifecycleCommandReceiptRepository.class);
        ReflectionTestUtils.setField(service, "lifecycleReceipts", receipts);
        var key = "11111111-1111-4111-8111-111111111111";
        when(receipts.findByEventIdAndRequestKey(11L, key)).thenReturn(Optional.empty());

        service.cancel(11L, key);
        var saved = ArgumentCaptor.forClass(EventLifecycleCommandReceipt.class);
        verify(receipts).saveAndFlush(saved.capture());
        clearInvocations(eventService, bookings, audit);
        when(receipts.findByEventIdAndRequestKey(11L, key)).thenReturn(Optional.of(saved.getValue()));

        service.cancel(11L, key);

        verify(eventService, never()).cancelEvent(anyLong(), any());
        verify(bookings, never()).saveAndFlush(any());
        verify(audit, never()).logAction(anyString(), anyString(), anyString(), anyString());
    }

    @Test void requestKeyReuseWithChangedRescheduleDetailsFailsClosed() {
        var receipts = mock(EventLifecycleCommandReceiptRepository.class);
        ReflectionTestUtils.setField(service, "lifecycleReceipts", receipts);
        var key = "33333333-3333-4333-8333-333333333333";
        when(receipts.findByEventIdAndRequestKey(11L, key)).thenReturn(Optional.empty());

        service.reschedule(11L, newStart, "Weather disruption", key);
        var saved = ArgumentCaptor.forClass(EventLifecycleCommandReceipt.class);
        verify(receipts).saveAndFlush(saved.capture());
        clearInvocations(bookings, eventService, audit);
        when(receipts.findByEventIdAndRequestKey(11L, key)).thenReturn(Optional.of(saved.getValue()));

        assertThrows(IllegalArgumentException.class,
                () -> service.reschedule(11L, newStart.plusDays(1), "Different reason", key));
        verify(bookings, never()).saveAndFlush(any());
        verify(eventService, never()).postponeEvent(anyLong(), any(), anyString(), any());
    }

    @Test void staleScheduleRevisionIsRejectedBeforeVenueMutation() {
        var history = mock(EventScheduleChangeRepository.class);
        ReflectionTestUtils.setField(service, "scheduleChanges", history);
        when(history.findTopByEventIdOrderByChangeNumberDesc(11L))
                .thenReturn(Optional.of(EventScheduleChange.builder().changeNumber(2L).build()));

        assertThrows(IllegalStateException.class,
                () -> service.reschedule(11L, newStart, "Weather disruption", "44444444-4444-4444-8444-444444444444", 1L));
        verify(bookings, never()).saveAndFlush(any());
        verify(eventService, never()).postponeEvent(anyLong(), any(), anyString(), any());
    }
}

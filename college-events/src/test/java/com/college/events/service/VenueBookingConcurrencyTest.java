package com.college.events.service;

import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never",
        "logging.file.name=target/test-logs/venue-test.log"}, showSql = false)
@Import({CUJammuMasterService.class, EventScheduleService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class VenueBookingConcurrencyTest {
    @Autowired CUJammuMasterService service;
    @Autowired FacilityRepository facilities;
    @Autowired FacilityBookingRepository bookings;
    @Autowired UserRepository users;
    @Autowired EventRepository events;
    @Autowired EventOperationsRepository eventOperations;
    @Autowired EventScheduleService schedules;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean DomainAccess access;
    @MockitoBean EventService eventService;
    @MockitoBean AuditService audit;
    @MockitoBean FeatureFlagService featureFlags;
    @MockitoBean NotificationService notifications;

    @Test void racingBookingsHaveOneWinnerAndForgedPriorityCannotDisplaceIt() throws Exception {
        var tx = new TransactionTemplate(transactions);
        User organizer = tx.execute(status -> users.saveAndFlush(User.builder().name("Synthetic organizer")
                .email("venue@example.invalid").password("hash").role(Role.ORGANIZER).enabled(true).build()));
        Facility facility = tx.execute(status -> facilities.saveAndFlush(Facility.builder()
                .name("Synthetic auditorium").facilityType("AUDITORIUM").active(true).build()));
        when(access.requireRole(any(Role[].class))).thenReturn(organizer);
        LocalDateTime start = LocalDateTime.of(2030, 1, 1, 10, 0);
        int workers = 6;
        var pool = Executors.newFixedThreadPool(workers);
        var ready = new CountDownLatch(workers);
        var go = new CountDownLatch(1);
        try {
            var results = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < workers; i++) {
                results.add(pool.submit(() -> {
                    ready.countDown(); assertTrue(go.await(10, TimeUnit.SECONDS));
                    try {
                        service.bookFacility(facility, null, organizer, "Private event", start, start.plusHours(1), 999, 1, false);
                        return true;
                    } catch (IllegalStateException conflict) {
                        assertFalse(conflict.getMessage().contains("Private event"));
                        return false;
                    }
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS)); go.countDown();
            int accepted = 0;
            for (var result : results) if (result.get(30, TimeUnit.SECONDS)) accepted++;
            assertEquals(1, accepted);
            var persisted = bookings.findConflictingBookings(facility.getId(), start, start.plusHours(1));
            assertEquals(1, persisted.size());
            assertEquals(10, persisted.get(0).getPriorityLevel());
            assertEquals("PENDING", persisted.get(0).getStatus());
            assertThrows(IllegalStateException.class, () -> service.bookFacility(facility, null, organizer,
                    "Attack", start, start.plusHours(1), Integer.MAX_VALUE, 1, false));
            // Half-open intervals allow adjacent bookings without configured buffers.
            service.bookFacility(facility, null, organizer, "Adjacent event", start.plusHours(1), start.plusHours(2), 10, 1, false);
            assertEquals(2, bookings.findConflictingBookings(facility.getId(), start, start.plusHours(2)).size());
        } finally { go.countDown(); pool.shutdownNow(); }
    }

    @Test void identicalBookingRetryReturnsOriginalAndChangedBodyCannotReuseKey() {
        User organizer = users.saveAndFlush(User.builder().name("Synthetic retry organizer")
                .email("venue-retry@example.invalid").password("hash").role(Role.ORGANIZER).enabled(true).build());
        Facility facility = facilities.saveAndFlush(Facility.builder()
                .name("Synthetic retry auditorium").facilityType("AUDITORIUM").active(true).build());
        when(access.requireRole(any(Role[].class))).thenReturn(organizer);
        LocalDateTime start = LocalDateTime.of(2031, 1, 1, 10, 0);
        String requestKey = "3f241fdf-8a72-4d10-9c37-9a2a8b7ce621";

        FacilityBooking first = service.bookFacility(facility, null, organizer, "Synthetic seminar",
                start, start.plusHours(1), 1, false, requestKey);
        FacilityBooking replay = service.bookFacility(facility, null, organizer, "Synthetic seminar",
                start, start.plusHours(1), 1, false, requestKey);

        assertEquals(first.getId(), replay.getId());
        assertEquals(1, bookings.findConflictingBookings(facility.getId(), start, start.plusHours(1)).size());
        assertThrows(IllegalStateException.class, () -> service.bookFacility(facility, null, organizer,
                "Changed title", start, start.plusHours(1), 1, false, requestKey));
    }

    @Test void reschedulingMovesLinkedReservationAndEventTogetherAndRejectsFreshOverlap() {
        var tx = new TransactionTemplate(transactions);
        User organizer = tx.execute(status -> users.saveAndFlush(User.builder().name("Synthetic event owner")
                .email("schedule-owner@example.invalid").password("hash").role(Role.ORGANIZER).enabled(true).build()));
        Facility facility = tx.execute(status -> facilities.saveAndFlush(Facility.builder()
                .name("Synthetic schedule hall").facilityType("AUDITORIUM").active(true).build()));
        Event event = tx.execute(status -> events.saveAndFlush(Event.builder().title("Synthetic event")
                .description("Test only").organizer(organizer).venue(facility.getName())
                .eventDate(LocalDateTime.of(2032, 2, 1, 10, 0)).maxSeats(20).status(EventStatus.APPROVED).build()));
        tx.executeWithoutResult(status -> eventOperations.saveAndFlush(EventOperations.builder().event(event).build()));
        LocalDateTime originalStart = LocalDateTime.of(2032, 2, 1, 10, 0);
        FacilityBooking linked = tx.execute(status -> bookings.saveAndFlush(FacilityBooking.builder().facility(facility)
                .event(event).bookedBy(organizer).title("Synthetic event").startTime(originalStart)
                .endTime(originalStart.plusHours(2)).status("CONFIRMED").build()));
        when(access.requireEventManager(event.getId())).thenReturn(event);
        when(access.currentUser()).thenReturn(organizer);
        LocalDateTime newStart = LocalDateTime.of(2032, 2, 2, 10, 0);

        new TransactionTemplate(transactions).executeWithoutResult(status -> schedules.reschedule(event.getId(), newStart, "Synthetic weather reason"));
        FacilityBooking moved = bookings.findById(linked.getId()).orElseThrow();
        assertEquals(newStart, moved.getStartTime());
        assertEquals(newStart.plusHours(2), moved.getEndTime());
        verify(eventService).postponeEvent(event.getId(), newStart, "Synthetic weather reason", organizer);

        FacilityBooking obstacle = tx.execute(status -> bookings.saveAndFlush(FacilityBooking.builder().facility(facility)
                .bookedBy(organizer).title("Synthetic conflicting booking").startTime(newStart.plusDays(1))
                .endTime(newStart.plusDays(1).plusHours(2)).status("CONFIRMED").build()));
        LocalDateTime conflict = newStart.plusDays(1);
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactions)
                .executeWithoutResult(status -> schedules.reschedule(event.getId(), conflict, "Must conflict")));
        assertEquals(newStart, bookings.findById(linked.getId()).orElseThrow().getStartTime(), "conflict must roll back reservation movement");
        assertEquals(conflict, bookings.findById(obstacle.getId()).orElseThrow().getStartTime());
        verify(eventService, never()).postponeEvent(event.getId(), conflict, "Must conflict", organizer);
    }

    @Test void cancellingEventReleasesLinkedReservationForAnotherBooking() {
        var tx = new TransactionTemplate(transactions);
        User organizer = tx.execute(status -> users.saveAndFlush(User.builder().name("Synthetic cancel owner")
                .email("cancel-owner@example.invalid").password("hash").role(Role.ORGANIZER).enabled(true).build()));
        Facility facility = tx.execute(status -> facilities.saveAndFlush(Facility.builder()
                .name("Synthetic cancel hall").facilityType("AUDITORIUM").active(true).build()));
        Event event = tx.execute(status -> events.saveAndFlush(Event.builder().title("Synthetic cancellable event")
                .description("Test only").organizer(organizer).venue(facility.getName())
                .eventDate(LocalDateTime.of(2033, 2, 1, 10, 0)).maxSeats(20).status(EventStatus.APPROVED).build()));
        tx.executeWithoutResult(status -> eventOperations.saveAndFlush(EventOperations.builder().event(event).build()));
        LocalDateTime start = LocalDateTime.of(2033, 2, 1, 10, 0);
        FacilityBooking linked = tx.execute(status -> bookings.saveAndFlush(FacilityBooking.builder().facility(facility)
                .event(event).bookedBy(organizer).title("Synthetic cancellable event").startTime(start)
                .endTime(start.plusHours(1)).status("CONFIRMED").build()));
        when(access.requireEventManager(event.getId())).thenReturn(event);
        when(access.currentUser()).thenReturn(organizer);

        new TransactionTemplate(transactions).executeWithoutResult(status -> schedules.cancel(event.getId()));
        FacilityBooking released = bookings.findById(linked.getId()).orElseThrow();
        assertEquals("CANCELLED", released.getStatus());
        assertTrue(bookings.findConflictingBookings(facility.getId(), start, start.plusHours(1)).isEmpty());
        verify(eventService).cancelEvent(event.getId(), organizer);
    }
}

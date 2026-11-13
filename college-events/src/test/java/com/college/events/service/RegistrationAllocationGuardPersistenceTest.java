package com.college.events.service;

import com.college.events.model.*;
import com.college.events.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never",
        "logging.file.name=target/test-logs/registration-allocation.log"}, showSql=false)
@Import(RegistrationService.class)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class RegistrationAllocationGuardPersistenceTest {
    @Autowired RegistrationService service;
    @Autowired UserRepository users;
    @Autowired EventRepository events;
    @Autowired EventOperationsRepository operations;
    @Autowired RegistrationRepository registrations;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean FeatureFlagService features;
    @MockitoBean EmailService email;
    @MockitoBean NotificationService notifications;
    @MockitoBean AuditService audit;

    private User user(Role role) {
        return users.saveAndFlush(User.builder().name("Synthetic allocation actor")
                .email(UUID.randomUUID()+"@example.invalid").password("synthetic-hash")
                .role(role).enabled(true).build());
    }
    private Event event(boolean guarded) {
        Event row=events.saveAndFlush(Event.builder().organizer(user(Role.ORGANIZER))
                .title("Synthetic last-seat event").description("Synthetic guard regression")
                .venue("Synthetic venue").eventDate(LocalDateTime.now().plusDays(5).truncatedTo(java.time.temporal.ChronoUnit.MICROS))
                .registrationDeadline(LocalDateTime.now().plusDays(2)).status(EventStatus.APPROVED)
                .maxSeats(1).build());
        if(guarded) operations.saveAndFlush(EventOperations.builder().event(row).build());
        return row;
    }
    private EventService lifecycle() {
        var result=new EventService(events,mock(CategoryRepository.class),mock(FileStorageService.class),email,
                registrations,users,mock(EventOperationsService.class),notifications,operations);
        ReflectionTestUtils.setField(result,"completionFlags",features);
        return result;
    }

    @Test void missingControlRowCannotAllocateOrProduceSideEffects() {
        var row=event(false); var student=user(Role.STUDENT);
        assertThrows(IllegalStateException.class,()->service.register(student,row.getId()));
        assertTrue(registrations.findByUserAndEvent(student,row).isEmpty());
        verifyNoInteractions(email,notifications,audit);
    }

    @Test void cancellationWithoutControlRowLeavesPersistedEventUnchanged() {
        var row=event(false); var owner=row.getOrganizer();
        assertThrows(IllegalStateException.class,()->new TransactionTemplate(transactions).execute(status->{
            lifecycle().cancelEvent(row.getId(),owner); return null;
        }));
        assertEquals(EventStatus.APPROVED,events.findById(row.getId()).orElseThrow().getStatus());
        verifyNoInteractions(email,notifications,audit);
    }

    @Test void postponementWithoutControlRowLeavesDateAndNotificationsUnchanged() {
        var row=event(false); var original=row.getEventDate(); var owner=row.getOrganizer();
        assertThrows(IllegalStateException.class,()->new TransactionTemplate(transactions).execute(status->{
            lifecycle().postponeEvent(row.getId(),original.plusDays(1),"Synthetic disruption",owner); return null;
        }));
        assertEquals(original,events.findById(row.getId()).orElseThrow().getEventDate());
        verifyNoInteractions(email,notifications,audit);
    }

    @Test void simultaneousLastSeatAllocationPersistsExactlyOneConfirmedRegistration() throws Exception {
        var row=event(true); var entrants=List.of(user(Role.STUDENT),user(Role.STUDENT),user(Role.STUDENT));
        var pool=Executors.newFixedThreadPool(3); var ready=new CountDownLatch(3); var go=new CountDownLatch(1);
        try {
            var results=entrants.stream().map(entrant->pool.submit(()->{
                ready.countDown(); assertTrue(go.await(10,TimeUnit.SECONDS));
                return service.register(entrant,row.getId()).getStatus();
            })).toList();
            assertTrue(ready.await(10,TimeUnit.SECONDS)); go.countDown();
            var states=new java.util.ArrayList<RegistrationStatus>();
            for(var result:results) states.add(result.get(20,TimeUnit.SECONDS));
            assertEquals(1,states.stream().filter(s->s==RegistrationStatus.REGISTERED).count());
            assertEquals(2,states.stream().filter(s->s==RegistrationStatus.WAITLISTED).count());
            assertEquals(1,registrations.countConfirmedByEvent(row));
            assertEquals(2,registrations.countWaitlistedByEvent(row));
            assertThrows(RuntimeException.class,()->service.register(entrants.get(0),row.getId()));
            assertEquals(3,registrations.findByEventOrderByRegisteredAtAsc(row).size());
            verify(email,times(1)).sendRegistrationConfirmation(any());
            verify(email,times(2)).sendWaitlistConfirmation(any());
        } finally {go.countDown();pool.shutdownNow();}
    }

    @Test void cancellationOfClosedEventNeverPromotesWaitlistedRegistration() {
        var row=event(true);
        row.setStatus(EventStatus.CANCELLED);
        events.saveAndFlush(row);
        var attendee=user(Role.STUDENT); var waiting=user(Role.STUDENT);
        var confirmed=registrations.saveAndFlush(Registration.builder().event(row).user(attendee)
                .status(RegistrationStatus.REGISTERED).build());
        var queued=registrations.saveAndFlush(Registration.builder().event(row).user(waiting)
                .status(RegistrationStatus.WAITLISTED).build());

        service.cancelRegistration(confirmed.getId(),attendee);

        assertEquals(RegistrationStatus.CANCELLED,registrations.findById(confirmed.getId()).orElseThrow().getStatus());
        assertEquals(RegistrationStatus.WAITLISTED,registrations.findById(queued.getId()).orElseThrow().getStatus());
        verify(email,never()).sendWaitlistPromotion(any());
        verify(notifications,never()).createNotification(eq(waiting),contains("seat opened"),anyString());
    }

    @Test void approvedEventCancellationPromotesExactlyTheOldestWaitlistedRegistration() {
        var row=event(true);
        var attendee=user(Role.STUDENT); var first=user(Role.STUDENT); var second=user(Role.STUDENT);
        var confirmed=registrations.saveAndFlush(Registration.builder().event(row).user(attendee)
                .status(RegistrationStatus.REGISTERED).build());
        var firstQueued=registrations.saveAndFlush(Registration.builder().event(row).user(first)
                .status(RegistrationStatus.WAITLISTED).build());
        var secondQueued=registrations.saveAndFlush(Registration.builder().event(row).user(second)
                .status(RegistrationStatus.WAITLISTED).build());
        // Prove that wall-clock timestamps do not reorder the serialized queue sequence.
        jdbc.update("update registrations set registered_at = ? where id = ?",
                LocalDateTime.now().minusDays(2), firstQueued.getId());
        jdbc.update("update registrations set registered_at = ? where id = ?",
                LocalDateTime.now().minusDays(3), secondQueued.getId());

        service.cancelRegistration(confirmed.getId(),attendee);

        assertEquals(RegistrationStatus.REGISTERED,registrations.findById(firstQueued.getId()).orElseThrow().getStatus());
        assertEquals(RegistrationStatus.WAITLISTED,registrations.findById(secondQueued.getId()).orElseThrow().getStatus());
        assertEquals(1,registrations.countConfirmedByEvent(row));
        verify(email,times(1)).sendWaitlistPromotion(argThat(r->r.getId().equals(firstQueued.getId())));
    }

    @Test void disabledWaitlistedAccountIsSkippedWhenPromotingNextEligibleStudent() {
        var row=event(true);
        var attendee=user(Role.STUDENT); var disabled=user(Role.STUDENT); var eligible=user(Role.STUDENT);
        disabled.setEnabled(false); users.saveAndFlush(disabled);
        var confirmed=registrations.saveAndFlush(Registration.builder().event(row).user(attendee)
                .status(RegistrationStatus.REGISTERED).build());
        var disabledQueued=registrations.saveAndFlush(Registration.builder().event(row).user(disabled)
                .status(RegistrationStatus.WAITLISTED).build());
        var eligibleQueued=registrations.saveAndFlush(Registration.builder().event(row).user(eligible)
                .status(RegistrationStatus.WAITLISTED).build());

        service.cancelRegistration(confirmed.getId(),attendee);

        assertEquals(RegistrationStatus.WAITLISTED,registrations.findById(disabledQueued.getId()).orElseThrow().getStatus());
        assertEquals(RegistrationStatus.REGISTERED,registrations.findById(eligibleQueued.getId()).orElseThrow().getStatus());
        verify(email,times(1)).sendWaitlistPromotion(argThat(r->r.getId().equals(eligibleQueued.getId())));
    }

    @Test void cancellationAfterEventStartDoesNotPromoteWaitlistedRegistration() {
        var row=event(true);
        row.setEventDate(LocalDateTime.now().minusMinutes(1));
        events.saveAndFlush(row);
        var attendee=user(Role.STUDENT); var waiting=user(Role.STUDENT);
        var confirmed=registrations.saveAndFlush(Registration.builder().event(row).user(attendee)
                .status(RegistrationStatus.REGISTERED).build());
        var queued=registrations.saveAndFlush(Registration.builder().event(row).user(waiting)
                .status(RegistrationStatus.WAITLISTED).build());

        service.cancelRegistration(confirmed.getId(),attendee);

        assertEquals(RegistrationStatus.WAITLISTED,registrations.findById(queued.getId()).orElseThrow().getStatus());
        verify(email,never()).sendWaitlistPromotion(any());
    }

    @Test void registrationDeadlineClosesDirectAdmissionWithoutSideEffects() {
        var row=event(true);
        row.setRegistrationDeadline(LocalDateTime.now().minusSeconds(1));
        events.saveAndFlush(row);
        var student=user(Role.STUDENT);

        assertThrows(IllegalStateException.class,()->service.register(student,row.getId()));

        assertTrue(registrations.findByUserAndEvent(student,row).isEmpty());
        verifyNoInteractions(email,notifications,audit);
    }

    @Test void registrationDeadlinePreventsLateAutomaticWaitlistPromotion() {
        var row=event(true);
        row.setRegistrationDeadline(LocalDateTime.now().minusSeconds(1));
        events.saveAndFlush(row);
        var attendee=user(Role.STUDENT); var waiting=user(Role.STUDENT);
        var confirmed=registrations.saveAndFlush(Registration.builder().event(row).user(attendee)
                .status(RegistrationStatus.REGISTERED).build());
        var queued=registrations.saveAndFlush(Registration.builder().event(row).user(waiting)
                .status(RegistrationStatus.WAITLISTED).build());

        service.cancelRegistration(confirmed.getId(),attendee);

        assertEquals(RegistrationStatus.WAITLISTED,registrations.findById(queued.getId()).orElseThrow().getStatus());
        verify(email,never()).sendWaitlistPromotion(any());
        verify(notifications,never()).createNotification(eq(waiting),contains("seat opened"),anyString());
    }

    @Test void admissionContendsWithCancellationAndCannotCommitAfterCancellation() throws Exception {
        var row=event(true); var student=user(Role.STUDENT); var owner=row.getOrganizer();
        var pool=Executors.newFixedThreadPool(2); var held=new CountDownLatch(1); var release=new CountDownLatch(1);
        var admissionStarted=new CountDownLatch(1);
        try {
            var cancellation=pool.submit(()->new TransactionTemplate(transactions).execute(status->{
                operations.findForUpdate(row.getId()).orElseThrow();
                lifecycle().cancelEvent(row.getId(),owner);
                held.countDown();
                try {assertTrue(release.await(10,TimeUnit.SECONDS));}
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
                return true;
            }));
            assertTrue(held.await(10,TimeUnit.SECONDS));
            var admission=pool.submit(()->{
                admissionStarted.countDown();
                return assertThrows(RuntimeException.class,()->service.register(student,row.getId()));
            });
            assertTrue(admissionStarted.await(10,TimeUnit.SECONDS));
            release.countDown(); assertTrue(cancellation.get(20,TimeUnit.SECONDS));
            assertNotNull(admission.get(20,TimeUnit.SECONDS));
            assertEquals(EventStatus.CANCELLED,events.findById(row.getId()).orElseThrow().getStatus());
            assertTrue(registrations.findByUserAndEvent(student,row).isEmpty());
            verify(email,never()).sendRegistrationConfirmation(any());
        } finally {release.countDown();pool.shutdownNow();}
    }
}

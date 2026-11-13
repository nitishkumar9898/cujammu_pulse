package com.college.events.service;

import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never",
        "logging.file.name=target/test-logs/feedback-privacy.log"}, showSql = false)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FeedbackPrivacyPersistenceTest {
    @Autowired FeedbackRepository feedback;
    @Autowired EventRepository events;
    @Autowired RegistrationRepository registrations;
    @Autowired UserRepository users;
    @Autowired PlatformTransactionManager manager;
    FeedbackService service;
    TransactionTemplate tx;
    User owner, participant, stranger;
    Event event;
    @BeforeEach void setup() {
        tx = new TransactionTemplate(manager);
        service = new FeedbackService(feedback, events, mock(FeatureFlagService.class),
                new DomainAccess(users, events), registrations, mock(AuditService.class));
        owner = user(Role.ORGANIZER); participant = user(Role.STUDENT); stranger = user(Role.STUDENT);
        LocalDateTime now = LocalDateTime.now();
        event = events.saveAndFlush(Event.builder().title("Synthetic review event").description("Synthetic only")
                .organizer(owner).venue("Synthetic hall").maxSeats(20).status(EventStatus.COMPLETED)
                .eventDate(now.minusHours(2)).approvedAt(now.minusDays(1)).completedAt(now.minusMinutes(1))
                .completedByUserId(owner.getId()).build());
        registrations.saveAndFlush(Registration.builder().event(event).user(participant).status(RegistrationStatus.REGISTERED).build());
    }
    User user(Role role) {
        return users.saveAndFlush(User.builder().name("Synthetic " + role).email(UUID.randomUUID()+"@example.invalid")
                .verifiedEnrollmentNo("000"+UUID.randomUUID()).password("synthetic-hash").enabled(true).role(role).build());
    }
    void as(User user) { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,user.getAuthorities())); }
    Long submit(User actor, int rating, String text) {
        as(actor); return tx.execute(s -> service.submitFeedback(actor,event.getId(),rating,text).getId());
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void identicalRetryIsStableChangedChoiceRejectedAndOwnerReadPrivate() {
        as(participant);
        Feedback first = tx.execute(s -> service.submitFeedback(participant,event.getId(),4," Private synthetic comment "));
        long id = first.getId();
        assertEquals(id,submit(participant,4,"Private synthetic comment"));
        assertEquals(first.getCreatedAt(),tx.execute(s -> service.submitFeedback(participant,event.getId(),4,"Private synthetic comment").getCreatedAt()));
        assertThrows(IllegalStateException.class,()->submit(participant,3,"Private synthetic comment"));
        assertEquals(1,feedback.countByEvent(event));
        as(participant);
        assertEquals(id,tx.execute(s->service.own(event.getId())).orElseThrow().id());
        assertThrows(AccessDeniedException.class,()->tx.execute(s->service.summary(event.getId())));
        as(stranger);
        assertTrue(tx.execute(s->service.own(event.getId())).isEmpty());
        assertThrows(AccessDeniedException.class,()->tx.execute(s->service.summary(event.getId())));
        assertThrows(AccessDeniedException.class,()->tx.execute(s->service.getAverageRating(event)));
        as(owner);
        var summary = tx.execute(s->service.summary(event.getId()));
        assertEquals(1,summary.totalResponses()); assertEquals(4,summary.averageRating());
        assertEquals("Private synthetic comment",summary.recent().getFirst().comment());
    }

    @Test void outsiderUnverifiedCancelledAndUncompletedAreDenied() {
        assertThrows(AccessDeniedException.class,()->submit(stranger,5,"Synthetic"));
        tx.executeWithoutResult(s->{User u=users.findById(participant.getId()).orElseThrow();u.setVerifiedEnrollmentNo(null);users.saveAndFlush(u);});
        assertThrows(AccessDeniedException.class,()->submit(participant,5,"Synthetic"));
        tx.executeWithoutResult(s->{User u=users.findById(participant.getId()).orElseThrow();u.setVerifiedEnrollmentNo("000001");users.saveAndFlush(u);
            Registration r=registrations.findByUserAndEvent(u,event).orElseThrow();r.setStatus(RegistrationStatus.CANCELLED);registrations.saveAndFlush(r);});
        assertThrows(AccessDeniedException.class,()->submit(participant,5,"Synthetic"));
        tx.executeWithoutResult(s->{Registration r=registrations.findByUserAndEvent(participant,event).orElseThrow();r.setStatus(RegistrationStatus.REGISTERED);registrations.saveAndFlush(r);
            Event e=events.findForApproval(event.getId()).orElseThrow();e.setCompletedAt(null);events.saveAndFlush(e);});
        assertThrows(IllegalStateException.class,()->submit(participant,5,"Synthetic"));
        assertEquals(0,feedback.countByEvent(event));
    }

    @Test void freshActorRevocationAndSuppliedIdentityCannotBypass() {
        as(stranger);
        assertThrows(AccessDeniedException.class,()->tx.execute(s->service.submitFeedback(participant,event.getId(),4,"Synthetic")));
        tx.executeWithoutResult(s->{User u=users.findById(participant.getId()).orElseThrow();u.setEnabled(false);users.saveAndFlush(u);});
        assertThrows(AccessDeniedException.class,()->submit(participant,4,"Synthetic"));
    }

    @Test void simultaneousRetriesCommitOneReceipt() throws Exception {
        CountDownLatch ready=new CountDownLatch(2), start=new CountDownLatch(1);
        try (ExecutorService pool=Executors.newFixedThreadPool(2)) {
            Callable<Long> command=()->{as(participant);ready.countDown();assertTrue(start.await(10,TimeUnit.SECONDS));
                try{return tx.execute(s->service.submitFeedback(participant,event.getId(),5,"Synthetic concurrent").getId());}
                finally{SecurityContextHolder.clearContext();}};
            Future<Long> first=pool.submit(command), second=pool.submit(command);
            assertTrue(ready.await(10,TimeUnit.SECONDS));start.countDown();
            assertEquals(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS));
            assertEquals(1,feedback.countByEvent(event));
        }
    }

    @Test void directInvalidRatingsAreRejectedBeforePersistence() {
        assertThrows(IllegalArgumentException.class,()->submit(participant,0,"Synthetic"));
        assertThrows(IllegalArgumentException.class,()->submit(participant,6,"Synthetic"));
        assertThrows(IllegalArgumentException.class,()->submit(participant,5,"x".repeat(2001)));
        assertEquals(0,feedback.countByEvent(event));
    }
}

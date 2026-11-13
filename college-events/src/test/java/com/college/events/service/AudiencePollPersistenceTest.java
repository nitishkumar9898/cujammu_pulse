package com.college.events.service;

import com.college.events.dto.PollDtos;
import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Synthetic real database transaction tests: no mocked vote counts, identity or locking queries. */
@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never",
        "logging.file.name=target/test-logs/audience-polls.log"}, showSql=false)
@Import({DomainAccess.class, AuditService.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class AudiencePollPersistenceTest {
    @Autowired EventPollRepository polls;
    @Autowired PollVoteRepository votes;
    @Autowired EventRepository events;
    @Autowired RegistrationRepository registrations;
    @Autowired EventOperationsRepository operations;
    @Autowired UserRepository users;
    @Autowired DomainAccess access;
    @Autowired AuditService audit;
    @Autowired AuditLogRepository auditLogs;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean FeatureFlagService features;
    private User organizer, student, other, stranger, admin;
    private Event event;
    private AudiencePollService service;
    private final AtomicReference<Instant> now = new AtomicReference<>();

    @BeforeEach void setup() {
        now.set(Instant.parse("2030-01-01T06:30:00Z"));
        organizer=user(Role.FACULTY); student=user(Role.STUDENT); other=user(Role.STUDENT);
        stranger=user(Role.STUDENT); admin=user(Role.ADMIN);
        event=events.saveAndFlush(Event.builder().title("Synthetic audience event").description("Synthetic")
                .venue("Synthetic venue").eventDate(LocalDateTime.of(2030,1,2,12,0)).maxSeats(20)
                .organizer(organizer).status(EventStatus.APPROVED).build());
        operations.saveAndFlush(EventOperations.builder().event(event).build());
        register(student); register(other);
        when(features.isEnabled("MODULE_EVENTS")).thenReturn(true);
        when(features.isEnabled("MODULE_POLLS")).thenReturn(true);
        Clock clock=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}
            public Clock withZone(ZoneId zone){return Clock.fixed(instant(),zone);}
            public Instant instant(){return now.get();}};
        service=new AudiencePollService(polls,votes,events,registrations,operations,access,features,audit,clock);
        as(organizer);
    }
    @AfterEach void clearActor(){SecurityContextHolder.clearContext();}
    private User user(Role role){return users.saveAndFlush(User.builder().name("Synthetic subject")
            .email(UUID.randomUUID()+"@example.invalid").password("synthetic-hash").enabled(true).role(role).build());}
    private void register(User user){registrations.saveAndFlush(Registration.builder().event(event).user(user)
            .status(RegistrationStatus.REGISTERED).build());}
    private void as(User user){SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user,null,user.getAuthorities()));}
    private <T>T tx(Supplier<T> action){return new TransactionTemplate(transactions).execute(status->action.get());}
    private PollDtos.Create command(String key){return new PollDtos.Create("Synthetic audience choice",
            List.of("Team Alpha","Team Beta"),10,key);}
    private PollDtos.PollView draft(){as(organizer);return tx(()->service.createPoll(event.getId(),command(UUID.randomUUID().toString())));}
    private PollDtos.PollView open(){var draft=draft();return tx(()->service.publish(draft.id(),new PollDtos.Revise(draft.revision())));}
    private PollDtos.PollView vote(long id,String choice){return tx(()->service.castVote(id,new PollDtos.Vote(choice)));}
    private PollDtos.PollView result(long id){return tx(()->service.getPollResults(id));}

    @Test void creationRetriesBindCanonicalDefinitionAndCurrentManagerScope() {
        String key=UUID.randomUUID().toString();var command=command(key);
        var first=tx(()->service.createPoll(event.getId(),command));
        assertEquals(first,tx(()->service.createPoll(event.getId(),command)));
        assertThrows(IllegalStateException.class,()->tx(()->service.createPoll(event.getId(),
                new PollDtos.Create("Changed",command.options(),10,key))));
        as(student);assertThrows(AccessDeniedException.class,()->tx(()->service.createPoll(event.getId(),command)));
        as(user(Role.FACULTY));assertThrows(AccessDeniedException.class,()->tx(()->service.createPoll(event.getId(),command)));
        assertEquals(1,polls.findByEventId(event.getId()).size());
    }

    @Test void draftsRemainPrivateAndOnlyScopedManagersPublishWithCurrentRevision() {
        var draft=draft();as(student);
        assertThrows(AccessDeniedException.class,()->result(draft.id()));
        assertThrows(AccessDeniedException.class,()->tx(()->service.publish(draft.id(),new PollDtos.Revise(draft.revision()))));
        as(organizer);assertThrows(IllegalStateException.class,()->tx(()->service.publish(draft.id(),new PollDtos.Revise(draft.revision()+99))));
        as(admin);var published=tx(()->service.publish(draft.id(),new PollDtos.Revise(draft.revision())));
        assertEquals("OPEN",published.state());as(stranger);assertThrows(AccessDeniedException.class,()->result(draft.id()));
    }

    @Test void immutableOwnVoteReceiptAndAggregateNeverPersistNetworkOrDisplayIdentity() {
        var poll=open();as(student);var first=vote(poll.id(),"Team Alpha");
        assertEquals(first,vote(poll.id(),"Team Alpha"));
        assertThrows(IllegalStateException.class,()->vote(poll.id(),"Team Beta"));
        as(other);vote(poll.id(),"Team Beta");assertEquals(2,votes.countByPollId(poll.id()));
        for(var row:votes.findByPollId(poll.id())){assertNull(row.getVoterIp());assertNull(row.getVoterName());}
        as(stranger);assertThrows(AccessDeniedException.class,()->vote(poll.id(),"Team Alpha"));
        as(organizer);assertThrows(AccessDeniedException.class,()->vote(poll.id(),"Team Alpha"));
    }

    @Test void exactUtcDeadlineRejectsNewVotesButPreservesAuthorizedOriginalReceipt() {
        var poll=open();as(student);var first=vote(poll.id(),"Team Alpha");
        now.set(now.get().plusSeconds(600));as(other);
        assertThrows(IllegalStateException.class,()->vote(poll.id(),"Team Beta"));
        as(student);assertEquals(first.id(),vote(poll.id(),"Team Alpha").id());
        assertEquals(1,votes.countByPollId(poll.id()));
    }

    @Test void expiredPollIsDurablyClosedInBoundedReconciliationAndAuditedEvenWhenModuleIsDisabled() throws Exception {
        var poll=open();
        assertTrue(polls.findExpiredOpenPollIds(now.get(),org.springframework.data.domain.PageRequest.of(0,100)).isEmpty());
        now.set(poll.closesAt());
        assertTrue(polls.findExpiredOpenPollIds(now.get(),org.springframework.data.domain.PageRequest.of(0,100))
                .contains(poll.id()));
        doThrow(new IllegalStateException("Poll module disabled")).when(features).requireEnabledForUpdate("MODULE_POLLS");

        List<Boolean> closeRace=race(()->tx(()->service.closeExpiredPoll(poll.id())),
                ()->tx(()->service.closeExpiredPoll(poll.id())));
        assertEquals(1,closeRace.stream().filter(Boolean::booleanValue).count());
        EventPoll closed=polls.findById(poll.id()).orElseThrow();
        assertEquals("CLOSED",closed.getState());assertFalse(closed.isActive());
        assertEquals(poll.closesAt(),closed.getClosesAt());
        assertFalse(tx(()->service.closeExpiredPoll(poll.id())));
        assertFalse(polls.findExpiredOpenPollIds(now.get(),org.springframework.data.domain.PageRequest.of(0,100))
                .contains(poll.id()));
        assertEquals(1,auditLogs.findAll().stream().filter(row->"POLL_AUTO_CLOSED".equals(row.getAction())).count());
    }

    @Test void lastInstantBeforeDeadlineIsAccepted() {
        var poll=open();now.set(now.get().plusSeconds(599).plusNanos(999_000_000));as(student);
        vote(poll.id(),"Team Alpha");assertEquals(1,votes.countByPollId(poll.id()));
    }

    @Test void revocationAndRuntimeDisableApplyBeforeOriginalReceiptReplay() {
        var poll=open();as(student);vote(poll.id(),"Team Alpha");
        var registration=registrations.findByUserAndEvent(student,event).orElseThrow();
        registration.setStatus(RegistrationStatus.CANCELLED);registrations.saveAndFlush(registration);
        assertThrows(AccessDeniedException.class,()->vote(poll.id(),"Team Alpha"));
        registration.setStatus(RegistrationStatus.REGISTERED);registrations.saveAndFlush(registration);
        doThrow(new IllegalStateException("Disabled")).when(features).requireEnabledForUpdate("MODULE_POLLS");
        assertThrows(IllegalStateException.class,()->vote(poll.id(),"Team Alpha"));
        doNothing().when(features).requireEnabledForUpdate("MODULE_POLLS");
        var disabled=users.findById(student.getId()).orElseThrow();disabled.setEnabled(false);users.saveAndFlush(disabled);
        assertThrows(AccessDeniedException.class,()->vote(poll.id(),"Team Alpha"));assertEquals(1,votes.countByPollId(poll.id()));
    }

    @Test void terminalEventAndClosedPollRejectNewVotesButPreserveExistingEvidence() {
        var poll=open();as(student);vote(poll.id(),"Team Alpha");
        as(organizer);var current=result(poll.id());tx(()->service.close(poll.id(),new PollDtos.Revise(current.revision())));
        as(student);assertEquals(poll.id(),vote(poll.id(),"Team Alpha").id());
        as(other);assertThrows(IllegalStateException.class,()->vote(poll.id(),"Team Beta"));
        var second=open();event.setStatus(EventStatus.CANCELLED);events.saveAndFlush(event);as(student);
        assertThrows(IllegalStateException.class,()->vote(second.id(),"Team Alpha"));
        assertEquals(0,votes.countByPollId(second.id()));
    }

    @Test void concurrentIdenticalVotesConvergeAndConflictingChoicesHaveOneWinner() throws Exception {
        var first=open();var same=race(()->vote(first.id(),"Team Alpha").id(),()->vote(first.id(),"Team Alpha").id());
        assertEquals(same.get(0),same.get(1));assertEquals(1,votes.countByPollId(first.id()));
        var second=open();var different=race(()->attempt(second.id(),"Team Alpha"),()->attempt(second.id(),"Team Beta"));
        assertNotEquals(different.get(0),different.get(1));assertEquals(1,votes.countByPollId(second.id()));
    }

    @Test void simultaneousDifferentStudentsRemainEligibleWithoutNetworkUniqueness() throws Exception {
        var poll=open();var outcomes=race(()->attempt(poll.id(),"Team Alpha"),()->{as(other);return attempt(poll.id(),"Team Alpha");});
        assertEquals(List.of(true,true),outcomes);assertEquals(2,votes.countByPollId(poll.id()));
    }

    @Test void committedAggregateCountsMatchOwnChoiceWithoutExposingAnotherVoter() {
        var poll=open();as(student);var empty=result(poll.id());
        assertEquals(0,empty.totalVotes());assertNull(empty.myChoice());
        assertTrue(empty.results().stream().allMatch(row->row.votes()==0 && row.percentage()==0));
        vote(poll.id(),"Team Alpha");as(other);var before=result(poll.id());
        assertNull(before.myChoice());assertEquals(1,before.totalVotes());
        vote(poll.id(),"Team Beta");var third=user(Role.STUDENT);register(third);as(third);vote(poll.id(),"Team Alpha");
        as(student);var tally=result(poll.id());assertEquals("Team Alpha",tally.myChoice());assertEquals(3,tally.totalVotes());
        assertEquals(3,tally.results().stream().mapToLong(PollDtos.Result::votes).sum());
        assertEquals(66.7,tally.results().stream().filter(row->row.option().equals("Team Alpha")).findFirst().orElseThrow().percentage(),0.01);
        assertEquals(33.3,tally.results().stream().filter(row->row.option().equals("Team Beta")).findFirst().orElseThrow().percentage(),0.01);
    }

    @Test void databaseUniquenessRejectsDuplicateVoteEvenOutsideTheService() {
        var poll=open();as(student);vote(poll.id(),"Team Alpha");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->tx(()->votes.saveAndFlush(
                PollVote.builder().pollId(poll.id()).voterId(student.getId()).selectedOption("Team Beta").build())));
        assertEquals(1,votes.countByPollId(poll.id()));
    }

    @Test void concurrentCloseAndVoteProduceAClosedPollWithAtMostOneAcceptedVote() throws Exception {
        var poll=open();var outcome=race(()->attempt(poll.id(),"Team Alpha"),()->{as(organizer);
            tx(()->service.close(poll.id(),new PollDtos.Revise(poll.revision())));return true;});
        assertTrue(outcome.get(1));as(organizer);assertEquals("CLOSED",result(poll.id()).state());
        assertEquals(outcome.get(0)?1:0,votes.countByPollId(poll.id()));as(other);
        assertThrows(IllegalStateException.class,()->vote(poll.id(),"Team Alpha"));
    }

    @Test void malformedOptionsKeysAndUnknownChoicesFailWithoutPersistingVotes() {
        for(var options:List.of(List.of("Only"),List.of("Alpha","Alpha"),List.of("","Beta"),List.of("x".repeat(101),"Beta")))
            assertThrows(IllegalArgumentException.class,()->tx(()->service.createPoll(event.getId(),
                    new PollDtos.Create("Question",options,10,UUID.randomUUID().toString()))));
        assertThrows(IllegalArgumentException.class,()->tx(()->service.createPoll(event.getId(),
                new PollDtos.Create("Question",List.of("Alpha","Beta"),10,"bad-key"))));
        var poll=open();as(student);assertThrows(IllegalArgumentException.class,()->vote(poll.id(),"Unknown choice"));
        assertEquals(0,votes.countByPollId(poll.id()));
    }

    @Test void legacyDefinitionsRemainStoredButCannotAppearOrAcquireNewVotes() {
        var legacy=polls.saveAndFlush(EventPoll.builder().eventId(event.getId()).question("Synthetic legacy")
                .options("Alpha,Beta").active(true).build());
        as(student);assertTrue(tx(()->service.pollsForEvent(event.getId(),0)).items().isEmpty());
        assertThrows(IllegalStateException.class,()->vote(legacy.getId(),"Alpha"));
        assertTrue(polls.existsById(legacy.getId()));assertEquals(0,votes.countByPollId(legacy.getId()));
        assertThrows(IllegalArgumentException.class,()->tx(()->service.pollsForEvent(event.getId(),-1)));
    }

    private boolean attempt(long id,String option){try{vote(id,option);return true;}catch(IllegalStateException denied){return false;}}
    private <T>List<T> race(Callable<T> a,Callable<T> b) throws Exception {
        var executor=Executors.newFixedThreadPool(2);var ready=new CountDownLatch(2);var go=new CountDownLatch(1);
        try {java.util.function.Function<Callable<T>,Callable<T>> wrap=task->()->{as(student);ready.countDown();
            try{assertTrue(go.await(10,TimeUnit.SECONDS));return task.call();}finally{SecurityContextHolder.clearContext();}};
            var first=executor.submit(wrap.apply(a));var second=executor.submit(wrap.apply(b));
            assertTrue(ready.await(10,TimeUnit.SECONDS));go.countDown();return List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));
        }finally{go.countDown();executor.shutdownNow();}
    }
}

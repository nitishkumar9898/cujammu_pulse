package com.college.events.service;

import com.college.events.dto.CommunityChatDtos;
import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real database transactions and contention, isolated synthetic rows; no mocked quota/dedupe queries. */
@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never",
        "logging.file.name=target/test-logs/chat-posting.log"}, showSql=false)
@Import(DomainAccess.class)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class ChatPostingPersistenceTest {
    @Autowired ChatGroupRepository groups;
    @Autowired ChatMessageRepository messages;
    @Autowired ChatMembershipRepository members;
    @Autowired UserRepository users;
    @Autowired RegistrationRepository registrations;
    @Autowired EventRepository events;
    @Autowired EventOperationsRepository operations;
    @Autowired DomainAccess access;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean FeatureFlagService features;
    private User sender, other;
    private ChatGroup group;
    private CommunityChatService service;
    private final AtomicReference<Instant> now = new AtomicReference<>();

    @BeforeEach void setup() {
        now.set(Instant.parse("2030-01-01T06:30:00Z"));
        sender=user(); other=user(); group=department(); grant(group,sender); grant(group,other);
        when(features.isEnabled("MODULE_COMMUNITY_CHAT")).thenReturn(true);
        when(features.isEnabled("MODULE_EVENTS")).thenReturn(true);
        Clock clock=new Clock() {
            public ZoneId getZone(){return ZoneOffset.UTC;}
            public Clock withZone(ZoneId zone){return Clock.fixed(instant(),zone);}
            public Instant instant(){return now.get();}
        };
        service=new CommunityChatService(groups,messages,registrations,events,access,features,operations,members,
                new ChatPostingPolicy(2,3),clock);
        as(sender);
    }
    @AfterEach void clearActor(){SecurityContextHolder.clearContext();}
    private User user(){return users.saveAndFlush(User.builder().name("Synthetic sender")
            .email(UUID.randomUUID()+"@example.invalid").password("synthetic-hash").enabled(true).role(Role.STUDENT).build());}
    private ChatGroup department(){return groups.saveAndFlush(ChatGroup.builder().name("Synthetic private group")
            .groupType("PERMANENT_DEPARTMENT").relatedDepartment("Synthetic").nightSilenceEnabled(false).build());}
    private void grant(ChatGroup target,User user){var row=new ChatMembership();row.setGroupId(target.getId());
        row.setUser(user);row.setActive(true);members.saveAndFlush(row);}
    private void as(User user){SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user,null,user.getAuthorities()));}
    private String key(){return UUID.randomUUID().toString();}
    private CommunityChatDtos.Message send(String content,String key){return send(group,content,key);}
    private CommunityChatDtos.Message send(ChatGroup target,String content,String key){return new TransactionTemplate(transactions)
            .execute(status->service.postMessage(target.getId(),content,key));}
    private long count(){return messages.findAll().stream().filter(row->row.getGroup().getId().equals(group.getId())).count();}

    @Test void retriesBindNormalizedContentAndNeverConsumeAdditionalQuota() {
        String key=key(); var original=send("  Synthetic private message  ",key);
        assertEquals(original,send("Synthetic private message",key.toUpperCase(java.util.Locale.ROOT)));
        assertThrows(IllegalStateException.class,()->send("Changed private message",key));
        send("Second message",key());
        assertThrows(ChatRateLimitException.class,()->send("Third message",key()));
        assertEquals(original,send("Synthetic private message",key));
        assertEquals(2,count());
        var row=messages.findById(original.id()).orElseThrow();
        assertEquals(now.get(),row.getAcceptedAt());
        assertEquals(key,row.getRequestKey());
        assertFalse(row.getRequestHash().contains("Synthetic"));
    }

    @Test void requestKeyScopeIncludesSenderAndGroup() {
        String key=key(); var first=send("First sender",key);
        as(other); var second=send("Different sender",key);
        assertNotEquals(first.id(),second.id());
        var another=department();grant(another,sender);as(sender);
        var third=send(another,"Different group",key);
        assertNotEquals(first.id(),third.id()); assertEquals(2,count());
    }

    @Test void groupLimitAppliesAcrossSendersAndQuarantinedMessagesStillCount() {
        var first=send("One",key());send("Two",key());
        var row=messages.findById(first.id()).orElseThrow();row.setQuarantined(true);messages.saveAndFlush(row);
        as(other);send("Three",key());
        assertThrows(ChatRateLimitException.class,()->send("Four",key()));
        assertEquals(3,count());
    }

    @Test void rollingWindowExpiresAtExactSixtySecondsRegardlessOfHostZone() {
        send("One",key());send("Two",key());
        now.set(now.get().plusSeconds(59).plusNanos(999_000_000));
        assertThrows(ChatRateLimitException.class,()->send("Too soon",key()));
        now.set(now.get().plusNanos(1_000_000));
        send("At boundary",key());assertEquals(3,count());
    }

    @Test void currentAuthorizationAndQuarantineAreCheckedBeforeReplay() {
        String key=key();var original=send("Private original",key);
        var member=members.findByGroupIdAndUserId(group.getId(),sender.getId()).orElseThrow();
        member.setActive(false);member=members.saveAndFlush(member);
        assertThrows(AccessDeniedException.class,()->send("Private original",key));
        member.setActive(true);members.saveAndFlush(member);
        doThrow(new IllegalStateException("Disabled")).when(features).requireEnabledForUpdate("MODULE_COMMUNITY_CHAT");
        assertThrows(IllegalStateException.class,()->send("Private original",key));
        doNothing().when(features).requireEnabledForUpdate("MODULE_COMMUNITY_CHAT");
        var row=messages.findById(original.id()).orElseThrow();row.setQuarantined(true);messages.saveAndFlush(row);
        assertThrows(AccessDeniedException.class,()->send("Private original",key));assertEquals(1,count());
    }

    @Test void acceptedReplayRemainsReadableDuringSilenceOrPostingLockButNewWritesFail() {
        String key=key();var original=send("Day message",key);
        group.setNightSilenceEnabled(true);groups.saveAndFlush(group);
        now.set(Instant.parse("2030-01-01T16:30:00Z"));
        assertEquals(original,send("Day message",key));
        assertThrows(IllegalStateException.class,()->send("Night message",key()));
        group.setNightSilenceEnabled(false);group.setOnlyAdminsCanPost(true);groups.saveAndFlush(group);
        assertEquals(original,send("Day message",key));
        assertThrows(IllegalStateException.class,()->send("Locked message",key()));assertEquals(1,count());
    }

    @Test void simultaneousIdenticalRequestsConvergeOnOnePersistedMessage() throws Exception {
        String key=key();var result=race(()->send("Same concurrent message",key).id(),()->send("Same concurrent message",key).id());
        assertEquals(result.get(0),result.get(1));assertEquals(1,count());
    }

    @Test void simultaneousDistinctRequestsCannotOverrunSenderLimit() throws Exception {
        send("First",key());
        Callable<Boolean> attempt=()->{try{send("Concurrent next",key());return true;}catch(ChatRateLimitException denied){return false;}};
        var result=race(attempt,attempt);assertNotEquals(result.get(0),result.get(1));assertEquals(2,count());
    }

    @Test void simultaneousDifferentSendersCannotOverrunGroupLimit() throws Exception {
        send("Sender initial",key());as(other);send("Other initial",key());as(sender);
        Callable<Boolean> first=()->{try{send("Sender next",key());return true;}catch(ChatRateLimitException denied){return false;}};
        Callable<Boolean> second=()->{as(other);try{send("Other next",key());return true;}catch(ChatRateLimitException denied){return false;}};
        var result=race(first,second);assertNotEquals(result.get(0),result.get(1));assertEquals(3,count());
    }

    @Test void acceptedEventReplaySurvivesCompletionWithoutAllowingNewPost() {
        User owner=user();owner.setRole(Role.FACULTY);owner=users.saveAndFlush(owner);
        Event event=events.saveAndFlush(Event.builder().title("Synthetic event").description("Synthetic")
                .venue("Synthetic venue").eventDate(LocalDateTime.of(2030,1,2,12,0)).maxSeats(10)
                .organizer(owner).status(EventStatus.APPROVED).build());
        operations.saveAndFlush(EventOperations.builder().event(event).build());
        registrations.saveAndFlush(Registration.builder().event(event).user(sender).status(RegistrationStatus.REGISTERED).build());
        ChatGroup eventGroup=groups.saveAndFlush(ChatGroup.builder().name("Synthetic event group")
                .groupType("EPHEMERAL_EVENT").relatedEventId(event.getId()).nightSilenceEnabled(false).build());
        String key=key();var original=send(eventGroup,"Event message",key);
        event.setStatus(EventStatus.COMPLETED);events.saveAndFlush(event);
        assertEquals(original,send(eventGroup,"Event message",key));
        assertThrows(IllegalStateException.class,()->send(eventGroup,"New message",key()));
    }

    @Test void invalidKeysAndBlankPayloadCannotReachPersistence() {
        for(String invalid:new String[]{null,"","not-a-uuid","1-1-1-1-1"})
            assertThrows(IllegalArgumentException.class,()->send("Synthetic",invalid));
        for(String invalid:new String[]{null,"","  ","a".repeat(2001)})
            assertThrows(IllegalArgumentException.class,()->send(invalid,key()));
        assertEquals(0,count());
        assertThrows(IllegalArgumentException.class,()->new ChatPostingPolicy(0,200));
        assertThrows(IllegalArgumentException.class,()->new ChatPostingPolicy(20,-1));
    }

    private <T> java.util.List<T> race(Callable<T> a,Callable<T> b) throws Exception {
        var pool=Executors.newFixedThreadPool(2);var ready=new CountDownLatch(2);var go=new CountDownLatch(1);
        try {
            java.util.function.Function<Callable<T>,Callable<T>> wrapped=task->()->{as(sender);ready.countDown();
                try{assertTrue(go.await(10,TimeUnit.SECONDS));return task.call();}finally{SecurityContextHolder.clearContext();}};
            var first=pool.submit(wrapped.apply(a));var second=pool.submit(wrapped.apply(b));
            assertTrue(ready.await(10,TimeUnit.SECONDS));go.countDown();
            return java.util.List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));
        } finally {go.countDown();pool.shutdownNow();}
    }
}

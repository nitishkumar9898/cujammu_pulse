package com.college.events.service;

import com.college.events.dto.TeamDtos;
import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never",
        "logging.file.name=target/test-logs/teams.log"}, showSql = false)
@Import({EventTeamService.class, RegistrationService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TeamWorkflowConcurrencyTest {
    @Autowired EventTeamService service;
    @Autowired RegistrationService registrationService;
    @Autowired UserRepository users;
    @Autowired EventRepository events;
    @Autowired EventOperationsRepository operations;
    @Autowired RegistrationRepository registrations;
    @Autowired TeamRepository teams;
    @Autowired TeamMemberRepository members;
    @MockitoBean DomainAccess access;
    @MockitoBean FeatureFlagService features;
    @MockitoBean AuditService audit;
    @MockitoBean EmailService email;
    @MockitoBean NotificationService notifications;

    private User user() {
        return users.saveAndFlush(User.builder().name("Synthetic member").email(UUID.randomUUID()+"@example.invalid")
                .password("synthetic-hash").role(Role.STUDENT).enabled(true).build());
    }
    private Event event(User owner) {
        Event event = events.saveAndFlush(Event.builder().title("Synthetic event").description("Test fixture")
                .organizer(owner).venue("Synthetic room").maxSeats(40).status(EventStatus.APPROVED)
                .eventDate(LocalDateTime.now().plusDays(10)).registrationDeadline(LocalDateTime.now().plusDays(2)).build());
        operations.saveAndFlush(EventOperations.builder().event(event).build());
        return event;
    }
    private void confirmed(User user, Event event) {
        registrations.saveAndFlush(Registration.builder().user(user).event(event).status(RegistrationStatus.REGISTERED).build());
    }
    private TeamDtos.TeamView create(User user, Event event, String name) {
        when(access.currentUser()).thenReturn(user);
        return service.createTeam(event.getId(), new TeamDtos.Create(name, 2, 2));
    }
    private void visible() {
        when(features.isEnabled("MODULE_EVENTS")).thenReturn(true);
        when(features.isEnabled("MODULE_TEAMS")).thenReturn(true);
    }

    @Test void simultaneousJoinIntoDifferentTeamsAcceptsOnlyOneMembership() throws Exception {
        User a=user(), b=user(), entrant=user(); Event event=event(a);
        confirmed(a,event); confirmed(b,event); confirmed(entrant,event);
        var first=create(a,event,"First"); var second=create(b,event,"Second");
        when(access.currentUser()).thenReturn(entrant);
        var pool=Executors.newFixedThreadPool(2); var ready=new CountDownLatch(2); var go=new CountDownLatch(1);
        try {
            var tasks=java.util.List.of(first,second).stream().map(team -> pool.submit(() -> {
                ready.countDown(); assertTrue(go.await(10,TimeUnit.SECONDS));
                try { service.joinTeamWithCode(new TeamDtos.Join(event.getId(), team.inviteCode())); return true; }
                catch (IllegalStateException conflict) { return false; }
            })).toList();
            assertTrue(ready.await(10,TimeUnit.SECONDS)); go.countDown();
            int successes=0; for(var task:tasks) if(task.get(20,TimeUnit.SECONDS)) successes++;
            assertEquals(1,successes);
            assertEquals(1,members.findByUser(entrant).size());
            assertEquals(3,members.countByTeamId(first.id())+members.countByTeamId(second.id()));
        } finally { go.countDown(); pool.shutdownNow(); }
    }

    @Test void joinAdvancesRosterRevisionAndDuplicateRetryDoesNotConsumeAnotherPlace() {
        User leader=user(), entrant=user(), other=user(); Event event=event(leader);
        confirmed(leader,event); confirmed(entrant,event); confirmed(other,event);
        var created=create(leader,event,"Small team");
        when(access.currentUser()).thenReturn(entrant);
        var joined=service.joinTeamWithCode(new TeamDtos.Join(event.getId(), created.inviteCode()));
        assertTrue(joined.revision()>created.revision());
        assertEquals(joined.id(),service.joinTeamWithCode(new TeamDtos.Join(event.getId(), created.inviteCode())).id());
        assertEquals(2,members.countByTeamId(created.id()));
        when(access.currentUser()).thenReturn(other);
        assertThrows(IllegalStateException.class,()->service.joinTeamWithCode(new TeamDtos.Join(event.getId(), created.inviteCode())));
        when(access.currentUser()).thenReturn(leader);
        assertThrows(IllegalStateException.class,()->service.setLock(created.id(),new TeamDtos.Lock(created.revision(),true)));
        var locked=service.setLock(created.id(),new TeamDtos.Lock(joined.revision(),true));
        assertTrue(locked.locked()); assertTrue(locked.revision()>joined.revision());
        assertThrows(IllegalStateException.class,()->service.setLock(created.id(),new TeamDtos.Lock(locked.revision(),false)));
    }

    @Test void ownViewReturnsOnlyOwnTeamAndOnlyLeaderGetsCode() {
        visible(); User leader=user(), entrant=user(), outsider=user(); Event event=event(leader);
        confirmed(leader,event); confirmed(entrant,event); confirmed(outsider,event);
        var created=create(leader,event,"Private team");
        assertEquals(created.inviteCode(),service.ownTeams(event.getId()).items().get(0).inviteCode());
        when(access.currentUser()).thenReturn(entrant);
        service.joinTeamWithCode(new TeamDtos.Join(event.getId(), created.inviteCode()));
        assertNull(service.ownTeams(event.getId()).items().get(0).inviteCode());
        assertEquals(2,service.getTeamMembers(created.id()).members().size());
        when(access.currentUser()).thenReturn(outsider);
        assertTrue(service.ownTeams(event.getId()).items().isEmpty());
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.getTeamMembers(created.id()));
    }

    @Test void cancelledEventCannotAcceptJoinAndFeatureOffStopsCreate() {
        User leader=user(), entrant=user(); Event event=event(leader);
        confirmed(leader,event); confirmed(entrant,event);
        var created=create(leader,event,"Closed team");
        event.setStatus(EventStatus.CANCELLED); events.saveAndFlush(event);
        when(access.currentUser()).thenReturn(entrant);
        assertThrows(IllegalStateException.class,()->service.joinTeamWithCode(new TeamDtos.Join(event.getId(), created.inviteCode())));
        doThrow(new IllegalStateException("disabled")).when(features).requireEnabledForUpdate("MODULE_TEAMS");
        assertThrows(IllegalStateException.class,()->service.createTeam(event.getId(),new TeamDtos.Create("Other",2,5)));
    }

    @Test void cancellationAndJoinCannotCommitAnInvalidMembership() throws Exception {
        User leader=user(), entrant=user(); Event event=event(leader);
        confirmed(leader,event); confirmed(entrant,event);
        var created=create(leader,event,"Contended team");
        long registrationId=registrations.findByUserAndEvent(entrant,event).orElseThrow().getId();
        when(access.currentUser()).thenReturn(entrant);
        var pool=Executors.newFixedThreadPool(2); var ready=new CountDownLatch(2); var go=new CountDownLatch(1);
        try {
            var join=pool.submit(() -> {
                ready.countDown(); assertTrue(go.await(10,TimeUnit.SECONDS));
                try { service.joinTeamWithCode(new TeamDtos.Join(event.getId(),created.inviteCode())); return true; }
                catch (org.springframework.security.access.AccessDeniedException denied) { return false; }
            });
            var cancel=pool.submit(() -> {
                ready.countDown(); assertTrue(go.await(10,TimeUnit.SECONDS));
                try { registrationService.cancelRegistration(registrationId,entrant); return true; }
                catch (IllegalStateException conflict) { return false; }
            });
            assertTrue(ready.await(10,TimeUnit.SECONDS)); go.countDown();
            boolean joined=join.get(20,TimeUnit.SECONDS), cancelled=cancel.get(20,TimeUnit.SECONDS);
            assertNotEquals(joined,cancelled);
            assertEquals(joined,members.existsByTeamIdAndUserId(created.id(),entrant.getId()));
            assertEquals(cancelled ? RegistrationStatus.CANCELLED : RegistrationStatus.REGISTERED,
                    registrations.findById(registrationId).orElseThrow().getStatus());
        } finally { go.countDown(); pool.shutdownNow(); }
    }

    @Test void closedRostersRemainPrivateAndReadableButCannotBeChanged() {
        visible(); User leader=user(), outsider=user(); Event event=event(leader);
        confirmed(leader,event); var created=create(leader,event,"Historical team");
        event.setRegistrationDeadline(LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).minusMinutes(1));
        events.saveAndFlush(event);
        assertTrue(service.eligibleEvents().items().isEmpty());
        var history=service.ownTeamHistory(0);
        assertEquals(created.id(),history.items().get(0).team().id());
        assertFalse(history.items().get(0).canChangeRoster());
        assertThrows(IllegalStateException.class,()->service.setLock(created.id(),new TeamDtos.Lock(created.revision(),true)));
        when(access.currentUser()).thenReturn(outsider);
        assertTrue(service.ownTeamHistory(0).items().isEmpty());
    }

    @Test void inviteCannotJoinAnEventOtherThanTheSelectedOne() {
        User leader=user(), entrant=user(); Event event=event(leader), selected=event(leader);
        confirmed(leader,event); confirmed(entrant,event); confirmed(entrant,selected);
        var created=create(leader,event,"Destination"); when(access.currentUser()).thenReturn(entrant);
        assertThrows(IllegalArgumentException.class,()->service.joinTeamWithCode(new TeamDtos.Join(selected.getId(),created.inviteCode())));
        assertTrue(members.findByUser(entrant).isEmpty());
    }
}

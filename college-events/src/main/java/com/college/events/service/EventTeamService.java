package com.college.events.service;

import com.college.events.model.Event;
import com.college.events.model.Team;
import com.college.events.model.TeamMember;
import com.college.events.model.User;
import com.college.events.repository.EventRepository;
import com.college.events.repository.TeamMemberRepository;
import com.college.events.repository.TeamRepository;
import com.college.events.repository.RegistrationRepository;
import com.college.events.dto.TeamDtos;
import com.college.events.security.DomainAccess;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventTeamService {

    private final TeamRepository teamRepository;
    private final EventRepository eventRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final RegistrationRepository registrations;
    private final DomainAccess access;
    private final com.college.events.repository.EventOperationsRepository operations;
    private final FeatureFlagService features;
    private final AuditService audit;

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private String generateInviteCode() {
        StringBuilder sb = new StringBuilder("CUJ-");
        for (int i = 0; i < 6; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    @Transactional
    public TeamDtos.TeamView createTeam(Long eventId, TeamDtos.Create command) {
        User leader = access.currentUser();
        guard(eventId);
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
        requireJoinable(event, leader);
        if (command.name() == null || command.name().isBlank() || command.name().trim().length() > 120
                || command.minSize() < 2 || command.maxSize() > 50) throw new IllegalArgumentException("Invalid team configuration");
        if (command.minSize() > command.maxSize()) throw new IllegalArgumentException("Minimum team size cannot exceed maximum size");
        if (teamMemberRepository.existsByUserIdAndEventId(leader.getId(), eventId)) throw new IllegalStateException("You already belong to a team for this event");

        String inviteCode;
        do {
            inviteCode = generateInviteCode();
        } while (teamRepository.findByInviteCode(inviteCode).isPresent());

        Team team = Team.builder()
                .event(event)
                .leader(leader)
                .name(command.name().trim())
                .inviteCode(inviteCode)
                .minSize(command.minSize())
                .maxSize(command.maxSize())
                .locked(false)
                .build();

        Team savedTeam = teamRepository.saveAndFlush(team);

        // Add leader as first member
        TeamMember leaderMember = TeamMember.builder()
                .team(savedTeam)
                .user(leader)
                .role("LEADER")
                .status("CONFIRMED")
                .build();
        teamMemberRepository.save(leaderMember);

        audit.logAction(leader.getUsername(), leader.getRole().name(), "TEAM_CREATED", "teamId=" + savedTeam.getId() + ";eventId=" + eventId);
        return view(savedTeam, leader, true);
    }

    @Transactional
    public TeamDtos.TeamView joinTeamWithCode(TeamDtos.Join command) {
        User member = access.currentUser();
        if (command.inviteCode() == null || !command.inviteCode().matches("CUJ-[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}"))
            throw new IllegalArgumentException("Invalid invite code");
        long eventId = teamRepository.findEventIdByCode(command.inviteCode())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or unavailable invite code"));
        if (command.expectedEventId() == null || command.expectedEventId() != eventId)
            throw new IllegalArgumentException("Invite does not match the selected event");
        guard(eventId);
        Team team = teamRepository.findByInviteCodeForUpdate(command.inviteCode().trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or non-existent invite code"));
        requireJoinable(team.getEvent(), member);

        if (teamMemberRepository.existsByTeamIdAndUserId(team.getId(), member.getId())) {
            return view(team, member, team.getLeader().getId().equals(member.getId()));
        }

        if (team.isLocked()) {
            throw new IllegalStateException("This team roster has been locked by the leader or organizers.");
        }

        int currentCount = teamMemberRepository.countByTeamId(team.getId());
        if (currentCount >= team.getMaxSize()) {
            throw new IllegalStateException("Team is already at maximum capacity (" + team.getMaxSize() + " members).");
        }

        if (teamMemberRepository.existsByUserIdAndEventId(member.getId(), team.getEvent().getId())) {
            throw new IllegalStateException("You already belong to a team for this event.");
        }

        TeamMember teamMember = TeamMember.builder()
                .team(team)
                .user(member)
                .role("MEMBER")
                .status("CONFIRMED")
                .build();

        teamMemberRepository.save(teamMember);
        team.setRosterChanges(Math.addExact(team.getRosterChanges(), 1));
        teamRepository.saveAndFlush(team);
        audit.logAction(member.getUsername(), member.getRole().name(), "TEAM_JOINED", "teamId=" + team.getId() + ";eventId=" + eventId);
        return view(team, member, false);
    }

    @Transactional
    public TeamDtos.TeamView leaveTeam(Long teamId) {
        User member = access.currentUser();
        long eventId = teamRepository.findEventIdByTeamId(teamId).orElseThrow(DomainAccess::denied);
        guard(eventId);
        Team team = teamRepository.findForUpdate(teamId).orElseThrow(DomainAccess::denied);
        requireJoinableForLock(team.getEvent());
        if (team.isLocked()) throw new IllegalStateException("Locked team membership cannot be changed");
        TeamMember membership = teamMemberRepository.findByTeamIdAndUserId(teamId, member.getId())
                .orElseThrow(() -> new IllegalStateException("You are not a member of this team"));
        if (!"CONFIRMED".equals(membership.getStatus())) return view(team, member, false);
        if (team.getLeader().getId().equals(member.getId())) {
            throw new IllegalStateException("Team leaders must transfer leadership or dissolve the team instead of leaving");
        }
        teamMemberRepository.delete(membership);
        team.setRosterChanges(Math.addExact(team.getRosterChanges(), 1));
        teamRepository.saveAndFlush(team);
        audit.logAction(member.getUsername(), member.getRole().name(), "TEAM_LEFT", "teamId=" + teamId);
        return view(team, member, false);
    }

    @Transactional
    public TeamDtos.TeamView dissolveTeam(Long teamId) {
        User user = access.currentUser();
        long eventId = teamRepository.findEventIdByTeamId(teamId).orElseThrow(DomainAccess::denied);
        guard(eventId);
        Team team = teamRepository.findForUpdate(teamId).orElseThrow(DomainAccess::denied);
        if (!team.getLeader().getId().equals(user.getId())) throw DomainAccess.denied();
        throw new IllegalStateException("Team dissolution requires a reviewed history and judging preservation workflow");
    }

    @Transactional
    public TeamDtos.TeamView setLock(Long teamId, TeamDtos.Lock command) {
        User actor = access.currentUser();
        long eventId = teamRepository.findEventIdByTeamId(teamId).orElseThrow(DomainAccess::denied);
        guard(eventId);
        Team team = teamRepository.findForUpdate(teamId).orElseThrow(DomainAccess::denied);
        if (!team.getLeader().getId().equals(actor.getId())) access.requireEventManager(eventId);
        requireJoinableForLock(team.getEvent());
        if (command.expectedRevision() == null || command.expectedRevision() != team.getRevision())
            throw new IllegalStateException("Team roster changed; refresh and retry");
        if (!command.locked()) throw new IllegalStateException("A finalized roster cannot be reopened through this command");
        int size = teamMemberRepository.countByTeamId(teamId);
        if (size < team.getMinSize() || size > team.getMaxSize())
            throw new IllegalStateException("Team roster does not meet its approved size limits");
        if (!team.isLocked()) {
            team.setLocked(true);
            teamRepository.saveAndFlush(team);
            audit.logAction(actor.getUsername(), actor.getRole().name(), "TEAM_ROSTER_LOCKED", "teamId=" + teamId);
        }
        return view(team, actor, team.getLeader().getId().equals(actor.getId()));
    }

    @Transactional(readOnly = true)
    public TeamDtos.Teams getTeamsForEvent(Long eventId) {
        requireVisible();
        Event event = access.requireEventManager(eventId);
        User actor = access.currentUser();
        return new TeamDtos.Teams(teamRepository.findByEventId(event.getId()).stream().map(team -> view(team, actor, false)).toList());
    }

    @Transactional(readOnly = true)
    public TeamDtos.EligibleEvents eligibleEvents() {
        requireVisible();
        User user = access.currentUser();
        return new TeamDtos.EligibleEvents(registrations.findActiveByUser(user).stream()
                .filter(registration -> registration.getStatus() == com.college.events.model.RegistrationStatus.REGISTERED)
                .filter(registration -> isJoinable(registration.getEvent()))
                .map(registration -> new TeamDtos.EligibleEvent(registration.getEvent().getId(), registration.getEvent().getTitle(), registration.getEvent().getEventDate()))
                .toList());
    }

    @Transactional(readOnly = true)
    public TeamDtos.Roster getTeamMembers(Long teamId) {
        requireVisible();
        User user = access.currentUser();
        Team team = teamRepository.findById(teamId).orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));
        boolean member = teamMemberRepository.existsByTeamIdAndUserId(teamId, user.getId());
        boolean manager = user.getRole() == com.college.events.model.Role.ADMIN || access.isEventOwner(user, team.getEvent());
        if (!member && !manager) throw DomainAccess.denied();
        return new TeamDtos.Roster(view(team, user, team.getLeader().getId().equals(user.getId())), teamMemberRepository.findByTeamId(teamId).stream()
                .map(m -> new TeamDtos.Member(m.getId(), m.getUser().getName(), m.getRole(), m.getJoinedAt())).toList());
    }

    private void requireJoinable(Event event, User user) {
        requireJoinableForLock(event);
        boolean confirmed = registrations.findActiveByUser(user).stream().anyMatch(registration -> registration.getEvent().getId().equals(event.getId())
                && registration.getStatus() == com.college.events.model.RegistrationStatus.REGISTERED);
        if (!confirmed) throw DomainAccess.denied();
    }

    private void requireJoinableForLock(Event event) {
        if (!isJoinable(event)) throw new IllegalStateException("Team registration is closed");
    }

    private boolean isJoinable(Event event) {
        var now = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata"));
        return event.getStatus() == com.college.events.model.EventStatus.APPROVED
                && event.getEventDate() != null && event.getEventDate().isAfter(now)
                && (event.getRegistrationDeadline() == null || event.getRegistrationDeadline().isAfter(now));
    }

    @Transactional(readOnly = true)
    public TeamDtos.Teams ownTeams(long eventId) {
        requireVisible();
        User user = access.currentUser();
        return new TeamDtos.Teams(teamRepository.findOwnByEvent(eventId, user.getId()).stream()
                .map(team -> view(team, user, team.getLeader().getId().equals(user.getId()))).toList());
    }

    @Transactional(readOnly = true)
    public TeamDtos.History ownTeamHistory(int page) {
        requireVisible();
        if (page < 0 || page > 10000) throw new IllegalArgumentException("Invalid page");
        User user = access.currentUser();
        var result = teamRepository.findOwnHistory(user.getId(), org.springframework.data.domain.PageRequest.of(page, 20));
        return new TeamDtos.History(result.getContent().stream().map(team -> {
            Event event = team.getEvent();
            boolean leader = team.getLeader().getId().equals(user.getId());
            return new TeamDtos.HistoryItem(new TeamDtos.EligibleEvent(event.getId(), event.getTitle(), event.getEventDate()),
                    view(team, user, leader), leader && !team.isLocked() && isJoinable(event));
        }).toList(), page, result.hasNext());
    }

    // All create/join/lock commands for an event use the lifecycle guard, including
    // joins into different destination teams. This closes the cross-team race.
    private void guard(long eventId) {
        features.requireEnabledForUpdate("MODULE_EVENTS");
        features.requireEnabledForUpdate("MODULE_TEAMS");
        operations.findForUpdate(eventId).orElseThrow(() -> new IllegalStateException("Event operations are unavailable"));
    }

    private void requireVisible() {
        if (!features.isEnabled("MODULE_EVENTS") || !features.isEnabled("MODULE_TEAMS"))
            throw new IllegalStateException("Teams are unavailable");
    }

    private TeamDtos.TeamView view(Team team, User viewer, boolean revealCode) {
        return new TeamDtos.TeamView(team.getId(), team.getRevision(), team.getName(), team.getMinSize(), team.getMaxSize(),
                teamMemberRepository.countByTeamId(team.getId()), team.isLocked(), team.getLeader().getId().equals(viewer.getId()),
                revealCode ? team.getInviteCode() : null, team.getCreatedAt());
    }
}

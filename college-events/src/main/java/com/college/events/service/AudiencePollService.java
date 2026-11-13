package com.college.events.service;

import com.college.events.dto.PollDtos;
import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/** Versioned polls. Legacy definitions remain retained but cannot acquire new votes. */
@Service
public class AudiencePollService {
    private static final List<RegistrationStatus> ELIGIBLE = List.of(RegistrationStatus.REGISTERED, RegistrationStatus.ATTENDED);
    private final EventPollRepository polls;
    private final PollVoteRepository votes;
    private final EventRepository events;
    private final RegistrationRepository registrations;
    private final EventOperationsRepository operations;
    private final DomainAccess access;
    private final FeatureFlagService features;
    private final AuditService audit;
    private final Clock clock;

    @Autowired
    public AudiencePollService(EventPollRepository polls, PollVoteRepository votes, EventRepository events,
            RegistrationRepository registrations, EventOperationsRepository operations, DomainAccess access,
            FeatureFlagService features, AuditService audit) {
        this(polls, votes, events, registrations, operations, access, features, audit, Clock.systemUTC());
    }
    AudiencePollService(EventPollRepository polls, PollVoteRepository votes, EventRepository events,
            RegistrationRepository registrations, EventOperationsRepository operations, DomainAccess access,
            FeatureFlagService features, AuditService audit, Clock clock) {
        this.polls = polls; this.votes = votes; this.events = events; this.registrations = registrations;
        this.operations = operations; this.access = access; this.features = features; this.audit = audit; this.clock = clock;
    }

    @Transactional
    public PollDtos.Events eligibleEvents(int page) {
        requirePage(page);
        featureGuard();
        User actor = access.currentUser();
        var result = polls.findAccessibleEvents(actor.getId(), actor.getRole() == Role.ADMIN,
                actor.getRole() == Role.FACULTY || actor.getRole() == Role.ORGANIZER,
                actor.getRole() == Role.STUDENT, PageRequest.of(page, 20));
        return new PollDtos.Events(result.getContent().stream().map(e ->
                new PollDtos.EventView(e.getId(), e.getTitle(), e.getEventDate(),
                        manager(actor, e) && e.getStatus() == EventStatus.APPROVED)).toList(), page, result.hasNext());
    }

    @Transactional
    public PollDtos.Polls pollsForEvent(Long eventId, int page) {
        requirePage(page);
        Event event = guard(eventId);
        User actor = access.currentUser();
        requireReader(actor, event);
        var result = polls.findVisible(eventId, manager(actor, event), PageRequest.of(page, 20));
        return new PollDtos.Polls(result.getContent().stream().map(p -> view(p, event, actor)).toList(), page, result.hasNext());
    }

    @Transactional
    public PollDtos.PollView createPoll(Long eventId, PollDtos.Create command) {
        Event event = guard(eventId);
        User actor = access.currentUser();
        requireManager(actor, event);
        Validated definition = validate(command);
        String fingerprint = fingerprint(eventId, definition);
        var previous = polls.findByEventIdAndCreatorIdAndRequestKey(eventId, actor.getId(), definition.key());
        if (previous.isPresent()) {
            EventPoll poll = previous.get();
            requireProtocol(poll);
            if (!fingerprint.equals(poll.getRequestHash())) throw new IllegalStateException("Creation request changed");
            return view(poll, event, actor);
        }
        requireApproved(event);
        EventPoll poll = polls.saveAndFlush(EventPoll.builder().eventId(eventId).question(definition.question())
                .options(String.join(",", definition.options())).active(false).protocolVersion(1).state("DRAFT")
                .creatorId(actor.getId()).requestKey(definition.key()).requestHash(fingerprint)
                .durationMinutes(definition.duration()).build());
        record(actor, "POLL_CREATED", poll);
        return view(poll, event, actor);
    }

    @Transactional
    public PollDtos.PollView publish(Long pollId, PollDtos.Revise command) {
        Locked locked = lockPoll(pollId);
        User actor = access.currentUser();
        requireManager(actor, locked.event());
        EventPoll poll = locked.poll();
        long expected = expected(command);
        if ("OPEN".equals(poll.getState()) && expected == poll.getRevision() - 1) return view(poll, locked.event(), actor);
        if (!"DRAFT".equals(poll.getState()) || expected != poll.getRevision()) throw new IllegalStateException("Poll changed");
        requireApproved(locked.event());
        Instant now = clock.instant();
        poll.setOpensAt(now);
        poll.setClosesAt(now.plusSeconds(poll.getDurationMinutes() * 60L));
        poll.setState("OPEN");
        poll.setActive(true);
        polls.saveAndFlush(poll);
        record(actor, "POLL_PUBLISHED", poll);
        return view(poll, locked.event(), actor);
    }

    @Transactional
    public PollDtos.PollView close(Long pollId, PollDtos.Revise command) {
        Locked locked = lockPoll(pollId);
        User actor = access.currentUser();
        requireManager(actor, locked.event());
        EventPoll poll = locked.poll();
        long expected = expected(command);
        if ("CLOSED".equals(poll.getState()) && expected == poll.getRevision() - 1) return view(poll, locked.event(), actor);
        if (!"OPEN".equals(poll.getState()) || expected != poll.getRevision()) throw new IllegalStateException("Poll changed");
        // Closing is allowed after an event is terminal, preserving the accepted vote record.
        Instant now = clock.instant();
        if (now.isBefore(poll.getClosesAt())) poll.setClosesAt(now.isBefore(poll.getOpensAt()) ? poll.getOpensAt() : now);
        poll.setState("CLOSED");
        poll.setActive(false);
        polls.saveAndFlush(poll);
        record(actor, "POLL_CLOSED", poll);
        return view(poll, locked.event(), actor);
    }

    /**
     * Durable deadline transition. This system safety action remains available while polls are
     * administratively disabled so an old OPEN state cannot linger after its accepted deadline.
     * The event-operations then poll lock order matches vote and event lifecycle commands.
     */
    @Transactional
    public boolean closeExpiredPoll(Long pollId) {
        if (pollId == null || pollId <= 0) return false;
        Long eventId = polls.findEventId(pollId).orElse(null);
        if (eventId == null || operations.findForUpdate(eventId).isEmpty()) return false;
        Event event = events.findById(eventId).orElse(null);
        if (event == null) return false;
        EventPoll poll = polls.findForUpdate(pollId).orElse(null);
        if (poll == null) return false;
        requireProtocol(poll);
        Instant now = clock.instant();
        if (!"OPEN".equals(poll.getState()) || poll.getClosesAt() == null || now.isBefore(poll.getClosesAt()))
            return false;
        poll.setState("CLOSED");
        poll.setActive(false);
        polls.saveAndFlush(poll);
        audit.logAction("system:poll-deadline", "SYSTEM", "POLL_AUTO_CLOSED",
                "pollId=" + poll.getId() + ";eventId=" + event.getId() + ";deadline=" + poll.getClosesAt());
        return true;
    }

    @Transactional
    public PollDtos.PollView castVote(Long pollId, PollDtos.Vote command) {
        Locked locked = lockPoll(pollId);
        User actor = access.currentUser();
        if (!eligible(actor, locked.event())) throw DomainAccess.denied();
        EventPoll poll = locked.poll();
        if (command == null || command.option() == null || !options(poll).contains(command.option()))
            throw new IllegalArgumentException("Invalid option");
        Optional<PollVote> previous = votes.findByPollIdAndVoterId(pollId, actor.getId());
        if (previous.isPresent()) {
            if (!previous.get().getSelectedOption().equals(command.option())) throw new IllegalStateException("Vote is immutable");
            return view(poll, locked.event(), actor);
        }
        requireApproved(locked.event());
        Instant now = clock.instant();
        if (!open(poll, now)) throw new IllegalStateException("Voting window is closed");
        votes.saveAndFlush(PollVote.builder().pollId(pollId).voterId(actor.getId())
                .selectedOption(command.option()).acceptedAt(now).build());
        record(actor, "POLL_VOTE_ACCEPTED", poll);
        return view(poll, locked.event(), actor);
    }

    @Transactional
    public PollDtos.PollView getPollResults(Long pollId) {
        Locked locked = lockPoll(pollId);
        User actor = access.currentUser();
        requireReader(actor, locked.event());
        if ("DRAFT".equals(locked.poll().getState()) && !manager(actor, locked.event())) throw DomainAccess.denied();
        return view(locked.poll(), locked.event(), actor);
    }

    private void featureGuard() {
        features.requireEnabledForUpdate("MODULE_EVENTS");
        features.requireEnabledForUpdate("MODULE_POLLS");
    }
    private Event guard(Long eventId) {
        if (eventId == null || eventId <= 0) throw new IllegalArgumentException("Invalid event");
        featureGuard();
        operations.findForUpdate(eventId).orElseThrow(DomainAccess::denied);
        return events.findById(eventId).orElseThrow(DomainAccess::denied);
    }
    private Locked lockPoll(Long pollId) {
        if (pollId == null || pollId <= 0) throw new IllegalArgumentException("Invalid poll");
        Long eventId = polls.findEventId(pollId).orElseThrow(DomainAccess::denied);
        Event event = guard(eventId);
        EventPoll poll = polls.findForUpdate(pollId).orElseThrow(DomainAccess::denied);
        requireProtocol(poll);
        return new Locked(event, poll);
    }
    private void requireProtocol(EventPoll poll) {
        if (!Integer.valueOf(1).equals(poll.getProtocolVersion()) || poll.getRevision() == null || poll.getRevision() < 0
                || !Set.of("DRAFT", "OPEN", "CLOSED").contains(Objects.toString(poll.getState(), ""))
                || poll.getDurationMinutes() == null || poll.getDurationMinutes() < 1 || poll.getDurationMinutes() > 1440
                || (!"DRAFT".equals(poll.getState()) && (poll.getOpensAt() == null || poll.getClosesAt() == null)))
            throw new IllegalStateException("Poll requires reviewed migration");
        try {
            if (poll.getCreatorId() == null || poll.getCreatorId() <= 0 || poll.getRequestHash() == null
                    || !poll.getRequestHash().matches("[0-9a-f]{64}")) throw new IllegalArgumentException();
            var definition = validate(new PollDtos.Create(poll.getQuestion(), options(poll), poll.getDurationMinutes(), poll.getRequestKey()));
            if (!poll.getRequestHash().equals(fingerprint(poll.getEventId(), definition))) throw new IllegalArgumentException();
            if ("DRAFT".equals(poll.getState()) && (poll.getOpensAt() != null || poll.getClosesAt() != null)) throw new IllegalArgumentException();
            if (!"DRAFT".equals(poll.getState()) && (poll.getClosesAt().isBefore(poll.getOpensAt())
                    || ("OPEN".equals(poll.getState()) && poll.getClosesAt().equals(poll.getOpensAt())))) throw new IllegalArgumentException();
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new IllegalStateException("Poll requires reviewed migration");
        }
    }
    private boolean manager(User actor, Event event) { return actor.getRole() == Role.ADMIN || access.isEventOwner(actor, event); }
    private void requireManager(User actor, Event event) { if (!manager(actor, event)) throw DomainAccess.denied(); }
    private boolean eligible(User actor, Event event) {
        return actor.getRole() == Role.STUDENT && registrations.existsByUserIdAndEventIdAndStatusIn(actor.getId(), event.getId(), ELIGIBLE);
    }
    private void requireReader(User actor, Event event) { if (!manager(actor, event) && !eligible(actor, event)) throw DomainAccess.denied(); }
    private void requireApproved(Event event) { if (event.getStatus() != EventStatus.APPROVED) throw new IllegalStateException("Event is not approved"); }
    private void requirePage(int page) { if (page < 0 || page > 10000) throw new IllegalArgumentException("Invalid page"); }
    private long expected(PollDtos.Revise command) {
        if (command == null || command.expectedRevision() == null || command.expectedRevision() < 0) throw new IllegalArgumentException("Invalid revision");
        return command.expectedRevision();
    }
    private boolean open(EventPoll poll, Instant now) {
        return "OPEN".equals(poll.getState()) && poll.getOpensAt() != null && !now.isBefore(poll.getOpensAt())
                && poll.getClosesAt() != null && now.isBefore(poll.getClosesAt());
    }
    private List<String> options(EventPoll poll) { return List.of(poll.getOptions().split(",", -1)); }
    private PollDtos.PollView view(EventPoll poll, Event event, User actor) {
        requireProtocol(poll);
        List<String> labels = options(poll);
        Map<String, Long> counts = new LinkedHashMap<>();
        labels.forEach(label -> counts.put(label, 0L));
        for (Object[] row : votes.countVotesByOption(poll.getId())) {
            if (!counts.containsKey((String) row[0])) throw new IllegalStateException("Poll tally requires review");
            counts.put((String) row[0], (Long) row[1]);
        }
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        String choice = votes.findByPollIdAndVoterId(poll.getId(), actor.getId()).map(PollVote::getSelectedOption).orElse(null);
        Instant now = clock.instant();
        String state = "OPEN".equals(poll.getState()) && !now.isBefore(poll.getClosesAt()) ? "CLOSED" : poll.getState();
        return new PollDtos.PollView(poll.getId(), event.getId(), poll.getQuestion(), labels, state,
                poll.getRevision(), poll.getOpensAt(), poll.getClosesAt(), manager(actor, event)
                        && ("OPEN".equals(poll.getState()) || event.getStatus() == EventStatus.APPROVED),
                choice == null && eligible(actor, event) && event.getStatus() == EventStatus.APPROVED && open(poll, now), choice, total,
                counts.entrySet().stream().map(e -> new PollDtos.Result(e.getKey(), e.getValue(),
                        total == 0 ? 0 : Math.round(e.getValue() * 1000.0 / total) / 10.0)).toList());
    }
    private Validated validate(PollDtos.Create command) {
        if (command == null || command.question() == null || command.question().isBlank() || command.question().trim().isEmpty() || command.question().length() > 200
                || command.options() == null || command.options().size() < 2 || command.options().size() > 8
                || command.durationMinutes() < 1 || command.durationMinutes() > 1440 || command.requestKey() == null
                || !command.requestKey().matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw new IllegalArgumentException("Invalid poll definition");
        List<String> labels = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String label : command.options()) {
            if (label == null || label.isBlank() || label.length() > 50 || label.contains(",")) throw new IllegalArgumentException("Invalid option");
            label = label.trim();
            if (label.isEmpty()) throw new IllegalArgumentException("Invalid option");
            if (!seen.add(label.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("Duplicate option");
            labels.add(label);
        }
        return new Validated(command.question().trim(), List.copyOf(labels), command.durationMinutes(), UUID.fromString(command.requestKey()).toString());
    }
    private String fingerprint(Long eventId, Validated definition) {
        // Length prefixes preserve boundaries even when question/options contain separator characters.
        List<String> values = new ArrayList<>(List.of(eventId.toString(), definition.question(), Integer.toString(definition.duration())));
        values.addAll(definition.options());
        StringBuilder canonical = new StringBuilder();
        values.forEach(value -> canonical.append(value.length()).append(':').append(value));
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("Digest unavailable", e); }
    }
    private void record(User actor, String action, EventPoll poll) {
        audit.logAction(actor.getUsername(), actor.getRole().name(), action, "pollId=" + poll.getId() + ";eventId=" + poll.getEventId());
    }
    private record Validated(String question, List<String> options, int duration, String key) {}
    private record Locked(Event event, EventPoll poll) {}
}

package com.college.events.service;

import com.college.events.dto.SportsDtos;
import com.college.events.model.Event;
import com.college.events.model.EventStatus;
import com.college.events.model.MatchFixture;
import com.college.events.model.FixtureScoreRevision;
import com.college.events.model.SportsScoreCommandReceipt;
import com.college.events.repository.MatchFixtureRepository;
import com.college.events.repository.EventRepository;
import com.college.events.repository.FixtureScoreRevisionRepository;
import com.college.events.repository.SportsScoreCommandReceiptRepository;
import com.college.events.repository.TeamRepository;
import com.college.events.security.DomainAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Bracket command service with event ownership and revision guards. */
@Service
@RequiredArgsConstructor
public class TournamentBracketService {
    private static final String AGGREGATE_TYPE = "FIXTURE";
    private static final Set<String> STATUSES = Set.of("SCHEDULED", "LIVE", "SUSPENDED", "RESUMED", "PROVISIONAL_RESULT", "FINAL");
    private final MatchFixtureRepository matchFixtureRepository;
    private final EventRepository events;
    private final FixtureScoreRevisionRepository scoreRevisions;
    private final SportsScoreCommandReceiptRepository commandReceipts;
    private final TeamRepository teams;
    private final DomainAccess access;
    private final FeatureFlagService featureFlags;

    @Transactional
    public SportsDtos.FixtureView createFixture(SportsDtos.CreateFixture c) {
        featureFlags.requireEnabledForUpdate("MODULE_TOURNAMENTS");
        requireApproved(c.eventId()); validKey(c.requestKey());
        String createHash = hash(c);
        var previous = commandReceipts.findByAggregateTypeAndEventIdAndRequestKey(AGGREGATE_TYPE, c.eventId(), c.requestKey());
        if (previous.isPresent()) {
            if (!createHash.equals(previous.get().getPayloadHash())) throw new IllegalArgumentException("Request identifier was reused with different fixture data");
            return matchFixtureRepository.findById(previous.get().getAggregateId()).map(this::view)
                    .orElseThrow(() -> new IllegalStateException("Command receipt points to a missing fixture"));
        }
        if(c.team1Id()==null || c.team2Id()==null || c.team1Id().equals(c.team2Id()))
            throw new IllegalArgumentException("Two distinct registered event teams are required");
        var team1=teams.findById(c.team1Id()).filter(t->t.getEvent()!=null && c.eventId().equals(t.getEvent().getId()))
                .orElseThrow(()->new IllegalArgumentException("First team is not registered for this event"));
        var team2=teams.findById(c.team2Id()).filter(t->t.getEvent()!=null && c.eventId().equals(t.getEvent().getId()))
                .orElseThrow(()->new IllegalArgumentException("Second team is not registered for this event"));
        String requestedTeam1=text(c.team1Name(),120),requestedTeam2=text(c.team2Name(),120);
        String t1=text(team1.getName(),120),t2=text(team2.getName(),120);
        if(t1.equalsIgnoreCase(t2)) throw new IllegalArgumentException("Registered teams need distinct display names");
        if(!t1.equals(requestedTeam1) || !t2.equals(requestedTeam2))
            throw new IllegalArgumentException("Team labels do not match registered event teams");
        MatchFixture f = MatchFixture.builder().eventId(c.eventId()).tournamentName(text(c.tournamentName(), 100))
                .roundNumber(c.roundNumber()).roundName(text(c.roundName(), 50)).matchNumber(c.matchNumber())
                .team1Id(c.team1Id()).team1Name(t1).team2Id(c.team2Id()).team2Name(t2)
                .matchTime(c.matchTime()).courtOrVenue(optional(c.venue(), 100)).status("SCHEDULED").build();
        MatchFixture saved = matchFixtureRepository.saveAndFlush(f);
        scoreRevisions.saveAndFlush(snapshot(saved, "CREATED"));
        commandReceipts.saveAndFlush(receipt(saved.getId(), saved.getEventId(), c.requestKey(), createHash, saved.getRevision()));
        return view(saved);
    }

    @Transactional
    public SportsDtos.FixtureView updateScore(Long fixtureId, SportsDtos.FixtureScoreUpdate c) {
        featureFlags.requireEnabledForUpdate("MODULE_TOURNAMENTS"); validKey(c.requestKey());
        MatchFixture f = matchFixtureRepository.findForUpdate(fixtureId).orElseThrow(() -> new IllegalArgumentException("Fixture not found"));
        requireApproved(f.getEventId());
        String hash = hash(c);
        if (c.requestKey().equals(f.getLastScoreRequestKey())) {
            if (!hash.equals(f.getLastScoreRequestHash())) throw new IllegalArgumentException("Request identifier was reused with different fixture data");
            return view(f);
        }
        var previous = commandReceipts.findByAggregateTypeAndEventIdAndRequestKey(AGGREGATE_TYPE, f.getEventId(), c.requestKey());
        if (previous.isPresent()) {
            if (!hash.equals(previous.get().getPayloadHash())) throw new IllegalArgumentException("Request identifier was reused with different fixture data");
            if (!f.getId().equals(previous.get().getAggregateId())) throw new IllegalArgumentException("Request identifier was already used for another fixture");
            if (f.getRevision() == previous.get().getAcceptedRevision()) return view(f);
            throw new IllegalStateException("Request was already accepted at an older revision; refresh before retrying");
        }
        if (f.getRevision() != c.expectedRevision()) throw new IllegalStateException("Fixture changed; refresh and retry");
        String status = text(c.status(), 20).toUpperCase(Locale.ROOT); validateTransition(f.getStatus(), status);
        String changeReason = optional(c.changeReason(), 300);
        if (f.getRevision() > 0L && changeReason == null) throw new IllegalArgumentException("A reason is required for fixture corrections");
        boolean resultState = "FINAL".equals(status) || "PROVISIONAL_RESULT".equals(status);
        if (resultState && c.winnerId() == null) throw new IllegalArgumentException("A result status requires a winner");
        if (!resultState && (c.winnerId() != null || c.winnerName() != null)) throw new IllegalArgumentException("Winner details are allowed only for provisional or final results");
        if (c.winnerId() != null && !c.winnerId().equals(f.getTeam1Id()) && !c.winnerId().equals(f.getTeam2Id())) throw new IllegalArgumentException("Winner must be one of the fixture teams");
        String winnerName = c.winnerId() == null ? null : c.winnerId().equals(f.getTeam1Id()) ? f.getTeam1Name() : f.getTeam2Name();
        String requestedWinnerName = optional(c.winnerName(), 120);
        if (requestedWinnerName != null && !requestedWinnerName.equals(winnerName)) throw new IllegalArgumentException("Winner name does not match the selected fixture team");
        f.setScoreTeam1(text(c.scoreTeam1(), 50)); f.setScoreTeam2(text(c.scoreTeam2(), 50)); f.setStatus(status); f.setWinnerId(c.winnerId()); f.setWinnerName(winnerName); f.setLastScoreRequestKey(c.requestKey()); f.setLastScoreRequestHash(hash);
        MatchFixture saved = matchFixtureRepository.saveAndFlush(f);
        scoreRevisions.saveAndFlush(snapshot(saved, "CORRECTION", changeReason));
        commandReceipts.saveAndFlush(receipt(saved.getId(), saved.getEventId(), c.requestKey(), hash, saved.getRevision()));
        return view(saved);
    }

    @Transactional(readOnly = true)
    public List<SportsDtos.FixtureView> getFixturesForEvent(Long eventId) {
        requireEnabledForRead();
        Event event=events.findById(eventId).orElseThrow(()->new IllegalArgumentException("Event not found"));
        if(event.getStatus()!=EventStatus.APPROVED && event.getStatus()!=EventStatus.COMPLETED)
            access.requireEventManager(eventId);
        return matchFixtureRepository.findByEventIdOrderByRoundNumberAscMatchNumberAsc(eventId).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public List<SportsDtos.FixtureScoreRevisionView> getScoreHistory(Long eventId, Long fixtureId) {
        requireEnabledForRead();
        MatchFixture fixture = matchFixtureRepository.findById(fixtureId).orElseThrow(() -> new IllegalArgumentException("Fixture not found"));
        if (!eventId.equals(fixture.getEventId())) throw DomainAccess.denied();
        access.requireEventManager(eventId);
        return scoreRevisions.findByFixtureIdOrderByFixtureRevisionAsc(fixtureId).stream().map(this::revisionView).toList();
    }

    private Event requireApproved(Long eventId) { Event e = access.requireEventManager(eventId); if (e.getStatus() != EventStatus.APPROVED) throw new IllegalStateException("Tournament operations require an approved event"); return e; }
    private void requireEnabledForRead() { if (!featureFlags.isFeatureEnabled("MODULE_TOURNAMENTS")) throw new IllegalStateException("Tournaments are disabled"); }
    private static void validateTransition(String current, String next) { if (!STATUSES.contains(next)) throw new IllegalArgumentException("Unsupported fixture status; postpone and cancel require the governed match-lifecycle workflow"); if ("FINAL".equals(current)) throw new IllegalStateException("Final fixtures cannot be changed"); Set<String> allowed = switch (current) { case "SCHEDULED" -> Set.of("LIVE"); case "LIVE" -> Set.of("LIVE", "SUSPENDED", "RESUMED", "PROVISIONAL_RESULT"); case "SUSPENDED" -> Set.of("SUSPENDED", "RESUMED"); case "RESUMED" -> Set.of("RESUMED", "SUSPENDED", "PROVISIONAL_RESULT"); case "PROVISIONAL_RESULT" -> Set.of("PROVISIONAL_RESULT", "FINAL"); default -> Set.of(); }; if (!allowed.contains(next)) throw new IllegalStateException("Invalid fixture score transition"); }
    private static String hash(SportsDtos.FixtureScoreUpdate c) { String raw = String.join("|", c.scoreTeam1(), c.scoreTeam2(), c.status(), String.valueOf(c.winnerId()), String.valueOf(c.winnerName()), String.valueOf(c.changeReason())); try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException("Hash unavailable", e); } }
    private static String hash(SportsDtos.CreateFixture c) { String raw = String.join("|", String.valueOf(c.eventId()), c.tournamentName(), String.valueOf(c.roundNumber()), c.roundName(), String.valueOf(c.matchNumber()), String.valueOf(c.team1Id()), c.team1Name(), String.valueOf(c.team2Id()), c.team2Name(), String.valueOf(c.matchTime()), String.valueOf(c.venue())); try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException("Hash unavailable", e); } }
    private static void validKey(String key) { try { UUID.fromString(key); } catch (RuntimeException e) { throw new IllegalArgumentException("A valid request identifier is required"); } }
    private static String text(String value, int max) { if (value == null || value.isBlank()) throw new IllegalArgumentException("Required text is missing"); String v = value.trim(); if (v.length() > max) throw new IllegalArgumentException("Text is too long"); return v; }
    private static String optional(String value, int max) { if (value == null || value.isBlank()) return null; String v = value.trim(); if (v.length() > max) throw new IllegalArgumentException("Text is too long"); return v; }
    private static SportsScoreCommandReceipt receipt(Long aggregateId, Long eventId, String requestKey, String payloadHash, long revision) {
        return SportsScoreCommandReceipt.builder().aggregateType(AGGREGATE_TYPE).aggregateId(aggregateId).eventId(eventId)
                .requestKey(requestKey).payloadHash(payloadHash).acceptedRevision(revision).build();
    }
    private FixtureScoreRevision snapshot(MatchFixture f, String changeType) { return snapshot(f, changeType, null); }
    private FixtureScoreRevision snapshot(MatchFixture f, String changeType, String changeReason) {
        return FixtureScoreRevision.builder().fixtureId(f.getId()).eventId(f.getEventId())
                .fixtureRevision(f.getRevision()).changeType(changeType).scoreTeam1(f.getScoreTeam1())
                .scoreTeam2(f.getScoreTeam2()).winnerId(f.getWinnerId()).winnerName(f.getWinnerName())
                .status(f.getStatus()).requestKey(f.getLastScoreRequestKey()).changeReason(changeReason).build();
    }
    private SportsDtos.FixtureScoreRevisionView revisionView(FixtureScoreRevision r) {
        return new SportsDtos.FixtureScoreRevisionView(r.getId(), r.getFixtureId(), r.getEventId(), r.getFixtureRevision(),
                r.getChangeType(), r.getScoreTeam1(), r.getScoreTeam2(), r.getWinnerId(), r.getWinnerName(),
                r.getStatus(), r.getChangeReason(), r.getRecordedAt());
    }
    private SportsDtos.FixtureView view(MatchFixture f) { return new SportsDtos.FixtureView(f.getId(), f.getEventId(), f.getTournamentName(), f.getRoundNumber(), f.getRoundName(), f.getMatchNumber(), f.getTeam1Id(), f.getTeam1Name(), f.getTeam2Id(), f.getTeam2Name(), f.getScoreTeam1(), f.getScoreTeam2(), f.getWinnerId(), f.getWinnerName(), f.getStatus(), f.getMatchTime(), f.getCourtOrVenue(), f.getRevision()); }
}

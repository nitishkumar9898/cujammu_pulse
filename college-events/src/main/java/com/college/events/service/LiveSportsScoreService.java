package com.college.events.service;

import com.college.events.dto.SportsDtos;
import com.college.events.model.Event;
import com.college.events.model.EventStatus;
import com.college.events.model.SportsCommentary;
import com.college.events.model.SportsLiveMatch;
import com.college.events.model.SportsMatchScoreRevision;
import com.college.events.model.SportsScoreCommandReceipt;
import com.college.events.repository.SportsCommentaryRepository;
import com.college.events.repository.SportsLiveMatchRepository;
import com.college.events.repository.SportsMatchScoreRevisionRepository;
import com.college.events.repository.SportsScoreCommandReceiptRepository;
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

/** Authoritative live-score command boundary. */
@Service
@RequiredArgsConstructor
public class LiveSportsScoreService {
    private static final String AGGREGATE_TYPE = "LIVE_MATCH";
    private static final Set<String> STATUSES = Set.of("SCHEDULED", "LIVE", "SUSPENDED", "RESUMED",
            "PROVISIONAL_RESULT", "FINAL", "CANCELLED", "POSTPONED");
    private final SportsLiveMatchRepository matchRepository;
    private final SportsMatchScoreRevisionRepository scoreRevisions;
    private final SportsScoreCommandReceiptRepository commandReceipts;
    private final SportsCommentaryRepository commentaryRepository;
    private final DomainAccess access;
    private final FeatureFlagService featureFlags;

    @Transactional
    public SportsDtos.MatchView createMatch(SportsDtos.CreateMatch command) {
        featureFlags.requireEnabledForUpdate("MODULE_LIVE_SPORTS");
        Event event = requireApprovedEvent(command.eventId());
        validKey(command.requestKey());
        String createHash = hash(command);
        var previous = commandReceipts.findByAggregateTypeAndEventIdAndRequestKey(AGGREGATE_TYPE, event.getId(), command.requestKey());
        if (previous.isPresent()) {
            if (!createHash.equals(previous.get().getPayloadHash())) throw new IllegalArgumentException("Request identifier was reused with different match data");
            return matchRepository.findById(previous.get().getAggregateId()).map(this::view)
                    .orElseThrow(() -> new IllegalStateException("Command receipt points to a missing match"));
        }
        String a = clean(command.teamA(), 80), b = clean(command.teamB(), 80);
        if (a.equalsIgnoreCase(b)) throw new IllegalArgumentException("Teams must be different");
        SportsLiveMatch match = SportsLiveMatch.builder().eventId(event.getId())
                .sportType(clean(command.sportType(), 60).toUpperCase(Locale.ROOT))
                .tournamentName(clean(command.tournamentName(), 120)).teamA(a).teamB(b)
                .venue(optional(command.venue(), 100)).status("SCHEDULED").build();
        SportsLiveMatch saved = matchRepository.saveAndFlush(match);
        scoreRevisions.saveAndFlush(snapshot(saved, "CREATED"));
        commandReceipts.saveAndFlush(receipt(saved.getId(), saved.getEventId(), command.requestKey(), createHash, saved.getRevision()));
        return view(saved);
    }

    @Transactional(readOnly = true)
    public List<SportsDtos.MatchView> getAllMatches() { requireEnabledForRead(); return matchRepository.findAllByOrderByStatusAscUpdatedAtDesc().stream().map(this::view).toList(); }

    @Transactional(readOnly = true)
    public List<SportsDtos.MatchView> getLiveMatches() { requireEnabledForRead(); return matchRepository.findByStatusOrderByUpdatedAtDesc("LIVE").stream().map(this::view).toList(); }

    @Transactional(readOnly = true)
    public SportsDtos.LiveState getMatchLiveState(Long matchId) {
        requireEnabledForRead();
        SportsLiveMatch match = matchRepository.findById(matchId).orElseThrow(() -> new IllegalArgumentException("Match not found"));
        List<SportsDtos.CommentaryView> commentary = commentaryRepository.findTop20ByMatchIdOrderByIdDesc(matchId).stream().map(this::commentary).toList();
        return new SportsDtos.LiveState(view(match), commentary);
    }

    @Transactional(readOnly = true)
    public List<SportsDtos.MatchScoreRevisionView> getScoreHistory(Long eventId, Long matchId) {
        requireEnabledForRead();
        SportsLiveMatch match = matchRepository.findById(matchId).orElseThrow(() -> new IllegalArgumentException("Match not found"));
        if (!eventId.equals(match.getEventId())) throw DomainAccess.denied();
        access.requireEventManager(eventId);
        return scoreRevisions.findByMatchIdOrderByMatchRevisionAsc(matchId).stream().map(this::revisionView).toList();
    }

    @Transactional
    public SportsDtos.MatchView updateScore(Long matchId, SportsDtos.ScoreUpdate command) {
        featureFlags.requireEnabledForUpdate("MODULE_LIVE_SPORTS");
        validKey(command.requestKey());
        SportsLiveMatch match = matchRepository.findForUpdate(matchId).orElseThrow(() -> new IllegalArgumentException("Match not found"));
        requireApprovedEvent(match.getEventId());
        if (match.getRevision() != command.expectedRevision()) throw new IllegalStateException("Score changed; refresh and retry");
        String hash = hash(command);
        if (command.requestKey().equals(match.getLastScoreRequestKey())) {
            if (!hash.equals(match.getLastScoreRequestHash())) throw new IllegalArgumentException("Request identifier was reused with different score data");
            return view(match);
        }
        var previous = commandReceipts.findByAggregateTypeAndEventIdAndRequestKey(AGGREGATE_TYPE, match.getEventId(), command.requestKey());
        if (previous.isPresent()) {
            if (!hash.equals(previous.get().getPayloadHash())) throw new IllegalArgumentException("Request identifier was reused with different score data");
            if (!match.getId().equals(previous.get().getAggregateId())) throw new IllegalArgumentException("Request identifier was already used for another match");
            if (match.getRevision() == previous.get().getAcceptedRevision()) return view(match);
            throw new IllegalStateException("Request was already accepted at an older revision; refresh before retrying");
        }
        String next = clean(command.status(), 30).toUpperCase(Locale.ROOT);
        validateTransition(match.getStatus(), next);
        String changeReason = optional(command.changeReason(), 300);
        if (match.getRevision() > 0L && changeReason == null) throw new IllegalArgumentException("A reason is required for score corrections");
        String winner = optional(command.winnerTeam(), 80);
        if (("FINAL".equals(next) || "PROVISIONAL_RESULT".equals(next)) && winner == null) throw new IllegalArgumentException("A result status requires a winner");
        if (winner != null && !winner.equals(match.getTeamA()) && !winner.equals(match.getTeamB())) throw new IllegalArgumentException("Winner must be one of the registered teams");
        match.setScoreTeamA(optional(command.scoreTeamA(), 50)); match.setScoreTeamB(optional(command.scoreTeamB(), 50));
        match.setStatus(next); match.setLiveSummary(optional(command.liveSummary(), 250)); match.setWinnerTeam(winner);
        match.setPlayerOfTheMatch(optional(command.playerOfTheMatch(), 100)); match.setLastScoreRequestKey(command.requestKey()); match.setLastScoreRequestHash(hash);
        SportsLiveMatch saved = matchRepository.saveAndFlush(match);
        scoreRevisions.saveAndFlush(snapshot(saved, "CORRECTION", changeReason));
        commandReceipts.saveAndFlush(receipt(saved.getId(), saved.getEventId(), command.requestKey(), hash, saved.getRevision()));
        return view(saved);
    }

    @Transactional
    public SportsDtos.CommentaryView addCommentary(Long matchId, SportsDtos.CommentaryCreate command) {
        featureFlags.requireEnabledForUpdate("MODULE_LIVE_SPORTS"); validKey(command.requestKey());
        SportsLiveMatch match = matchRepository.findForUpdate(matchId).orElseThrow(() -> new IllegalArgumentException("Match not found"));
        requireApprovedEvent(match.getEventId());
        if (!Set.of("LIVE", "RESUMED", "SUSPENDED").contains(match.getStatus())) throw new IllegalStateException("Commentary is only allowed while a match is in progress");
        SportsCommentary commentary = SportsCommentary.builder().matchId(matchId).timelineMark(optional(command.timelineMark(), 20))
                .commentaryText(clean(command.commentaryText(), 1000)).scoreEvent(optional(command.scoreEvent(), 30)).isImportant(command.important()).build();
        return commentary(commentaryRepository.save(commentary));
    }

    private Event requireApprovedEvent(Long eventId) { Event event = access.requireEventManager(eventId); if (event.getStatus() != EventStatus.APPROVED) throw new IllegalStateException("Sports operations require an approved event"); return event; }
    private static void validateTransition(String current, String next) {
        if (!STATUSES.contains(next)) throw new IllegalArgumentException("Unsupported sports status");
        if ("FINAL".equals(current) || "CANCELLED".equals(current)) throw new IllegalStateException("Final or cancelled matches cannot be changed");
        Set<String> allowed = switch (current) {
            case "SCHEDULED" -> Set.of("SCHEDULED", "LIVE", "POSTPONED", "CANCELLED");
            case "LIVE" -> Set.of("LIVE", "SUSPENDED", "RESUMED", "PROVISIONAL_RESULT", "CANCELLED");
            case "SUSPENDED" -> Set.of("SUSPENDED", "RESUMED", "POSTPONED", "CANCELLED");
            case "RESUMED" -> Set.of("RESUMED", "SUSPENDED", "PROVISIONAL_RESULT", "CANCELLED");
            case "PROVISIONAL_RESULT" -> Set.of("PROVISIONAL_RESULT", "FINAL", "CANCELLED");
            case "POSTPONED" -> Set.of("POSTPONED", "SCHEDULED", "CANCELLED");
            default -> Set.of();
        };
        if (!allowed.contains(next)) throw new IllegalStateException("Invalid status transition");
    }
    private static String hash(SportsDtos.ScoreUpdate c) { String raw = String.join("|", n(c.scoreTeamA()), n(c.scoreTeamB()), n(c.status()), n(c.liveSummary()), n(c.winnerTeam()), n(c.playerOfTheMatch()), n(c.changeReason())); try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException("Hash unavailable", e); } }
    private static String hash(SportsDtos.CreateMatch c) { String raw = String.join("|", n(c.eventId() == null ? null : c.eventId().toString()), n(c.sportType()), n(c.tournamentName()), n(c.teamA()), n(c.teamB()), n(c.venue())); try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException("Hash unavailable", e); } }
    private static String n(String v) { return v == null ? "" : v.trim(); }
    private static void validKey(String key) { try { UUID.fromString(key); } catch (RuntimeException e) { throw new IllegalArgumentException("A valid request identifier is required"); } }
    private static String clean(String value, int max) { String v = value == null ? "" : value.trim(); if (v.isBlank()) throw new IllegalArgumentException("Required text is missing"); if (v.length() > max) throw new IllegalArgumentException("Text is too long"); return v; }
    private static String optional(String value, int max) { if (value == null || value.isBlank()) return null; String v = value.trim(); if (v.length() > max) throw new IllegalArgumentException("Text is too long"); return v; }
    private static SportsScoreCommandReceipt receipt(Long aggregateId, Long eventId, String requestKey, String payloadHash, long revision) {
        return SportsScoreCommandReceipt.builder().aggregateType(AGGREGATE_TYPE).aggregateId(aggregateId).eventId(eventId)
                .requestKey(requestKey).payloadHash(payloadHash).acceptedRevision(revision).build();
    }
    private void requireEnabledForRead() {
        if (!featureFlags.isFeatureEnabled("MODULE_LIVE_SPORTS")) throw new IllegalStateException("Live sports is disabled");
    }
    private SportsMatchScoreRevision snapshot(SportsLiveMatch m, String changeType) { return snapshot(m, changeType, null); }
    private SportsMatchScoreRevision snapshot(SportsLiveMatch m, String changeType, String changeReason) {
        return SportsMatchScoreRevision.builder().matchId(m.getId()).eventId(m.getEventId())
                .matchRevision(m.getRevision()).changeType(changeType).scoreTeamA(m.getScoreTeamA())
                .scoreTeamB(m.getScoreTeamB()).status(m.getStatus()).liveSummary(m.getLiveSummary())
                .winnerTeam(m.getWinnerTeam()).playerOfTheMatch(m.getPlayerOfTheMatch()).changeReason(changeReason)
                .requestKey(m.getLastScoreRequestKey()).build();
    }
    private SportsDtos.MatchScoreRevisionView revisionView(SportsMatchScoreRevision r) {
        return new SportsDtos.MatchScoreRevisionView(r.getId(), r.getMatchId(), r.getEventId(), r.getMatchRevision(),
                r.getChangeType(), r.getScoreTeamA(), r.getScoreTeamB(), r.getStatus(), r.getLiveSummary(),
                r.getWinnerTeam(), r.getPlayerOfTheMatch(), r.getChangeReason(), r.getRecordedAt());
    }
    private SportsDtos.MatchView view(SportsLiveMatch m) { return new SportsDtos.MatchView(m.getId(), m.getEventId(), m.getSportType(), m.getTournamentName(), m.getTeamA(), m.getTeamB(), m.getScoreTeamA(), m.getScoreTeamB(), m.getStatus(), m.getLiveSummary(), m.getVenue(), m.getWinnerTeam(), m.getPlayerOfTheMatch(), m.getRevision(), m.getUpdatedAt()); }
    private SportsDtos.CommentaryView commentary(SportsCommentary c) { return new SportsDtos.CommentaryView(c.getId(), c.getMatchId(), c.getTimelineMark(), c.getCommentaryText(), c.getScoreEvent(), c.isImportant(), c.getTimestamp()); }
}

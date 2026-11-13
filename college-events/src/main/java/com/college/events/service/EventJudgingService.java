package com.college.events.service;

import com.college.events.dto.JudgingDtos;
import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Versioned four-criterion judging boundary. Assignment is explicit; role alone is insufficient. */
@Service
@RequiredArgsConstructor
public class EventJudgingService {
    private static final String RUBRIC = "FOUR_CRITERIA_25_V1";
    private final JudgeScoreRepository scores;
    private final JudgeScoreRevisionRepository revisions;
    private final EventJudgeAssignmentRepository assignments;
    private final EventRepository events;
    private final UserRepository users;
    private final DomainAccess access;
    private final FeatureFlagService featureFlags;

    @Transactional
    public JudgingDtos.AssignmentView assignJudge(Long eventId, JudgingDtos.Assign command) {
        featureFlags.requireEnabledForUpdate("MODULE_JUDGING");
        Event event = requireManager(eventId);
        User judge = users.findById(command.judgeId()).orElseThrow(DomainAccess::denied);
        if (judge.getRole() != Role.JUDGE && judge.getRole() != Role.FACULTY)
            throw new IllegalArgumentException("Only verified judge or faculty accounts may be assigned");
        EventJudgeAssignment a = assignments.findForUpdate(eventId, command.judgeId()).orElseGet(() ->
                EventJudgeAssignment.builder().eventId(event.getId()).judgeId(judge.getId()).build());
        a.setActive(true); a.setRubricVersion(RUBRIC);
        return view(assignments.saveAndFlush(a));
    }

    @Transactional(readOnly = true)
    public List<JudgingDtos.AssignmentView> assignments(Long eventId) {
        requireManager(eventId);
        return assignments.findByEventIdAndActiveTrueOrderByIdAsc(eventId).stream().map(this::view).toList();
    }

    @Transactional
    public JudgingDtos.AssignmentView revokeJudge(Long eventId, Long judgeId) {
        featureFlags.requireEnabledForUpdate("MODULE_JUDGING");
        requireManager(eventId);
        EventJudgeAssignment assignment = assignments.findForUpdate(eventId, judgeId)
                .orElseThrow(DomainAccess::denied);
        assignment.setActive(false);
        return view(assignments.saveAndFlush(assignment));
    }

    @Transactional
    public JudgingDtos.ScoreView submitScore(Long eventId, JudgingDtos.Score command) {
        featureFlags.requireEnabledForUpdate("MODULE_JUDGING");
        Event event = requireApproved(eventId);
        User judge = access.currentUser();
        authorizeScorer(event, judge);
        validKey(command.requestKey());
        if (!Double.isFinite(command.innovation()) || !Double.isFinite(command.technical())
                || !Double.isFinite(command.execution()) || !Double.isFinite(command.presentation()))
            throw new IllegalArgumentException("Scores must be finite numbers");
        JudgeScore score = scores.findForUpdate(eventId, judge.getId(), command.participantId(), command.roundNumber()).orElse(null);
        String hash = hash(command);
        if (score != null && command.requestKey().equals(score.getRequestKey())) {
            if (!hash.equals(score.getRequestHash())) throw new IllegalArgumentException("Request identifier was reused with different score data");
            return view(score);
        }
        if (score != null && score.getRevision() != command.expectedRevision())
            throw new IllegalStateException("Score changed; refresh and retry");
        if (score == null && command.expectedRevision() != 0L)
            throw new IllegalStateException("New score must start at revision zero");
        if (score == null) score = JudgeScore.builder().eventId(eventId).judgeId(judge.getId()).judgeName(judge.getName())
                .participantId(command.participantId()).participantName(command.participantName().trim()).roundNumber(command.roundNumber()).build();
        else revisions.saveAndFlush(snapshot(score, "CORRECTION"));
        score.setParticipantName(command.participantName().trim());
        score.setInnovationScore(command.innovation()); score.setTechnicalDepth(command.technical());
        score.setExecutionScore(command.execution()); score.setPresentationScore(command.presentation());
        score.setTotalScore(command.innovation() + command.technical() + command.execution() + command.presentation());
        score.setFeedbackNotes(command.feedbackNotes() == null ? null : command.feedbackNotes().trim());
        score.setRubricVersion(RUBRIC); score.setRequestKey(command.requestKey()); score.setRequestHash(hash);
        JudgeScore saved = scores.saveAndFlush(score);
        if (score.getRevision() == 0L) revisions.saveAndFlush(snapshot(saved, "SUBMISSION"));
        return view(saved);
    }

    @Transactional(readOnly = true)
    public List<JudgingDtos.ScoreView> getScoresForEventRound(Long eventId, int roundNumber) {
        requireManager(eventId);
        if (roundNumber < 1 || roundNumber > 20) throw new IllegalArgumentException("Invalid round");
        return scores.findByEventIdAndRoundNumber(eventId, roundNumber).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public JudgingDtos.Leaderboard getLeaderboard(Long eventId, int roundNumber) {
        if (!featureFlags.isFeatureEnabled("MODULE_JUDGING")) throw new IllegalStateException("Judging is disabled");
        requireApproved(eventId);
        if (roundNumber < 1 || roundNumber > 20) throw new IllegalArgumentException("Invalid round");
        List<Object[]> rows = scores.getLeaderboardForRound(eventId, roundNumber);
        List<JudgingDtos.LeaderboardRow> result = new ArrayList<>(); int rank = 1;
        for (Object[] row : rows) result.add(new JudgingDtos.LeaderboardRow(rank++, (Long) row[0], (String) row[1], Math.round(((Number) row[2]).doubleValue() * 100.0) / 100.0));
        return new JudgingDtos.Leaderboard(result);
    }

    private Event requireManager(Long eventId) { Event e = requireApproved(eventId); access.requireEventManager(eventId); return e; }
    private Event requireApproved(Long eventId) { Event e = events.findById(eventId).orElseThrow(DomainAccess::denied); if (e.getStatus() != EventStatus.APPROVED) throw new IllegalStateException("Judging requires an approved event"); return e; }
    private void authorizeScorer(Event e, User judge) { boolean manager = judge.getRole() == Role.ADMIN || access.isEventOwner(judge, e); if (!manager) { EventJudgeAssignment a = assignments.findByEventIdAndJudgeId(e.getId(), judge.getId()).orElseThrow(DomainAccess::denied); if (!a.isActive() || !RUBRIC.equals(a.getRubricVersion())) throw DomainAccess.denied(); } }
    private static void validKey(String key) { try { UUID.fromString(key); } catch (RuntimeException e) { throw new IllegalArgumentException("A valid request identifier is required"); } }
    private static String hash(JudgingDtos.Score c) { String raw = String.join("|", String.valueOf(c.participantId()), c.participantName().trim(), String.valueOf(c.roundNumber()), String.valueOf(c.innovation()), String.valueOf(c.technical()), String.valueOf(c.execution()), String.valueOf(c.presentation()), String.valueOf(c.feedbackNotes())); try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException("Hash unavailable", e); } }
    private JudgingDtos.AssignmentView view(EventJudgeAssignment a) { return new JudgingDtos.AssignmentView(a.getId(), a.getEventId(), a.getJudgeId(), a.isActive(), a.getRubricVersion(), a.getRevision(), a.getAssignedAt()); }
    private JudgingDtos.ScoreView view(JudgeScore s) { return new JudgingDtos.ScoreView(s.getId(), s.getEventId(), s.getJudgeId(), s.getParticipantId(), s.getParticipantName(), s.getRoundNumber(), s.getInnovationScore(), s.getTechnicalDepth(), s.getExecutionScore(), s.getPresentationScore(), s.getTotalScore(), s.getRubricVersion(), s.getFeedbackNotes(), s.getRevision(), s.getScoredAt()); }
    private JudgeScoreRevision snapshot(JudgeScore s, String changeType) {
        return JudgeScoreRevision.builder().score(s).eventId(s.getEventId()).judgeId(s.getJudgeId()).participantId(s.getParticipantId())
                .participantName(s.getParticipantName()).roundNumber(s.getRoundNumber()).scoreRevision(s.getRevision()).changeType(changeType)
                .innovationScore(s.getInnovationScore()).technicalDepth(s.getTechnicalDepth()).executionScore(s.getExecutionScore())
                .presentationScore(s.getPresentationScore()).totalScore(s.getTotalScore()).feedbackNotes(s.getFeedbackNotes())
                .rubricVersion(s.getRubricVersion()).requestKey(s.getRequestKey()).build();
    }
}

package com.college.events.service;

import com.college.events.dto.JudgingDtos;
import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EventJudgingRevisionTest {
    JudgeScoreRepository scores = mock(JudgeScoreRepository.class);
    JudgeScoreRevisionRepository revisions = mock(JudgeScoreRevisionRepository.class);
    EventJudgeAssignmentRepository assignments = mock(EventJudgeAssignmentRepository.class);
    EventRepository events = mock(EventRepository.class);
    UserRepository users = mock(UserRepository.class);
    DomainAccess access = mock(DomainAccess.class);
    FeatureFlagService flags = mock(FeatureFlagService.class);
    EventJudgingService service = new EventJudgingService(scores, revisions, assignments, events, users, access, flags);

    @Test
    void firstScoreCreatesImmutableSubmissionSnapshot() {
        User judge = user(2L, Role.JUDGE);
        Event event = event(7L, judge);
        EventJudgeAssignment assignment = EventJudgeAssignment.builder().eventId(7L).judgeId(2L).active(true)
                .rubricVersion("FOUR_CRITERIA_25_V1").build();
        when(events.findById(7L)).thenReturn(Optional.of(event));
        when(access.currentUser()).thenReturn(judge);
        when(access.isEventOwner(judge, event)).thenReturn(false);
        when(assignments.findByEventIdAndJudgeId(7L, 2L)).thenReturn(Optional.of(assignment));
        when(scores.findForUpdate(7L, 2L, 12L, 1)).thenReturn(Optional.empty());
        when(scores.saveAndFlush(any())).thenAnswer(invocation -> {
            JudgeScore score = invocation.getArgument(0);
            score.setId(31L);
            return score;
        });
        when(revisions.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.submitScore(7L, new JudgingDtos.Score(12L, "Team 12", 1,
                20, 21, 22, 23, "Good work", 0L, UUID.randomUUID().toString()));

        var capture = org.mockito.ArgumentCaptor.forClass(JudgeScoreRevision.class);
        verify(revisions).saveAndFlush(capture.capture());
        assertEquals("SUBMISSION", capture.getValue().getChangeType());
        assertEquals(31L, capture.getValue().getScore().getId());
        assertEquals(86.0, capture.getValue().getTotalScore());
    }

    @Test
    void correctionSnapshotsThePreviousScoreBeforeMutation() {
        User judge = user(2L, Role.JUDGE);
        Event event = event(7L, judge);
        EventJudgeAssignment assignment = EventJudgeAssignment.builder().eventId(7L).judgeId(2L).active(true)
                .rubricVersion("FOUR_CRITERIA_25_V1").build();
        JudgeScore current = JudgeScore.builder().id(31L).eventId(7L).judgeId(2L).judgeName("Judge")
                .participantId(12L).participantName("Team 12").roundNumber(1).rubricVersion("FOUR_CRITERIA_25_V1")
                .innovationScore(20).technicalDepth(21).executionScore(22).presentationScore(23).totalScore(86).revision(1L)
                .requestKey(UUID.randomUUID().toString()).build();
        when(events.findById(7L)).thenReturn(Optional.of(event));
        when(access.currentUser()).thenReturn(judge);
        when(access.isEventOwner(judge, event)).thenReturn(false);
        when(assignments.findByEventIdAndJudgeId(7L, 2L)).thenReturn(Optional.of(assignment));
        when(scores.findForUpdate(7L, 2L, 12L, 1)).thenReturn(Optional.of(current));
        when(scores.saveAndFlush(current)).thenReturn(current);
        when(revisions.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.submitScore(7L, new JudgingDtos.Score(12L, "Team 12", 1,
                10, 11, 12, 13, "Corrected", 1L, UUID.randomUUID().toString()));

        var capture = org.mockito.ArgumentCaptor.forClass(JudgeScoreRevision.class);
        verify(revisions).saveAndFlush(capture.capture());
        assertEquals("CORRECTION", capture.getValue().getChangeType());
        assertEquals(86.0, capture.getValue().getTotalScore());
        assertEquals(1L, capture.getValue().getScoreRevision());
        assertEquals(46.0, current.getTotalScore());
    }

    private User user(long id, Role role) { return User.builder().id(id).name("Judge").email("judge@example.invalid").role(role).enabled(true).build(); }
    private Event event(long id, User organizer) { return Event.builder().id(id).title("Judged event").description("Description").organizer(organizer).venue("Hall").eventDate(LocalDateTime.now().plusDays(1)).maxSeats(100).status(EventStatus.APPROVED).build(); }
}

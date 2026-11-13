package com.college.events.repository;

import com.college.events.model.JudgeScoreRevision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JudgeScoreRevisionRepository extends JpaRepository<JudgeScoreRevision, Long> {
    List<JudgeScoreRevision> findByEventIdAndRoundNumberAndParticipantIdOrderByScoreRevisionAsc(
            long eventId, int roundNumber, long participantId);
}

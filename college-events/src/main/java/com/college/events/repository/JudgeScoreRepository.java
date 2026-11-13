package com.college.events.repository;

import com.college.events.model.JudgeScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

@Repository
public interface JudgeScoreRepository extends JpaRepository<JudgeScore, Long> {
    List<JudgeScore> findByEventId(Long eventId);
    List<JudgeScore> findByEventIdAndRoundNumber(Long eventId, int roundNumber);
    Optional<JudgeScore> findByEventIdAndJudgeIdAndParticipantIdAndRoundNumber(Long eventId, Long judgeId, Long participantId, int roundNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from JudgeScore j where j.eventId = :eventId and j.judgeId = :judgeId and j.participantId = :participantId and j.roundNumber = :roundNumber")
    Optional<JudgeScore> findForUpdate(@Param("eventId") Long eventId, @Param("judgeId") Long judgeId,
                                       @Param("participantId") Long participantId, @Param("roundNumber") int roundNumber);

    @Query("SELECT j.participantId, j.participantName, AVG(j.totalScore) as avgScore " +
           "FROM JudgeScore j WHERE j.eventId = :eventId AND j.roundNumber = :roundNumber " +
           "GROUP BY j.participantId, j.participantName ORDER BY avgScore DESC")
    List<Object[]> getLeaderboardForRound(@Param("eventId") Long eventId, @Param("roundNumber") int roundNumber);
}

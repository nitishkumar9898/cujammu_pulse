package com.college.events.repository;

import com.college.events.model.EventJudgeAssignment;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface EventJudgeAssignmentRepository extends JpaRepository<EventJudgeAssignment, Long> {
    Optional<EventJudgeAssignment> findByEventIdAndJudgeId(Long eventId, Long judgeId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from EventJudgeAssignment a where a.eventId = :eventId and a.judgeId = :judgeId")
    Optional<EventJudgeAssignment> findForUpdate(@Param("eventId") Long eventId, @Param("judgeId") Long judgeId);
    List<EventJudgeAssignment> findByEventIdAndActiveTrueOrderByIdAsc(Long eventId);
}

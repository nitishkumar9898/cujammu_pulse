package com.college.events.repository;

import com.college.events.model.ReadinessTask;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReadinessTaskRepository extends JpaRepository<ReadinessTask, Long> {
    List<ReadinessTask> findTop100ByEventIdOrderByDueAtAscIdAsc(Long eventId);
    Optional<ReadinessTask> findByEventIdAndId(Long eventId, Long id);
    Optional<ReadinessTask> findByEventIdAndRequestKey(Long eventId, String requestKey);
}

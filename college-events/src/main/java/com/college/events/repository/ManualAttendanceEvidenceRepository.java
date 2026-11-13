package com.college.events.repository;

import com.college.events.model.ManualAttendanceEvidence;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManualAttendanceEvidenceRepository extends JpaRepository<ManualAttendanceEvidence, Long> {
    Optional<ManualAttendanceEvidence> findByEventIdAndIdempotencyKey(Long eventId, String idempotencyKey);
    Optional<ManualAttendanceEvidence> findByEventIdAndRegistrationIdAndAction(Long eventId, Long registrationId, String action);
    java.util.List<ManualAttendanceEvidence> findByEventIdOrderByOccurredAtAscIdAsc(Long eventId);
    long countByEventId(long eventId);
    java.util.List<ManualAttendanceEvidence> findByEventIdAndOccurredAtLessThanEqualOrderByOccurredAtAscIdAsc(Long eventId, java.time.Instant cutoff);
}

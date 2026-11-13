package com.college.events.repository;

import com.college.events.model.EventQrAttendanceEvidence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EventQrAttendanceEvidenceRepository extends JpaRepository<EventQrAttendanceEvidence, Long> {
    Optional<EventQrAttendanceEvidence> findByEventIdAndRegistrationIdAndAction(long eventId, long registrationId, String action);
    java.util.List<EventQrAttendanceEvidence> findByEventIdOrderByOccurredAtAscIdAsc(long eventId);
    long countByEventId(long eventId);
    java.util.List<EventQrAttendanceEvidence> findByEventIdAndOccurredAtLessThanEqualOrderByOccurredAtAscIdAsc(long eventId, java.time.Instant cutoff);
}

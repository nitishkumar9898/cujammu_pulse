package com.college.events.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

/** Append-only accepted manual check-in evidence. Changes require a separate reviewed correction record. */
@Entity
@Table(name = "manual_attendance_evidence", uniqueConstraints = {
        @UniqueConstraint(name = "uk_manual_evidence_action", columnNames = {"event_id", "registration_id", "action"}),
        @UniqueConstraint(name = "uk_manual_evidence_idempotency", columnNames = {"event_id", "idempotency_key"})
}, indexes = @Index(name = "idx_manual_evidence_event_time", columnList = "event_id,occurred_at"))
@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class ManualAttendanceEvidence {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "event_id", nullable = false, updatable = false) private Event event;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "registration_id", nullable = false, updatable = false) private Registration registration;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "operator_id", nullable = false, updatable = false) private User operator;
    @Column(nullable = false, length = 32, updatable = false) private String action;
    @Column(name = "idempotency_key", nullable = false, length = 36, updatable = false) private String idempotencyKey;
    @Column(name = "verified_identifier_snapshot", nullable = false, length = 64, updatable = false) private String verifiedIdentifierSnapshot;
    @Column(name = "reason_code", nullable = false, length = 40, updatable = false) private String reasonCode;
    @Column(name = "occurred_at", nullable = false, updatable = false) private Instant occurredAt;
}

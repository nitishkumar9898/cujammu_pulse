package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Append-only accepted owner-confirmed QR evidence. Corrections require a separate reviewed record. */
@Entity
@Table(name = "event_qr_attendance_evidence", uniqueConstraints = {
        @UniqueConstraint(name = "uk_event_qr_attendance_action", columnNames = {"event_id", "registration_id", "action"})
}, indexes = @Index(name = "idx_event_qr_attendance_event_time", columnList = "event_id,occurred_at"))
@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class EventQrAttendanceEvidence {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "event_id", nullable = false, updatable = false) private Event event;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "registration_id", nullable = false, updatable = false) private Registration registration;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "operator_id", nullable = false, updatable = false) private User operator;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "intent_id", nullable = false, updatable = false) private EventScanIntent intent;
    @Column(nullable = false, length = 32, updatable = false) private String action;
    @Column(nullable = false, length = 40, updatable = false) private String source;
    @Column(nullable = false, updatable = false) private Instant occurredAt;
}

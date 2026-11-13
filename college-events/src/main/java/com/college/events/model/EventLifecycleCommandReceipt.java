package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** Idempotency receipt for effective event cancellation/rescheduling commands. */
@Entity
@Table(name = "event_lifecycle_command_receipts",
        uniqueConstraints = @UniqueConstraint(name = "uk_event_lifecycle_request", columnNames = {"event_id", "request_key"}),
        indexes = @Index(name = "idx_event_lifecycle_receipt_event", columnList = "event_id, command_type, accepted_at"))
@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class EventLifecycleCommandReceipt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "event_id", nullable = false, updatable = false) private Long eventId;
    @Column(name = "request_key", nullable = false, length = 36, updatable = false) private String requestKey;
    @Column(name = "command_type", nullable = false, length = 20, updatable = false) private String commandType;
    @Column(name = "payload_hash", nullable = false, length = 64, updatable = false) private String payloadHash;
    @Column(name = "result_status", nullable = false, length = 24, updatable = false) private String resultStatus;
    @CreationTimestamp @Column(nullable = false, updatable = false) private LocalDateTime acceptedAt;
}

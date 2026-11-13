package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** Durable command receipt used to prevent an old request key being applied again. */
@Entity
@Table(name = "sports_score_command_receipts",
        uniqueConstraints = @UniqueConstraint(name = "uk_sports_score_command_receipt",
                columnNames = {"aggregate_type", "event_id", "request_key"}),
        indexes = @Index(name = "idx_sports_score_receipt_aggregate", columnList = "aggregate_type,aggregate_id,accepted_revision"))
@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class SportsScoreCommandReceipt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_type", nullable = false, length = 24, updatable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private Long aggregateId;

    @Column(name = "event_id", nullable = false, updatable = false)
    private Long eventId;

    @Column(name = "request_key", nullable = false, length = 36, updatable = false)
    private String requestKey;

    @Column(name = "payload_hash", nullable = false, length = 64, updatable = false)
    private String payloadHash;

    @Column(name = "accepted_revision", nullable = false, updatable = false)
    private long acceptedRevision;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime acceptedAt;
}

package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** Durable owner-scoped receipt for an uncertain proposal submission retry. */
@Entity
@Table(name = "event_proposal_submission_receipts",
        uniqueConstraints = @UniqueConstraint(name = "uk_proposal_submission_owner_key", columnNames = {"organizer_id", "request_key"}),
        indexes = @Index(name = "idx_proposal_submission_event", columnList = "event_id"))
@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class EventProposalSubmissionReceipt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "organizer_id", nullable = false, updatable = false) private Long organizerId;
    @Column(name = "request_key", nullable = false, length = 36, updatable = false) private String requestKey;
    @Column(name = "payload_hash", nullable = false, length = 64, updatable = false) private String payloadHash;
    @Column(name = "event_id", nullable = false, updatable = false) private Long eventId;
    @CreationTimestamp @Column(nullable = false, updatable = false) private LocalDateTime acceptedAt;
}

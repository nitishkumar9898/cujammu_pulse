package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** Append-only evidence for an owner-confirmed hostel gate transition. */
@Entity
@Table(name = "hostel_gate_evidence",
        uniqueConstraints = @UniqueConstraint(name = "uk_hostel_gate_evidence_action", columnNames = {"pass_id", "action"}),
        indexes = @Index(name = "idx_hostel_gate_evidence_pass", columnList = "pass_id,occurred_at"))
@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class HostelGateEvidence {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pass_id", nullable = false, updatable = false)
    private HostelGatePass pass;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "intent_id", nullable = false, updatable = false)
    private HostelScanIntent intent;

    @Column(nullable = false, length = 12, updatable = false)
    private String action;

    @Column(nullable = false, updatable = false)
    private Long operatorId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    @Column(nullable = false, length = 24, updatable = false)
    private String resultingStatus;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime recordedAt;
}

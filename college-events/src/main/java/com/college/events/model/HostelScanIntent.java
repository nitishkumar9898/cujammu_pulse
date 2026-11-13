package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** Short-lived operator scan waiting for authenticated pass-owner confirmation. */
@Entity
@Table(name = "hostel_scan_intents",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_hostel_scan_intent_key", columnNames = "intent_key"),
                @UniqueConstraint(name = "uk_hostel_scan_request_key", columnNames = "request_key")
        }, indexes = @Index(name = "idx_hostel_scan_intent_pass_status", columnList = "pass_id,status"))
@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class HostelScanIntent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Builder.Default
    private long revision = 0L;

    @Column(name = "intent_key", nullable = false, length = 36, updatable = false)
    private String intentKey;

    @Column(name = "request_key", nullable = false, length = 36, updatable = false)
    private String requestKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credential_id", nullable = false, updatable = false)
    private HostelPassCredential credential;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pass_id", nullable = false, updatable = false)
    private HostelGatePass pass;

    @Column(nullable = false, length = 12, updatable = false)
    private String action;

    @Column(nullable = false, updatable = false)
    private Long operatorId;

    @Column(name = "challenge_digest", nullable = false, length = 64, updatable = false)
    private String challengeDigest;

    @Column(nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false, length = 16)
    @Builder.Default
    private String status = "PENDING";

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    public void markExpired() { this.status = "EXPIRED"; }
    public void markConfirmed() { this.status = "CONFIRMED"; }
}

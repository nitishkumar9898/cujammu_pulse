package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Short-lived online intent created by an authorized scanner before owner confirmation. */
@Entity
@Table(name = "event_scan_intents", uniqueConstraints = {
        @UniqueConstraint(name = "uk_event_scan_intent_key", columnNames = {"event_id", "request_key"}),
        @UniqueConstraint(name = "uk_event_scan_intent_id", columnNames = "intent_key")
}, indexes = {
        @Index(name = "idx_event_scan_intent_owner", columnList = "registration_id,status,expires_at"),
        @Index(name = "idx_event_scan_intent_digest", columnList = "credential_id,status")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EventScanIntent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "event_id", nullable = false, updatable = false) private Event event;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "registration_id", nullable = false, updatable = false) private Registration registration;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "credential_id", nullable = false, updatable = false) private EventQrCredential credential;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "operator_id", nullable = false, updatable = false) private User operator;
    @Column(name = "intent_key", nullable = false, length = 36, updatable = false) private String intentKey;
    @Column(name = "request_key", nullable = false, length = 36, updatable = false) private String requestKey;
    @Column(nullable = false, length = 32, updatable = false) private String action;
    @Column(name = "challenge_digest", nullable = false, length = 64, updatable = false) private String challengeDigest;
    /** Immutable version binding; null legacy rows fail closed at owner confirmation. */
    @Column(name = "credential_version_at_scan") private Long credentialVersionAtScan;
    /** Immutable scanner-control epoch binding; null legacy rows fail closed. */
    @Column(name = "scanner_epoch_at_scan") private Long scannerEpochAtScan;
    @Column(nullable = false, length = 16) @Builder.Default private String status = "PENDING";
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(nullable = false) private Instant expiresAt;
    private Instant confirmedAt;
    @Version @Builder.Default private long revision = 0L;
    @PrePersist void created() { if (createdAt == null) createdAt = Instant.now(); }
}

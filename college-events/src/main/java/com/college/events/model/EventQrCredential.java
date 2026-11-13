package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Server-resolved event credential. The bearer value is never persisted or returned from reports. */
@Entity
@Table(name = "event_qr_credentials", uniqueConstraints = {
        @UniqueConstraint(name = "uk_event_qr_registration_action", columnNames = {"event_id", "registration_id", "action"}),
        @UniqueConstraint(name = "uk_event_qr_digest", columnNames = "token_digest")
}, indexes = @Index(name = "idx_event_qr_registration_status", columnList = "registration_id,status"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EventQrCredential {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "event_id", nullable = false, updatable = false) private Event event;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "registration_id", nullable = false, updatable = false) private Registration registration;
    @Column(name = "token_digest", nullable = false, length = 64) private String tokenDigest;
    @Column(nullable = false, length = 32) private String action;
    @Column(nullable = false, length = 16) @Builder.Default private String status = "ACTIVE";
    @Column(nullable = false) @Builder.Default private long credentialVersion = 0L;
    /** Null on legacy rows; null deliberately fails closed until reissued under a live scanner epoch. */
    private Long scannerEpoch;
    @Column(nullable = false, updatable = false) private Instant issuedAt;
    @Column(nullable = false) private Instant expiresAt;
    private Instant consumedAt;
    @Version @Builder.Default private long revision = 0L;
    @PrePersist void created() { if (issuedAt == null) issuedAt = Instant.now(); }
}

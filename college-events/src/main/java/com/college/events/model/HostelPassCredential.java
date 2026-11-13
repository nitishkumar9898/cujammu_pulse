package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Digest-only, short-lived credential for one hostel gate action. */
@Entity
@Table(name = "hostel_pass_credentials",
        uniqueConstraints = @UniqueConstraint(name = "uk_hostel_pass_credential_digest", columnNames = "token_digest"),
        indexes = @Index(name = "idx_hostel_pass_credential_action", columnList = "pass_id,action,active"))
@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class HostelPassCredential {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Builder.Default
    private long revision = 0L;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pass_id", nullable = false, updatable = false)
    private HostelGatePass pass;

    @Column(nullable = false, length = 12, updatable = false)
    private String action;

    @Column(name = "token_digest", nullable = false, length = 64, updatable = false)
    private String tokenDigest;

    @Column(nullable = false, updatable = false)
    private LocalDateTime issuedAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    public void deactivate() { this.active = false; }
}

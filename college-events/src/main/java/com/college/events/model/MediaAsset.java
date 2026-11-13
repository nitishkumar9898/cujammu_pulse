package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Metadata for an accepted upload; the object key is never a public authority. */
@Entity
@Table(name = "media_assets", indexes = {
        @Index(name = "idx_media_expiry", columnList = "expiresAt, status"),
        @Index(name = "idx_media_key", columnList = "objectKey", unique = true)
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MediaAsset {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 300, unique = true)
    private String objectKey;
    /** Historical unbound objects remain inaccessible until reviewed ownership migration. */
    private Long ownerUserId;
    @Column(length=24) private String resourceType;
    private Long resourceId;
    @Column(nullable = false, length = 80)
    private String mediaType;
    @Column(nullable = false)
    private long sizeBytes;
    @Column(nullable = false)
    private Instant uploadAcceptedAt;
    @Column(nullable = false)
    private Instant expiresAt;
    @Column(nullable = false, length = 20)
    @Builder.Default private String status = "ACTIVE";
    @Column(length = 64) private String sha256;
    @Column(nullable = false) @Builder.Default private boolean legalHold = false;
    private Instant legalHoldUntil;
    private Instant purgedAt;
    @Column(nullable = false) @Builder.Default private int purgeAttempts = 0;
    @Column(length = 240) private String lastPurgeError;
    @Version @Builder.Default private long revision = 0L;
}

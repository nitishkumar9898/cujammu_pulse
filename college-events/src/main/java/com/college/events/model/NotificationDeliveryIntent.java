package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** Durable in-app delivery/outbox receipt. External channels are separate adapters. */
@Entity
@Table(name = "notification_delivery_intents",
        uniqueConstraints = @UniqueConstraint(name = "uk_notification_delivery_intent", columnNames = {"notification_id", "channel"}),
        indexes = @Index(name = "idx_notification_delivery_status", columnList = "status, next_attempt_at"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationDeliveryIntent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "notification_id", nullable = false, updatable = false) private Notification notification;
    @Column(nullable = false, length = 24, updatable = false) private String channel;
    @Column(nullable = false, length = 32, updatable = false) private String dedupeKey;
    @Column(nullable = false, length = 24) @Builder.Default private String status = "QUEUED";
    @Column(nullable = false) @Builder.Default private int attempts = 0;
    private LocalDateTime nextAttemptAt;
    @Column(length = 80) private String leaseOwner;
    private LocalDateTime leaseUntil;
    private LocalDateTime reconciledAt;
    @Column(length = 240) private String lastError;
    @CreationTimestamp @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PreUpdate void touch() { updatedAt = LocalDateTime.now(); }
}

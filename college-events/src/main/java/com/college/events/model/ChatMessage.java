package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages", uniqueConstraints = @UniqueConstraint(name = "uq_chat_post_request", columnNames = {"group_id", "sender_id", "request_key"}), indexes = {
    @Index(name = "idx_cm_group_time", columnList = "group_id, createdAt"),
    @Index(name = "idx_cm_group_accepted", columnList = "group_id, accepted_at"),
    @Index(name = "idx_cm_sender_accepted", columnList = "group_id, sender_id, accepted_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "group_id", nullable = false)
    private ChatGroup group;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    /** Nullable only for pre-migration messages. New command paths always supply all three values. */
    @Column(name = "request_key", length = 36, updatable = false)
    private String requestKey;
    @Column(name = "request_hash", length = 64, updatable = false)
    private String requestHash;
    @Column(name = "accepted_at", updatable = false)
    private java.time.Instant acceptedAt;

    private String mediaUrl;

    @Column(length = 20)
    @Builder.Default
    private String mediaType = "NONE"; // NONE, IMAGE, PDF, AUDIO

    @Builder.Default
    private int flaggedCount = 0;

    @Builder.Default
    private boolean quarantined = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}

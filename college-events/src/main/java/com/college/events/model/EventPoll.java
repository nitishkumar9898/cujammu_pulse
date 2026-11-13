package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.time.Instant;

@Entity
@Table(name = "event_polls", uniqueConstraints = {
    @UniqueConstraint(name = "uq_poll_creation", columnNames = {"event_id", "creator_id", "request_key"})
}, indexes = {
    @Index(name = "idx_ep_event", columnList = "eventId"),
    @Index(name = "idx_ep_active", columnList = "active"),
    @Index(name = "idx_ep_deadline", columnList = "protocolVersion,state,closesAt")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventPoll {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private Long eventId;

    @Column(nullable = false, length = 200, updatable = false)
    private String question;

    // Comma-separated or JSON list of options e.g. "Team Alpha,Team Beta,Team Gamma"
    @Column(nullable = false, length = 500, updatable = false)
    private String options;

    @Builder.Default
    private boolean active = true;

    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    // Null protocol denotes retained legacy data, never an implicitly migrated live poll.
    @Column(updatable = false)
    private Integer protocolVersion;
    private String state;
    @Version
    @Builder.Default
    private Long revision = 0L;
    @Column(name = "creator_id", updatable = false)
    private Long creatorId;
    @Column(name = "request_key", length = 36, updatable = false)
    private String requestKey;
    @Column(length = 64, updatable = false)
    private String requestHash;
    @Column(updatable = false)
    private Integer durationMinutes;
    private Instant opensAt;
    private Instant closesAt;
}

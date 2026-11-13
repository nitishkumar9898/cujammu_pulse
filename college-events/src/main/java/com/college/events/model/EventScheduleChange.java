package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** Immutable, manager-scoped history of an effective event schedule change. */
@Entity
@Table(name = "event_schedule_changes",
        uniqueConstraints = @UniqueConstraint(name = "uk_event_schedule_change_number", columnNames = {"event_id", "change_number"}),
        indexes = @Index(name = "idx_event_schedule_changes_event", columnList = "event_id, change_number"))
@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class EventScheduleChange {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "event_id", nullable = false, updatable = false) private Event event;
    @Column(name = "change_number", nullable = false, updatable = false) private long changeNumber;
    @Column(nullable = false, length = 20, updatable = false) private String changeType;
    @Column(updatable = false) private LocalDateTime previousDate;
    @Column(updatable = false) private LocalDateTime replacementDate;
    @Column(nullable = false, length = 300, updatable = false) private String publicReason;
    @Column(nullable = false, updatable = false) private Long actorId;
    @Column(nullable = false, length = 160, updatable = false) private String actorEmail;
    @Column(nullable = false, length = 24, updatable = false) private String actorRole;
    @CreationTimestamp @Column(nullable = false, updatable = false) private LocalDateTime recordedAt;
}

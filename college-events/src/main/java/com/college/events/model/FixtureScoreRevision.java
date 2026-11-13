package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** Immutable accepted-state snapshot for a tournament fixture. */
@Entity
@Table(name = "fixture_score_revisions",
        uniqueConstraints = @UniqueConstraint(name = "uk_fixture_score_revision", columnNames = {"fixture_id", "fixture_revision"}),
        indexes = @Index(name = "idx_fixture_revision_event", columnList = "event_id,fixture_id,recorded_at"))
@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class FixtureScoreRevision {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fixture_id", nullable = false, updatable = false)
    private Long fixtureId;

    @Column(name = "event_id", nullable = false, updatable = false)
    private Long eventId;

    @Column(name = "fixture_revision", nullable = false, updatable = false)
    private long fixtureRevision;

    @Column(name = "change_type", nullable = false, length = 24, updatable = false)
    private String changeType;

    @Column(length = 50, updatable = false)
    private String scoreTeam1;

    @Column(length = 50, updatable = false)
    private String scoreTeam2;

    private Long winnerId;

    @Column(length = 120, updatable = false)
    private String winnerName;

    @Column(nullable = false, length = 20, updatable = false)
    private String status;

    @Column(length = 36, updatable = false)
    private String requestKey;

    @Column(length = 300, updatable = false)
    private String changeReason;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime recordedAt;
}

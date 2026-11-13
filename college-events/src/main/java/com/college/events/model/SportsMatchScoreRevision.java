package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** Immutable accepted-state snapshot for a live sports match. */
@Entity
@Table(name = "sports_match_score_revisions",
        uniqueConstraints = @UniqueConstraint(name = "uk_sports_match_score_revision", columnNames = {"match_id", "match_revision"}),
        indexes = @Index(name = "idx_sports_match_revision_event", columnList = "event_id,match_id,recorded_at"))
@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class SportsMatchScoreRevision {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_id", nullable = false, updatable = false)
    private Long matchId;

    @Column(name = "event_id", nullable = false, updatable = false)
    private Long eventId;

    @Column(name = "match_revision", nullable = false, updatable = false)
    private long matchRevision;

    @Column(name = "change_type", nullable = false, length = 24, updatable = false)
    private String changeType;

    @Column(name = "score_team_a", length = 50, updatable = false)
    private String scoreTeamA;

    @Column(name = "score_team_b", length = 50, updatable = false)
    private String scoreTeamB;

    @Column(nullable = false, length = 30, updatable = false)
    private String status;

    @Column(length = 250, updatable = false)
    private String liveSummary;

    @Column(length = 100, updatable = false)
    private String winnerTeam;

    @Column(length = 100, updatable = false)
    private String playerOfTheMatch;

    @Column(length = 36, updatable = false)
    private String requestKey;

    @Column(length = 300, updatable = false)
    private String changeReason;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime recordedAt;
}

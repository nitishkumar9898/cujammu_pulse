package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "sports_live_matches", indexes = {
    @Index(name = "idx_slm_status", columnList = "status"),
    @Index(name = "idx_slm_sport", columnList = "sportType")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SportsLiveMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Optimistic concurrency token; score commands must carry the revision read by the operator. */
    @Version
    @Builder.Default
    private long revision = 0L;

    private Long eventId;

    @Column(length = 60, nullable = false)
    @Builder.Default
    private String sportType = "CRICKET"; // CRICKET, FOOTBALL, VOLLEYBALL, BASKETBALL, BADMINTON, KABADDI, TABLE_TENNIS, CHESS

    @Column(length = 120, nullable = false)
    private String tournamentName;

    @Column(nullable = false, length = 80)
    private String teamA;

    @Column(nullable = false, length = 80)
    private String teamB;

    @Column(length = 50)
    @Builder.Default
    private String scoreTeamA = "0/0 (0.0 ov)";

    @Column(length = 50)
    @Builder.Default
    private String scoreTeamB = "Yet to bat";

    @Column(length = 30, nullable = false)
    @Builder.Default
    private String status = "SCHEDULED"; // SCHEDULED, LIVE, INNINGS_BREAK, COMPLETED, ABANDONED

    @Column(length = 36)
    private String lastScoreRequestKey;

    @Column(length = 64)
    private String lastScoreRequestHash;

    @Column(length = 250)
    private String liveSummary; // e.g. "CSIT need 12 runs in 8 balls with 4 wickets in hand"

    @Column(length = 100)
    private String venue;

    private String playerOfTheMatch;

    private String winnerTeam;

    @Column(columnDefinition = "TEXT")
    private String extraStatsJson; // Batters, bowlers, line-ups, foul counts, etc.

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}

package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "match_fixtures", indexes = {
    @Index(name = "idx_mf_event_round", columnList = "eventId, roundNumber"),
    @Index(name = "idx_mf_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchFixture {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Builder.Default
    private long revision = 0L;

    @Column(nullable = false)
    private Long eventId;

    @Column(length = 100)
    private String tournamentName;

    @Builder.Default
    private int roundNumber = 1; // 1 = Prelims / R16, 2 = Quarterfinals, 3 = Semifinals, 4 = Finals

    @Column(length = 50)
    private String roundName; // e.g. "Quarter Final 1"

    @Builder.Default
    private int matchNumber = 1;

    private Long team1Id;
    @Column(nullable = false)
    private String team1Name;

    private Long team2Id;
    @Column(nullable = false)
    private String team2Name;

    @Builder.Default
    private String scoreTeam1 = "0";

    @Builder.Default
    private String scoreTeam2 = "0";

    private Long winnerId;
    private String winnerName;

    @Column(length = 20, nullable = false)
    @Builder.Default
    private String status = "SCHEDULED"; // SCHEDULED, LIVE, COMPLETED, CANCELLED

    @Column(length = 36)
    private String lastScoreRequestKey;

    @Column(length = 64)
    private String lastScoreRequestHash;

    private LocalDateTime matchTime;

    private String courtOrVenue;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}

package com.college.events.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** Typed, allow-listed contracts for live sports and tournament commands. */
public final class SportsDtos {
    private SportsDtos() { }

    public record CreateMatch(
            @NotNull Long eventId,
            @NotBlank @Size(max = 60) String sportType,
            @NotBlank @Size(max = 120) String tournamentName,
            @NotBlank @Size(max = 80) String teamA,
            @NotBlank @Size(max = 80) String teamB,
            @Size(max = 100) String venue,
            @NotBlank String requestKey) { }

    public record ScoreUpdate(
            @Size(max = 50) String scoreTeamA,
            @Size(max = 50) String scoreTeamB,
            @NotBlank @Size(max = 30) String status,
            @Size(max = 250) String liveSummary,
            @Size(max = 80) String winnerTeam,
            @Size(max = 100) String playerOfTheMatch,
            @NotNull Long expectedRevision,
            @NotBlank String requestKey,
            @Size(max = 300) String changeReason) {
        public ScoreUpdate(String scoreTeamA, String scoreTeamB, String status, String liveSummary,
                           String winnerTeam, String playerOfTheMatch, Long expectedRevision, String requestKey) {
            this(scoreTeamA, scoreTeamB, status, liveSummary, winnerTeam, playerOfTheMatch, expectedRevision, requestKey, null);
        }
    }

    public record CommentaryCreate(
            @Size(max = 20) String timelineMark,
            @NotBlank @Size(max = 1000) String commentaryText,
            @Size(max = 30) String scoreEvent,
            boolean important,
            @NotBlank String requestKey) { }

    public record MatchView(Long id, Long eventId, String sportType, String tournamentName,
                            String teamA, String teamB, String scoreTeamA, String scoreTeamB,
                            String status, String liveSummary, String venue, String winnerTeam,
                            String playerOfTheMatch, Long revision, LocalDateTime updatedAt) { }

    public record CommentaryView(Long id, Long matchId, String timelineMark, String commentaryText,
                                 String scoreEvent, boolean important, LocalDateTime timestamp) { }

    public record LiveState(MatchView match, List<CommentaryView> commentary) { }

    public record CreateFixture(
            @NotNull Long eventId,
            @NotBlank @Size(max = 100) String tournamentName,
            @Positive int roundNumber,
            @NotBlank @Size(max = 50) String roundName,
            @Positive int matchNumber,
            Long team1Id,
            @NotBlank @Size(max = 120) String team1Name,
            Long team2Id,
            @NotBlank @Size(max = 120) String team2Name,
            @NotNull LocalDateTime matchTime,
            @Size(max = 100) String venue,
            @NotBlank String requestKey) { }

    public record FixtureScoreUpdate(
            @NotBlank @Size(max = 50) String scoreTeam1,
            @NotBlank @Size(max = 50) String scoreTeam2,
            @NotBlank @Size(max = 20) String status,
            Long winnerId,
            @Size(max = 120) String winnerName,
            @NotNull Long expectedRevision,
            @NotBlank String requestKey,
            @Size(max = 300) String changeReason) {
        public FixtureScoreUpdate(String scoreTeam1, String scoreTeam2, String status, Long winnerId,
                                  String winnerName, Long expectedRevision, String requestKey) {
            this(scoreTeam1, scoreTeam2, status, winnerId, winnerName, expectedRevision, requestKey, null);
        }
    }

    public record FixtureView(Long id, Long eventId, String tournamentName, int roundNumber,
                              String roundName, int matchNumber, Long team1Id, String team1Name,
                              Long team2Id, String team2Name, String scoreTeam1, String scoreTeam2,
                              Long winnerId, String winnerName, String status, LocalDateTime matchTime,
                              String venue, Long revision) { }

    public record MatchScoreRevisionView(Long id, Long matchId, Long eventId, long matchRevision,
                                         String changeType, String scoreTeamA, String scoreTeamB,
                                         String status, String liveSummary, String winnerTeam,
                                         String playerOfTheMatch, String changeReason, LocalDateTime recordedAt) { }

    public record FixtureScoreRevisionView(Long id, Long fixtureId, Long eventId, long fixtureRevision,
                                           String changeType, String scoreTeam1, String scoreTeam2,
                                           Long winnerId, String winnerName, String status,
                                           String changeReason, LocalDateTime recordedAt) { }
}

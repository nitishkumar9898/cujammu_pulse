package com.college.events.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public final class JudgingDtos {
    private JudgingDtos() { }
    public record Assign(@NotNull Long judgeId) { }
    public record Score(@NotNull Long participantId, @NotBlank @Size(max = 120) String participantName,
                        @Min(1) @Max(20) int roundNumber,
                        @DecimalMin("0.0") @DecimalMax("25.0") double innovation,
                        @DecimalMin("0.0") @DecimalMax("25.0") double technical,
                        @DecimalMin("0.0") @DecimalMax("25.0") double execution,
                        @DecimalMin("0.0") @DecimalMax("25.0") double presentation,
                        @Size(max = 1000) String feedbackNotes,
                        @NotNull Long expectedRevision, @NotBlank String requestKey) { }
    public record AssignmentView(Long id, Long eventId, Long judgeId, boolean active, String rubricVersion,
                                 long revision, LocalDateTime assignedAt) { }
    public record ScoreView(Long id, Long eventId, Long judgeId, Long participantId, String participantName,
                            int roundNumber, double innovation, double technical, double execution,
                            double presentation, double total, String rubricVersion, String feedbackNotes,
                            long revision, LocalDateTime scoredAt) { }
    public record LeaderboardRow(int rank, Long participantId, String participantName, double averageScore) { }
    public record Leaderboard(List<LeaderboardRow> rows) { }
}

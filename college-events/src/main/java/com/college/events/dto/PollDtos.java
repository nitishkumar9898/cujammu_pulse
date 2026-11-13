package com.college.events.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public final class PollDtos {
    private PollDtos() {}
    public record Create(@NotBlank @Size(max = 200) String question,
            @NotNull @Size(min = 2, max = 8) List<@NotBlank @Size(max = 50) String> options,
            @Min(1) @Max(1440) int durationMinutes,
            @NotBlank @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String requestKey) {}
    public record Revise(@NotNull @Min(0) Long expectedRevision) {}
    public record Vote(@NotBlank @Size(max = 50) String option) {}
    public record Result(String option, long votes, double percentage) {}
    public record PollView(Long id, Long eventId, String question, List<String> options, String state,
            long revision, Instant opensAt, Instant closesAt, boolean canManage, boolean canVote,
            String myChoice, long totalVotes, List<Result> results) {}
    public record EventView(Long id, String title, LocalDateTime eventDate, boolean canManage) {}
    public record Events(List<EventView> items, int page, boolean hasMore) {}
    public record Polls(List<PollView> items, int page, boolean hasMore) {}
}

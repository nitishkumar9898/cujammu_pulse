package com.college.events.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class TeamDtos {
    private TeamDtos() { }
    public record Create(@NotBlank @Size(max = 120) String name, @Min(2) @Max(50) int minSize,
                         @Min(2) @Max(50) int maxSize) { }
    public record Join(@NotNull @Min(1) Long expectedEventId,
                       @NotBlank @Pattern(regexp = "CUJ-[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}") String inviteCode) { }
    public record Lock(@NotNull Long expectedRevision, boolean locked) { }
    public record TeamView(long id, long revision, String name, int minSize, int maxSize, int memberCount,
                           boolean locked, boolean canManage, String inviteCode, LocalDateTime createdAt) { }
    public record Teams(List<TeamView> items) { }
    public record EligibleEvent(long id, String title, LocalDateTime eventDate) { }
    public record EligibleEvents(List<EligibleEvent> items) { }
    public record HistoryItem(EligibleEvent event, TeamView team, boolean canChangeRoster) { }
    public record History(List<HistoryItem> items, int page, boolean hasMore) { }
    public record Member(long id, String name, String role, LocalDateTime joinedAt) { }
    public record Roster(TeamView team, List<Member> members) { }
}

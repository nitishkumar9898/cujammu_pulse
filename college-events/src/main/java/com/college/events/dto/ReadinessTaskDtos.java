package com.college.events.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class ReadinessTaskDtos {
    private ReadinessTaskDtos() {}

    public record Create(@NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 500) String description,
            @NotBlank @Pattern(regexp = "APPROVAL|ACCESSIBILITY|SAFETY|STAFFING|RESOURCE|OPERATIONS|OTHER") String category,
            @Positive Long ownerUserId, @NotNull Instant dueAt,
            @NotBlank @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String requestKey) {}

    public record Update(@PositiveOrZero long expectedRevision,
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 500) String description,
            @NotBlank @Pattern(regexp = "APPROVAL|ACCESSIBILITY|SAFETY|STAFFING|RESOURCE|OPERATIONS|OTHER") String category,
            @Positive Long ownerUserId, @NotNull Instant dueAt,
            @NotBlank @Pattern(regexp = "OPEN|IN_PROGRESS|BLOCKED|DONE") String status,
            @Size(max = 300) String completionNote) {}

    public record Item(long id, long eventId, String title, String description, String category,
            String status, String completionNote, long ownerUserId, String ownerName, String ownerRole,
            long createdByUserId, Instant dueAt, long revision, Instant createdAt, Instant updatedAt) {}

    public record Owner(long userId, String name, String role) {}
    public record ListView(long eventId, List<Item> items, boolean truncated) {}
}

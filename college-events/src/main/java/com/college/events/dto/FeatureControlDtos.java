package com.college.events.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** Safe control-plane views. They intentionally expose no internal targeting rules. */
public final class FeatureControlDtos {
    private FeatureControlDtos() { }

    public record Catalogue(List<Item> items) { }

    public record Item(String key, String name, String description, String category,
                       boolean requestedEnabled, boolean effectiveEnabled, boolean locked,
                       long revision, LocalDateTime updatedAt, String updatedBy) { }

    public record Change(@NotNull Boolean enabled, @NotNull Long expectedRevision,
                         @NotBlank @Size(max = 300) String reason) { }
}

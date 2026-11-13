package com.college.events.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** Client may request resources, but never supplies booking priority or authority. */
public record FacilityBookingCommand(
        @NotBlank @Size(max = 200) String title,
        @NotNull LocalDateTime startTime,
        @NotNull LocalDateTime endTime,
        @Min(0) @Max(100) int requestedMics,
        boolean requestedProjector,
        @NotBlank @Pattern(regexp = "(?i)[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
        String requestKey) {}

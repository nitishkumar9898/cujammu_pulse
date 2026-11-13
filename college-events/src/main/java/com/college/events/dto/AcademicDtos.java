package com.college.events.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AcademicDtos {
    private AcademicDtos() {}

    public record SubmitScoreCard(
            @NotBlank @Size(max = 64) String verifiedIdentifier,
            @NotBlank @Size(max = 20) String semester,
            @NotBlank @Size(max = 20) String academicYear,
            @Size(max = 120) String program,
            @NotNull @DecimalMin("0.0") @DecimalMax("10.0") Double sgpa,
            @NotNull @DecimalMin("0.0") @DecimalMax("10.0") Double cgpa,
            @Pattern(regexp = "PASS|PROMOTED|REAPPEAR") String resultStatus,
            @Size(max = 2000) String pdfUrl,
            @NotBlank @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}") String requestKey,
            @Size(max = 500) String correctionReason) {}

    public record ReviewScoreCard(boolean publish, @Size(max = 500) String reason) {}

    public record BatchPreviewCommand(
            @NotBlank @Size(max = 120) String department,
            @NotBlank @Size(max = 500000) String csvContent) {}

    public record BatchSubmitCommand(
            @NotBlank @Size(max = 120) String department,
            @NotBlank @Size(max = 500000) String csvContent,
            @NotBlank @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}") String requestKey) {}

    public record ReviewBatchCommand(
            boolean publish,
            @jakarta.validation.constraints.PositiveOrZero long expectedVersion,
            @Size(max = 500) String reason) {}
}

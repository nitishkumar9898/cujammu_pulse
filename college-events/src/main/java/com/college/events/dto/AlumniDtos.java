package com.college.events.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

/** Explicit projections: no user entity, enrollment, email, phone or authentication state. */
public final class AlumniDtos {
    private AlumniDtos() { }

    public record ProfileUpdate(
            @PositiveOrZero long expectedVersion,
            @NotBlank @Size(max = 80) String degree,
            @Min(1900) @Max(2200) int graduationYear,
            @Size(max = 120) String company, @Size(max = 100) String designation,
            @Size(max = 100) String location, @Size(max = 250) String linkedin,
            @Size(max = 2000) String bio, boolean mentor, boolean refer,
            boolean directoryVisible, boolean showCareer, boolean showLocation,
            boolean showBio, boolean showLinkedIn) { }

    public record Verification(@PositiveOrZero long expectedVersion, boolean verified) { }
    public record JobUpdate(
            @NotBlank @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String requestKey,
            @NotBlank @Size(max = 120) String companyName,
            @NotBlank @Size(max = 120) String jobTitle,
            @NotBlank @Pattern(regexp = "FULL_TIME|INTERNSHIP|REMOTE|HYBRID") String jobType,
            @Size(max = 100) String location,
            @NotBlank @Size(max = 4000) String description,
            @NotBlank @Size(max = 250) String linkOrEmail,
            @NotBlank @Size(max = 80) String targetDepartment) { }
    public record JobState(@PositiveOrZero long expectedVersion, boolean active) { }

    public record DirectoryEntry(Long id, String displayName, String department, int graduationYear,
            String degree, String currentCompany, String currentDesignation, String location,
            String linkedInUrl, String bio, boolean willingToMentor, boolean willingToRefer) { }

    public record Profile(Long id, long version, String displayName, String department, int graduationYear,
            String degree, String currentCompany, String currentDesignation, String location,
            String linkedInUrl, String bio, boolean willingToMentor, boolean willingToRefer,
            boolean verifiedAlumni, boolean directoryVisible, boolean showCareer, boolean showLocation,
            boolean showBio, boolean showLinkedIn) { }

    public record Job(Long id, long version, String companyName, String jobTitle, String jobType,
            String location, String description, String applicationLinkOrEmail, String targetDepartment,
            boolean active, LocalDateTime postedAt) { }

    public record PageResult<T>(List<T> items, int page, boolean hasNext) { }
}

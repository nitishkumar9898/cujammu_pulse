package com.college.events.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public final class HelpdeskDtos {
    private HelpdeskDtos() { }
    public static final String UUID_PATTERN="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
    public record Create(@NotBlank @Pattern(regexp=UUID_PATTERN) String requestKey,
        @NotBlank @Size(max=150) String subject,@NotBlank @Size(max=4000) String description,
        @NotBlank @Pattern(regexp="EVENT_REGISTRATION|LIBRARY_PASS|HOSTEL_LEAVE|CERTIFICATE_ISSUE|TECH_SUPPORT|GENERAL") String category,
        @NotBlank @Pattern(regexp="LOW|MEDIUM|HIGH|URGENT") String requestedPriority) { }
    public record Reply(@NotBlank @Pattern(regexp=UUID_PATTERN) String requestKey,@PositiveOrZero long expectedVersion,
        @NotBlank @Size(max=4000) String body) { }
    public record Update(@NotBlank @Pattern(regexp=UUID_PATTERN) String requestKey,@PositiveOrZero long expectedVersion,
        @NotBlank @Pattern(regexp="OPEN|IN_PROGRESS|RESOLVED|CLOSED") String status,
        @NotBlank @Pattern(regexp="LOW|MEDIUM|HIGH|URGENT") String priority,
        @NotBlank @Pattern(regexp="TECHNICAL|STUDENT_AFFAIRS|LIBRARY|HOSTEL|EXAMINATION|EVENTS") String assignedOffice,
        @Positive Long assignedStaffId,@Size(max=2000) String resolutionNotes) { }
    public record OwnerAction(@NotBlank @Pattern(regexp=UUID_PATTERN) String requestKey,@PositiveOrZero long expectedVersion,
        @NotBlank @Size(max=2000) String reason) { }
    public record LegacyLink(@NotBlank @Pattern(regexp=UUID_PATTERN) String requestKey,@PositiveOrZero long expectedVersion,
        @NotBlank @Size(max=32) String ticketNumber) { }
    public record Ticket(String id,long version,String ticketNumber,String subject,String description,String category,
        String priority,String requestedPriority,String status,String assignedOffice,Long assignedStaffId,boolean escalated,
        String resolutionNotes,LocalDateTime createdAt,LocalDateTime resolvedAt,boolean canManage,boolean canReopen) { }
    public record Entry(String id,String kind,String authorLabel,String body,LocalDateTime occurredAt) { }
    public record PageResult<T>(List<T> items,int page,boolean hasNext) { }
}

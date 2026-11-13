package com.college.events.dto;
import jakarta.validation.constraints.*;
import java.time.*;
public final class VolunteerDtos {
 private VolunteerDtos() {}
 public record TaskView(long id,long eventId,long revision,String title,String description,String category,String status,double hoursLogged,Double requestedHours,boolean certificateAwarded,String assignedUserName,LocalDateTime completedAt,Instant reportedShiftStart,Instant reportedShiftEnd,String completionEvidence,String reviewReason,boolean canAssign,boolean canSubmit,boolean canApprove) {}
 public record EligibleVolunteer(long userId,String name,String verifiedIdentifier) {}
 public record CreateTask(@NotBlank @Size(max=36) String requestKey,@NotBlank @Size(max=120) String title,@Size(max=2000) String description,@Size(max=50) String category) {}
 public record Assign(@NotBlank @Size(max=36) String requestKey,@NotNull @PositiveOrZero Long expectedRevision,@Positive long userId) {}
 public record Complete(@NotBlank @Size(max=36) String requestKey,@NotNull @PositiveOrZero Long expectedRevision,@DecimalMin("0.25") @DecimalMax("24.0") double hoursLogged,@NotNull Instant shiftStart,@NotNull Instant shiftEnd,@NotBlank @Size(max=1000) String evidence) {}
 public record Review(@NotBlank @Size(max=36) String requestKey,@NotNull @PositiveOrZero Long expectedRevision,@NotBlank @Size(max=500) String reason) {}
}

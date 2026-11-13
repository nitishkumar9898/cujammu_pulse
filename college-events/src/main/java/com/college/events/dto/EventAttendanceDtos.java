package com.college.events.dto;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;

public final class EventAttendanceDtos {
    private EventAttendanceDtos() {}

    public record IssueCredential(@NotBlank @Pattern(regexp = "ENTRY|ATTENDANCE_CHECKPOINT") String action) {}
    public record CredentialView(String action, String credential, Instant expiresAt, long credentialVersion) {}

    public record CreateIntent(@NotBlank @Size(max = 512) String credential,
                               @NotBlank @Pattern(regexp = "ENTRY|ATTENDANCE_CHECKPOINT") String action,
                               @NotBlank @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String requestKey) {}
    public record IntentCreated(String intentKey, String action, String challengeCode, Instant expiresAt, String status) {}
    public record PendingIntent(String intentKey, String eventTitle, String action, Instant expiresAt, String status) {}
    public record PendingIntents(List<PendingIntent> items) {}

    public record ConfirmIntent(@NotBlank @Pattern(regexp = "[0-9]{6}") String challengeCode,
                                @NotBlank @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String requestKey) {}
    public record Receipt(long evidenceId, String action, String status, Instant occurredAt) {}
}

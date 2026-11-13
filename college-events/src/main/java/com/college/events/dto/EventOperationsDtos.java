package com.college.events.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public final class EventOperationsDtos {
    private EventOperationsDtos() {}
    public record Configure(@PositiveOrZero long expectedRevision,
            @NotBlank @Pattern(regexp = "SINGLE_QR|DUAL_QR|NO_QR") String attendanceMode,
            @NotBlank @Pattern(regexp = "ENTRY_ONLY|ENTRY_AND_ATTENDANCE") String singleQrPolicy,
            @Pattern(regexp = "UNREVIEWED|POINT_ATTENDANCE") String certificateEvidencePolicy) {
        public Configure(long expectedRevision, String attendanceMode, String singleQrPolicy) {
            this(expectedRevision, attendanceMode, singleQrPolicy, "UNREVIEWED");
        }
    }
    public record Recruitment(@PositiveOrZero long expectedRevision, boolean open, @Min(1) @Max(500) int maxApplicants) {}
    public record Apply() {}
    public record Decision(@PositiveOrZero long expectedRevision,
            @NotBlank @Pattern(regexp = "ACCEPT|REJECT") String decision) {}
    public record OperationsView(long revision, String attendanceMode, String singleQrPolicy,
            boolean recruitmentOpen, int maxVolunteerApplicants, boolean scannerMasterEnabled, String scannerScope, long scannerEpoch, long manualEpoch,
            LocalDateTime scannerOpensAt, LocalDateTime scannerClosesAt, boolean manualWindowOpen,
            LocalDateTime manualOpensAt, LocalDateTime manualClosesAt,
            String certificateEvidencePolicy, long certificatePolicyVersion) {}
    public record EventSummary(long id, String title, String status, LocalDateTime eventDate, long operationsRevision) {}
    public record EventSummaries(List<EventSummary> items) {}
    public record EventSummaryPage(List<EventSummary> items, int page, boolean hasNext) {}
    public record ScheduleChangeView(long id, long changeNumber, String changeType,
            LocalDateTime previousDate, LocalDateTime replacementDate, String reason,
            String actorRole, LocalDateTime recordedAt) {}
    public record Postpone(@NotNull @Future LocalDateTime newDate, @NotBlank @Size(max = 300) String reason,
            @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String requestKey,
            @Min(0) Long expectedRevision) {
        public Postpone(LocalDateTime newDate, String reason) { this(newDate, reason, null, null); }
        public Postpone(LocalDateTime newDate, String reason, String requestKey) { this(newDate, reason, requestKey, null); }
    }
    public record Cancel(@Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String requestKey,
            @Min(0) Long expectedRevision) {
        public Cancel(String requestKey) { this(requestKey, null); }
    }
    public record VolunteerView(long id, long userId, long revision, String status, String identifier, String displayName,
            String department, LocalDateTime appliedAt, LocalDateTime decidedAt) {}
    public record VolunteerList(List<VolunteerView> items) {}
    public record OwnVolunteerApplication(long id, long revision, String status, long eventId,
            String eventTitle, String eventStatus, LocalDateTime eventDate,
            LocalDateTime appliedAt, LocalDateTime decidedAt) {}
    public record OwnVolunteerApplicationPage(List<OwnVolunteerApplication> items, int page, int size, boolean hasNext) {}
    public record ScannerControl(@PositiveOrZero long expectedRevision,
            @NotBlank @Pattern(regexp = "OFF|ALL|SELECTED") String scope,
            @NotNull @Size(max = 4) List<@Pattern(regexp = "ENTRY|ATTENDANCE_CHECKPOINT|EXIT") String> actions,
            @FutureOrPresent LocalDateTime opensAt, @Future LocalDateTime closesAt) {}
    public record ManualWindow(@PositiveOrZero long expectedRevision, boolean open,
            @FutureOrPresent LocalDateTime opensAt, @Future LocalDateTime closesAt,
            @NotBlank @Size(max = 240) String reason) {}
    public record Delegate(@PositiveOrZero long expectedRevision, @NotNull @Size(min = 1, max = 4)
            List<@Pattern(regexp = "ENTRY|ATTENDANCE_CHECKPOINT|EXIT") String> actions) {}
    public record ManualDelegate(@PositiveOrZero long expectedRevision, @NotNull @Size(min = 1, max = 4)
            List<@Pattern(regexp = "ENTRY|ATTENDANCE_CHECKPOINT|EXIT") String> actions,
            @NotBlank @Size(max = 240) String reason, @NotNull @Future LocalDateTime expiresAt) {}
    public record ExportDelegate(@PositiveOrZero long expectedRevision, @NotBlank @Size(max = 240) String reason,
            @NotNull @Future LocalDateTime expiresAt) {}
    public record Revoke(@PositiveOrZero long expectedRevision) {}
    public record GrantView(long userId, String grantType, List<String> actions, boolean enabled, long epoch, LocalDateTime expiresAt) {}
    public record GrantList(List<GrantView> items) {}
    public record CapabilityView(boolean qrScannerEnabled, boolean manualAttendanceEnabled, List<String> qrActions, List<String> manualActions, long scannerEpoch) {}
    public record ManualCheckIn(@NotBlank @Pattern(regexp = "[A-Za-z0-9/-]{1,64}") String verifiedIdentifier,
            @NotBlank @Pattern(regexp = "PHONE_UNAVAILABLE|QR_UNAVAILABLE|ACCESSIBILITY_SUPPORT|DEVICE_FAILURE|OTHER") String reasonCode,
            @NotBlank @Pattern(regexp = "[0-9a-fA-F-]{36}") String idempotencyKey) {}
    public record ManualCheckInReceipt(long evidenceId, String action, String identifier, String displayName,
            String status, java.time.Instant occurredAt) {}
}

package com.college.events.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public final class CertificateDtos {
    private CertificateDtos() {}
    public record Finalize(@PositiveOrZero long expectedOperationsRevision,
            @NotBlank @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String requestKey,
            @NotBlank @Size(max = 240) String reason) {}
    public record Finalization(long eventId, long revision, String status, String certificateType,
            long policyVersion, int issuedCount, int ineligibleCount, int unresolvedCount, LocalDateTime finalizedAt) {}
    public record Item(long registrationId, long eventId, String eventTitle, String certificateNumber,
            String certificateType, LocalDateTime issuedAt, String downloadUrl) {}
    public record Own(List<Item> items, int page, boolean hasNext) {}
    public record Verification(boolean valid, String certificateType, Long policyVersion) {}
}

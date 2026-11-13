package com.college.events.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public final class LostFoundDtos {
    private LostFoundDtos() { }
    public record Report(@NotBlank @Pattern(regexp=HelpdeskDtos.UUID_PATTERN) String requestKey,
            @NotBlank @Size(max=120) String title,
            @NotBlank @Pattern(regexp="ELECTRONICS|ID_CARDS_DOCS|BAGS_WALLETS|KEYS|CLOTHING|OTHER") String category,
            @NotBlank @Size(max=2000) String description, @NotBlank @Size(max=150) String locationFound) { }
    public record Claim(@NotBlank @Pattern(regexp=HelpdeskDtos.UUID_PATTERN) String requestKey,
            @PositiveOrZero long expectedVersion, @NotBlank @Size(max=2000) String evidence) { }
    public record Handover(@NotBlank @Pattern(regexp=HelpdeskDtos.UUID_PATTERN) String requestKey,
            @Positive long claimId, @PositiveOrZero long expectedVersion,
            @NotBlank @Size(max=500) String verificationReason) { }
    public record Item(long id, long version, String title, String category, String description,
            String locationFound, String status, LocalDateTime reportedAt, boolean canReview) { }
    public record ClaimView(long id, long itemId, String status, String evidence, LocalDateTime submittedAt) { }
    public record Page(List<Item> items, int page, boolean hasNext) { }
}

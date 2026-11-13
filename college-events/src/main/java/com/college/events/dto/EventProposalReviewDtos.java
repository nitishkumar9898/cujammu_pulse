package com.college.events.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public final class EventProposalReviewDtos {
    private EventProposalReviewDtos() {}
    public record Assign(@NotNull @PositiveOrZero Long expectedRevision, @NotNull @Positive Long reviewerId) {}
    public record Approve(@NotNull @PositiveOrZero Long expectedRevision) {}
    public record Reject(@NotNull @PositiveOrZero Long expectedRevision, @NotBlank @Size(max=500) String reason) {}
    public record Proposal(long id, String title, String description, String venue, LocalDateTime eventDate,
            LocalDateTime eventEndsAt, String organizerName, String status, long revision, Long assignedReviewerId,
            String assignedReviewerName, boolean assignedReviewerActive, boolean canAssign, boolean canDecide, boolean canPublish,
            int maxSeats, double fees, String targetDepartment, String categoryName, boolean generateCertificate,
            boolean hasBanner, boolean hasCertificateTemplate) {}
    public record Queue(List<Proposal> items, int page, boolean hasNext) {}
    public record Reviewer(long id, String name) {}
    public record Reviewers(List<Reviewer> items, int page, boolean hasNext) {}
    public record OwnProposal(long id, String title, String venue, LocalDateTime eventDate, String status,
            long revision, String rejectionReason, LocalDateTime submittedAt) {}
    public record OwnQueue(List<OwnProposal> items, int page, boolean hasNext) {}
    public record EditableProposal(long id, String title, String description, long categoryId, String venue,
            LocalDateTime eventDate, LocalDateTime eventEndsAt, int maxSeats, double fees, String contactEmail,
            String targetDepartment, boolean generateCertificate, long revision, boolean hasBanner,
            boolean hasCertificateTemplate) {}
    public record Update(@NotNull @PositiveOrZero Long expectedRevision, @NotBlank @Size(min=3,max=200) String title,
            @NotBlank @Size(min=20,max=10000) String description, @NotNull @Positive Long categoryId,
            @NotBlank @Size(max=200) String venue, @NotNull @Future LocalDateTime eventDate,
            LocalDateTime eventEndsAt, @NotNull @Min(1) @Max(10000) Integer maxSeats,
            @DecimalMin("0.0") Double fees, @Email @Size(max=254) String contactEmail,
            @Size(max=160) String targetDepartment, @NotNull Boolean generateCertificate) {}
    public record Submit(@NotNull java.util.UUID requestKey, @NotBlank @Size(min=3,max=200) String title,
            @NotBlank @Size(min=20,max=10000) String description, @NotNull @Positive Long categoryId,
            @NotBlank @Size(max=200) String venue, @NotNull @Future LocalDateTime eventDate,
            LocalDateTime eventEndsAt, @NotNull @Min(1) @Max(10000) Integer maxSeats,
            @DecimalMin("0.0") Double fees, @Email @Size(max=254) String contactEmail,
            @Size(max=160) String targetDepartment, @NotNull Boolean generateCertificate) {}
    public record SubmitReceipt(long id, String status, long revision, boolean replayed) {}
    public record ProposalCategory(long id, String name) {}
}

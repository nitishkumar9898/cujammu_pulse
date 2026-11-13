package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "events", indexes = {
    @Index(name = "idx_event_status_date", columnList = "status, eventDate"),
    @Index(name = "idx_event_organizer", columnList = "organizer_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Monotonic persisted revision used to reject notices from an earlier event state. */
    @Version
    @Column(nullable = false)
    @Builder.Default
    private long lifecycleVersion = 0L;
    /** Proposal decision input revision, used only for exact no-write decision replay. */
    private Long lastProposalDecisionRevision;
    /** Proposal publication input revision, used only for exact no-write publish retries. */
    private Long lastPublicationDecisionRevision;
    private LocalDateTime publishedAt;
    private Long publishedByUserId;
    private LocalDateTime proposalSubmittedAt;
    /** Explicit private review assignment. Null legacy assignments never confer decision authority. */
    private Long assignedReviewerId;
    private Long reviewerAssignedByUserId;
    private LocalDateTime reviewerAssignedAt;
    private Long lastReviewerAssignmentRevision;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "organizer_id", nullable = false)
    private User organizer;

    @Column(nullable = false)
    private String venue;

    @Column(nullable = false)
    private LocalDateTime eventDate;

    /** Explicit campus-local finish time. Null means unknown, never an inferred duration. */
    private LocalDateTime eventEndsAt;

    @Column(nullable = false)
    private Integer maxSeats;

    @Builder.Default
    private Double fees = 0.0;

    private String contactEmail;

    private String bannerImage;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private EventStatus status = EventStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(columnDefinition = "TEXT")
    private String adminNote;

    /** "ALL" or specific department name (e.g. "CSE") for smart email notifications */
    @Builder.Default
    private String targetDepartment = "ALL";

    /** Optional registration deadline (auto-hide if past) */
    private LocalDateTime registrationDeadline;

    /** Reason provided when organizer postpones the event */
    @Column(columnDefinition = "TEXT")
    private String postponeReason;

    /** Timestamp when organizer cancelled the event */
    private LocalDateTime cancelledAt;

    @Column(nullable = false, columnDefinition = "boolean default true")
    @Builder.Default
    private boolean generateCertificate = true;

    private String certificateTemplatePath;

    @Column(length = 30)
    @Builder.Default
    private String eventScope = "WHOLE_UNIVERSITY"; // DEPARTMENT, INTER_DEPARTMENT, HOSTEL, CLUB, WHOLE_UNIVERSITY, INTER_COLLEGE

    @Column(length = 20)
    @Builder.Default
    private String targetDegree = "ALL"; // ALL, UG, PG, PHD

    @Column(length = 20)
    @Builder.Default
    private String targetYear = "ALL"; // ALL, 1ST, 2ND, 3RD, 4TH

    private String targetSemester;

    @Builder.Default
    private boolean allowOtherDepartments = true;

    @Column(length = 30)
    @Builder.Default
    private String approvalPolicy = "MANUAL_REVIEW"; // MANUAL_REVIEW, AUTO_APPROVE

    @Column(length = 30)
    @Builder.Default
    private String attendanceMode = "DUAL_QR"; // DUAL_QR, SINGLE_QR, MANUAL, NONE

    @Column(length = 64)
    private String exitQrToken;

    @Builder.Default
    private int minDwellTimePercent = 80;


    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime approvedAt;
    /** Explicit manager closure provenance; legacy auto-completed rows remain unverified. */
    private LocalDateTime completedAt;
    private Long completedByUserId;

    // Helper methods
    public boolean isUpcoming() {
        return eventDate != null && eventDate.isAfter(LocalDateTime.now())
               && status != EventStatus.CANCELLED;
    }

    public boolean isPast() {
        return eventDate != null && eventDate.isBefore(LocalDateTime.now());
    }

    public boolean isCancelled() {
        return status == EventStatus.CANCELLED;
    }
}


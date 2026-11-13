package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** Academic scorecard with private PDF attachment; obeys CF-30 30-day media expiry. */
@Entity
@Table(name = "student_score_cards", indexes = {
    @Index(name = "idx_sc_roll", columnList = "rollNumber"),
    @Index(name = "idx_sc_dept_sem", columnList = "departmentName, semester")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentScoreCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String rollNumber;

    @Column(nullable = false)
    private String studentName;

    @Column(nullable = false)
    private String departmentName;

    private String programName;

    @Column(nullable = false, length = 20)
    private String semester;

    @Column(nullable = false, length = 20)
    private String academicYear;

    private Double sgpa;

    private Double cgpa;

    @Column(length = 20)
    @Builder.Default
    private String resultStatus = "PASS"; // PASS, PROMOTED, REAPPEAR

    @Column(name = "score_card_pdf_url", length = 500)
    private String scoreCardPdfUrl;

    /** CF-30 applies to a separately accepted file, not the lifetime of numeric result records. */
    @Column(name = "uploaded_accepted_at")
    private LocalDateTime uploadedAcceptedAt;

    /** File expiry only; null means no managed PDF has been attached. */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /** Publication requires recorded independent review; legacy records need a reviewed migration. */
    @Column(nullable = false, length = 24, columnDefinition = "varchar(24) default 'PENDING_REVIEW'")
    @Builder.Default
    private String publicationStatus = "PENDING_REVIEW";

    @Column(length = 255)
    private String reviewedBy;

    private LocalDateTime reviewedAt;

    @Column(length = 500)
    private String reviewReason;

    @Column(nullable = false, columnDefinition = "integer default 1")
    @Builder.Default
    private int publicationVersion = 1;

    @Column(unique = true, length = 36)
    private String submissionRequestKey;

    @Column(length = 500)
    private String correctionReason;

    @Column(length = 36)
    private String batchRequestKey;

    private Integer batchRowNumber;

    @Builder.Default
    private boolean pushedToDashboard = false;

    @Builder.Default
    private boolean pushedToEmail = false;

    private String uploadedBy;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime pushedAt;
}

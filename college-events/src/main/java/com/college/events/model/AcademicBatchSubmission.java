package com.college.events.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/** Stores an idempotency receipt and integrity digest, never the uploaded CSV content. */
@Entity
@Table(name = "academic_batch_submissions", indexes = {
        @Index(name = "idx_academic_batch_owner", columnList = "submittedBy, createdAt")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AcademicBatchSubmission {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String requestKey;

    @Column(nullable = false, length = 64)
    private String contentDigest;

    @Column(nullable = false, length = 120)
    private String departmentName;

    @Column(nullable = false, length = 255)
    private String submittedBy;

    @Column(nullable = false)
    private int rowCount;

    @Column(nullable = false, length = 24)
    @Builder.Default
    private String status = "PENDING_REVIEW";

    @jakarta.persistence.Version
    private long version;

    @CreationTimestamp
    @Column(updatable = false, nullable = false)
    private LocalDateTime createdAt;
}

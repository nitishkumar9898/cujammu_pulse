package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "library_gate_logs", indexes = {
    @Index(name = "idx_lgl_user_status", columnList = "user_id, status"),
    @Index(name = "idx_lgl_date", columnList = "logDate"),
    @Index(name = "idx_lgl_type", columnList = "sessionType")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LibraryGateLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Serializes entry/exit/repair decisions for a single durable visit. */
    @Version
    @Builder.Default
    private long revision = 0L;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 30)
    private String sessionType; // STUDY, BOOK_ISSUE_RETURN

    @Column(nullable = false)
    private LocalDateTime entryTime;

    private LocalDateTime exitTime;

    private Long durationSeconds;

    private String durationFormatted; // "02h 45m 12s"

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "ACTIVE"; // ACTIVE, COMPLETED, AUTO_CLOSED_UNSCANNED

    @Column(nullable = false)
    private LocalDate logDate;

    @Builder.Default
    private int mismatchAttemptCount = 0;

    private String academicLevel; // UG, PG, PHD, FACULTY, STAFF

    private String departmentName;

    private String rollNumberOrEmpId;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
    @Column(length=36) private String safeExitRequestKey;
    @Column(length=64) private String safeExitPayloadHash;
}

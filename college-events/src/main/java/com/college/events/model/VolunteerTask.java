package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "volunteer_tasks", indexes = {
    @Index(name = "idx_vt_event", columnList = "eventId"),
    @Index(name = "idx_vt_user", columnList = "user_id"),
    @Index(name = "idx_vt_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VolunteerTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Builder.Default
    private long revision = 0L;

    @Column(nullable = false)
    private Long eventId;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 50, nullable = false)
    @Builder.Default
    private String category = "GENERAL"; // REGISTRATION_DESK, STAGE_MANAGEMENT, CROWD_CONTROL, HOSPITALITY, TECHNICAL_SUPPORT

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    private User assignedUser;

    private String assignedUserName;
    private java.time.Instant assignedAt;

    @Column(length = 20, nullable = false)
    @Builder.Default
    private String status = "PENDING"; // PENDING, ASSIGNED, IN_PROGRESS, COMPLETED

    @Builder.Default
    private double hoursLogged = 0.0;

    @Builder.Default
    private boolean certificateAwarded = false;

    private Double requestedHours;
    private LocalDateTime completionRequestedAt;
    private Long approvedByUserId;
    private java.time.Instant reportedShiftStart;
    private java.time.Instant reportedShiftEnd;
    @Column(length = 1000) private String completionEvidence;
    @Column(length = 500) private String reviewReason;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;
}

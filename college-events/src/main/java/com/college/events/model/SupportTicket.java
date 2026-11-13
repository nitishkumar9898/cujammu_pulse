package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "support_tickets", uniqueConstraints = @UniqueConstraint(name = "uq_support_create_request", columnNames = {"user_id", "request_key"}), indexes = {
    @Index(name = "idx_st_ticket_no", columnList = "ticketNumber", unique = true),
    @Index(name = "idx_st_status", columnList = "status"),
    @Index(name = "idx_st_category", columnList = "category")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupportTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String ticketNumber; // e.g. "CUJ-TKT-2026-1001"

    @Column(unique = true, length = 36)
    private String publicId;

    @Column(name = "request_key", length = 36, updatable = false)
    private String requestKey;

    @Column(length = 64, updatable = false)
    private String requestDigest;

    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long version;

    @Column(nullable = false, length = 150)
    private String subject;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(length = 50, nullable = false)
    @Builder.Default
    private String category = "GENERAL"; // EVENT_REGISTRATION, LIBRARY_PASS, HOSTEL_LEAVE, CERTIFICATE_ISSUE, TECH_SUPPORT, GENERAL

    @Column(length = 20, nullable = false)
    @Builder.Default
    private String priority = "MEDIUM"; // LOW, MEDIUM, HIGH, URGENT

    @Column(length = 20, nullable = false)
    @Builder.Default
    private String status = "OPEN"; // OPEN, IN_PROGRESS, RESOLVED, CLOSED

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    private User createdBy;

    private String createdByName;
    private String createdByEmail;

    private String assignedTo;

    @Column(length = 40)
    private String assignedOffice;

    private Long assignedStaffId;

    @Column(length = 20)
    private String requestedPriority;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean escalated;

    @Column(columnDefinition = "TEXT")
    private String resolutionNotes;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime resolvedAt;

    private LocalDateTime lastActivityAt;
}

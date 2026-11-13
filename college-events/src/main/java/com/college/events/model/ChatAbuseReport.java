package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** Abuse report for chat messages; reviewed by administrators with scoped hold before purge. */
@Entity
@Table(name = "chat_abuse_reports", indexes = {
    @Index(name = "icar_group_id", columnList = "group_id"),
    @Index(name = "icar_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatAbuseReport {
    public enum ReportStatus { PENDING, REVIEWED, RESOLVED, HIDDEN_HOLD }
    public enum ReviewedBy { ADMIN, MODERATOR }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private ChatGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_user_id", nullable = false)
    private User reporter;

    @Column(nullable = false, length = 20)
    private String reason; // SPAM, HARASSMENT, ILLEGAL, OTHER

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamp default current_timestamp")
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private ReportStatus status = ReportStatus.PENDING; // PENDING, REVIEWED, RESOLVED, HIDDEN_HOLD

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ReviewedBy reviewedBy;

    private LocalDateTime reviewedAt;

    @Column(columnDefinition = "text")
    private String resolvedReason;
}


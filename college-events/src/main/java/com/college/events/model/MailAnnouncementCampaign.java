package com.college.events.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.time.LocalDateTime;

/** Bounded immutable audience snapshot for resumable event announcement fan-out. */
@Entity
@Table(name = "mail_announcement_campaigns", indexes = {
        @Index(name = "idx_mail_campaign_status", columnList = "status,createdAt"),
        @Index(name = "idx_mail_campaign_terminal", columnList = "status,terminalAt")})
@Getter @Setter @NoArgsConstructor
public class MailAnnouncementCampaign {
    public enum Status { QUEUED, PROCESSING, COMPLETED, SUPPRESSED }
    @Id @Column(length=36, updatable=false) private String id;
    @Column(nullable=false, updatable=false) private Long eventId;
    @Column(nullable=false, updatable=false) private LocalDateTime eventDate;
    @Column(nullable=false, updatable=false) private Long eventVersion;
    @Column(updatable=false) private Long verifiedDepartmentId;
    @Column(nullable=false, updatable=false) private int audienceSize;
    @Column(nullable=false) private int processedCount;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=16) private Status status;
    @Column(nullable=false, updatable=false) private Instant createdAt;
    /** Recipient membership is erased no later than this operational deadline. */
    @Column(nullable=false, updatable=false) private Instant expiresAt;
    private Instant terminalAt;
}

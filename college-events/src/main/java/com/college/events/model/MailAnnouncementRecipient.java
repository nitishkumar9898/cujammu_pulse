package com.college.events.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Minimal immutable member of a frozen campaign audience; no email or profile fields copied. */
@Entity
@Table(name="mail_announcement_recipients",
        uniqueConstraints=@UniqueConstraint(name="uk_mail_campaign_recipient", columnNames={"campaignId","recipientUserId"}),
        indexes=@Index(name="idx_mail_campaign_recipient_pending", columnList="campaignId,status,recipientUserId"))
@Getter @Setter @NoArgsConstructor
public class MailAnnouncementRecipient {
    public enum Status { PENDING, ENQUEUED, SUPPRESSED }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false, updatable=false, length=36) private String campaignId;
    @Column(nullable=false, updatable=false) private Long recipientUserId;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=16) private Status status;
}

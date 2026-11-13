package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.time.LocalDateTime;

/** Bounded private delivery receipt. No HTML, credential, free text or domain entity is retained. */
@Entity
@Table(name="mail_deliveries", indexes={
    @Index(name="idx_mail_due", columnList="status,nextAttemptAt"),
    @Index(name="idx_mail_lease", columnList="status,leaseUntil"),
    @Index(name="idx_mail_expiry", columnList="expiresAt")})
@Getter @Setter @NoArgsConstructor
public class MailDelivery {
    public enum Kind { REGISTRATION, WAITLIST, PROMOTION, WITHDRAWAL, EVENT_APPROVED,
        EVENT_REJECTED, EVENT_CANCELLED, ATTENDANCE_UPDATE, EVENT_POSTPONED, EVENT_ANNOUNCEMENT, CERTIFICATE }
    public enum Status { QUEUED, LEASED, RETRY, PROVIDER_ACCEPTED, SUPPRESSED, DEAD_LETTER, EXPIRED }
    @Id @Column(length=36, updatable=false) private String id;
    @Version private long version;
    @Column(nullable=false, updatable=false) private Long recipientUserId;
    @Column(length=254) private String recipient;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=32,updatable=false) private Kind kind;
    /** Optional safe source reference used to suppress event notices after the source changes. */
    @Column(updatable=false) private Long sourceEventId;
    @Column(updatable=false) private LocalDateTime sourceEventDate;
    @Column(updatable=false) private Long sourceEventVersion;
    /** Optional source reference for registration lifecycle notices. */
    @Column(updatable=false) private Long sourceRegistrationId;
    /** Optional audience scope revalidated under the user-row lock before campaign mail claim. */
    @Enumerated(EnumType.STRING) @Column(length=24, updatable=false) private Role requiredRecipientRole;
    @Column(updatable=false) private Long requiredVerifiedDepartmentId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) private Status status;
    @Column(nullable=false,updatable=false) private Instant createdAt;
    @Column(nullable=false,updatable=false) private Instant expiresAt;
    private Instant nextAttemptAt;
    private Instant leaseUntil;
    @Column(length=36) private String leaseToken;
    private int attempts;
    private Instant completedAt;
    @Column(length=64) private String outcomeCode;
}

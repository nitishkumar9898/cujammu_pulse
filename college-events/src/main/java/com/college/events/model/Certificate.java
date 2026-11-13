package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "certificates", uniqueConstraints = @UniqueConstraint(name = "uk_certificate_registration", columnNames = "registration_id"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Certificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "registration_id", nullable = false)
    private Registration registration;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String templateId;

    @Column(unique = true, nullable = false)
    private String certificateNumber;

    private String pdfPath;

    /** Null for unverified legacy rows; only governed point issuance populates this proof. */
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "finalization_id", updatable = false)
    private CertificateFinalization finalization;
    @Column(updatable = false, length = 32) private String certificateType;
    @Column(updatable = false) private Long entryEvidenceId;
    @Column(updatable = false) private Long checkpointEvidenceId;
    @Column(updatable = false) private String recipientNameSnapshot;
    @Column(updatable = false) private String verifiedIdentifierSnapshot;
    @Column(updatable = false) private String eventTitleSnapshot;
    @Column(updatable = false) private LocalDateTime eventDateSnapshot;

    @Builder.Default
    private boolean emailed = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime issuedAt;
}

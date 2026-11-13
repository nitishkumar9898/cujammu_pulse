package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** One bounded, committed point-attendance issuance job; later corrections require reviewed revisions. */
@Entity
@Table(name = "certificate_finalizations", uniqueConstraints = {
        @UniqueConstraint(name = "uk_certificate_finalization_event", columnNames = "event_id"),
        @UniqueConstraint(name = "uk_certificate_finalization_request", columnNames = "request_key")})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CertificateFinalization {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Version private long revision;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "event_id", nullable = false, updatable = false) private Event event;
    @Column(name = "request_key", nullable = false, updatable = false, length = 36) private String requestKey;
    @Column(nullable = false, updatable = false, length = 64) private String commandDigest;
    @Column(nullable = false, updatable = false) private long finalizedByUserId;
    @Column(nullable = false, updatable = false) private long operationsRevision;
    @Column(nullable = false, updatable = false) private long policyVersion;
    @Column(nullable = false, updatable = false, length = 24) private String attendanceMode;
    @Column(nullable = false, updatable = false, length = 24) private String singleQrPolicy;
    @Column(nullable = false, updatable = false, length = 32) private String evidencePolicy;
    @Column(nullable = false, length = 32) private String status;
    private int issuedCount;
    private int ineligibleCount;
    private int unresolvedCount;
    @Column(nullable = false, updatable = false) private LocalDateTime finalizedAt;
}

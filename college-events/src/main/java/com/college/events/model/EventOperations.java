package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** Per-event control aggregate. It contains no participant identifiers or QR material. */
@Entity
@Table(name = "event_operations", uniqueConstraints = @UniqueConstraint(name = "uk_event_operations_event", columnNames = "event_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EventOperations {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "event_id", nullable = false, unique = true) private Event event;
    @Version private long revision;
    @Column(nullable = false, length = 16) @Builder.Default private String attendanceMode = "DUAL_QR";
    @Column(nullable = false, length = 24) @Builder.Default private String singleQrPolicy = "ENTRY_ONLY";
    @Column(nullable = false, length = 32, columnDefinition = "varchar(32) default 'UNREVIEWED'")
    @Builder.Default private String certificateEvidencePolicy = "UNREVIEWED";
    @Column(nullable = false, columnDefinition = "bigint default 0")
    @Builder.Default private long certificatePolicyVersion = 0;
    @Builder.Default private boolean recruitmentOpen = false;
    @Column(nullable = false) @Builder.Default private int maxVolunteerApplicants = 100;
    @Builder.Default private boolean scannerMasterEnabled = false;
    @Column(nullable = false, length = 12) @Builder.Default private String scannerScope = "OFF";
    @Column(nullable = false, length = 48) @Builder.Default private String scannerActions = "ENTRY";
    private LocalDateTime scannerOpensAt;
    private LocalDateTime scannerClosesAt;
    @Builder.Default private boolean manualWindowOpen = false;
    private LocalDateTime manualOpensAt;
    private LocalDateTime manualClosesAt;
    @Column(length = 240) private String manualWindowReason;
    @Builder.Default private long scannerEpoch = 0;
    @Builder.Default private long manualEpoch = 0;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist void created() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate void updated() { updatedAt = LocalDateTime.now(); }
}

package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "dual_attendances", indexes = {
    @Index(name = "idx_da_registration", columnList = "registration_id", unique = true),
    @Index(name = "idx_da_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DualAttendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "registration_id", unique = true, nullable = false)
    private Registration registration;

    private LocalDateTime entryScannedAt;

    private String entryScannedBy;

    private LocalDateTime exitScannedAt;

    private String exitScannedBy;

    private Long dwellTimeSeconds;

    private String dwellTimeFormatted; // e.g. "02h 15m 00s"

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "ONLY_ENTRY"; // FULL_ATTENDANCE, EARLY_DEPARTURE, ONLY_ENTRY

    @Builder.Default
    private boolean certificateEligible = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}

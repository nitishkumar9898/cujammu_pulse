package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "hostel_gate_passes", indexes = {
    @Index(name = "idx_hgp_user", columnList = "user_id"),
    @Index(name = "idx_hgp_hostel", columnList = "hostel_id"),
    @Index(name = "idx_hgp_status", columnList = "status"),
    @Index(name = "idx_hgp_qr", columnList = "qrToken", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HostelGatePass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Builder.Default
    private long revision = 0L;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "hostel_id", nullable = false)
    private Hostel hostel;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String passType = "DAY_OUT"; // DAY_OUT, NIGHT_OUT, LEAVE

    @Column(nullable = false)
    private String destination;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String reason;

    @Column(nullable = false)
    private LocalDateTime expectedExitTime;

    @Column(nullable = false)
    private LocalDateTime expectedReturnTime;

    private LocalDateTime actualExitTime;

    private LocalDateTime actualReturnTime;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "PENDING"; // PENDING, APPROVED, REJECTED, ACTIVE_OUT, RETURNED_ON_TIME, LATE_ENTRY

    private String wardenApprovedBy;

    private String rejectionReason;

    @Column(unique = true, length = 64)
    private String qrToken;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}

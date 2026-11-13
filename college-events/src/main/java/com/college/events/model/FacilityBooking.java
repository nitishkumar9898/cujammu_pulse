package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "facility_bookings", indexes = {
    @Index(name = "idx_fb_facility_time", columnList = "facility_id, startTime, endTime"),
    @Index(name = "idx_fb_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FacilityBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "facility_id", nullable = false)
    private Facility facility;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private Event event;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "booked_by_user_id", nullable = false)
    private User bookedBy;

    private String department;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private LocalDateTime endTime;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "CONFIRMED"; // PENDING, CONFIRMED, CANCELLED, PREEMPTED

    @Builder.Default
    private int priorityLevel = 50; // 100=VC/Admin, 75=Dean, 50=Dept, 25=Club

    private String preemptionReason;

    /** Client retry key; null for legacy rows. */
    @Column(unique = true, length = 36)
    private String requestKey;

    /** Canonical non-secret command fingerprint used to reject key reuse with changed content. */
    @Column(length = 64)
    private String requestDigest;

    @Version
    private long version;

    private LocalDateTime reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    private User reviewedBy;

    @Column(length = 300)
    private String reviewReason;

    @Builder.Default
    private int requestedMics = 0;

    @Builder.Default
    private boolean requestedProjector = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}

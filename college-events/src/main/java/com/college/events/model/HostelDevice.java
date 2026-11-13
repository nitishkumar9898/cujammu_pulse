package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** Registered gate-scanning device bound to a hostel and warden operator. */
@Entity
@Table(name = "hostel_devices", indexes = {
    @Index(name = "idx_device_hostel", columnList = "hostel_id"),
    @Index(name = "idx_device_operator", columnList = "operator_id"),
    @Index(name = "idx_device_active", columnList = "active,hostel_id")
})
@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class HostelDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Builder.Default
    private long revision = 0L;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hostel_id", nullable = false)
    private Hostel hostel;

    /** Existing unbound devices remain unprovisioned until an authorized migration assigns an operator. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operator_id")
    private User operator;

    @Column(nullable = false, length = 128)
    private String deviceId; // opaque client/device identifier

    @Column(nullable = false, length = 64)
    private String platform; // "ANDROID", "IOS", "WEB"

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    public void deactivate() { this.active = false; }
}

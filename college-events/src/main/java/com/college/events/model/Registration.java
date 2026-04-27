package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "registrations",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "event_id"}),
       indexes = {
           @Index(name = "idx_reg_qr_token", columnList = "qrToken", unique = true),
           @Index(name = "idx_reg_status", columnList = "status"),
           @Index(name = "idx_reg_event", columnList = "event_id")
       })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Registration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private RegistrationStatus status = RegistrationStatus.REGISTERED;

    @Column(unique = true)
    private String qrToken;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime registeredAt;

    private LocalDateTime cancelledAt;
}

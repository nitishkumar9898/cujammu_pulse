package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** Independent event-scoped scanner/manual capability. */
@Entity
@Table(name = "event_operator_grants", uniqueConstraints = @UniqueConstraint(name = "uk_event_operator_grant", columnNames = {"event_id", "user_id", "grant_type"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EventOperatorGrant {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "event_id", nullable = false) private Event event;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(name = "grant_type", nullable = false, length = 12) private String grantType;
    @Column(nullable = false, length = 48) private String actions;
    @Column(length = 240) private String reason;
    @Column(nullable = false) private boolean enabled;
    @Column(nullable = false) private long epoch;
    private LocalDateTime expiresAt;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist void created() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate void updated() { updatedAt = LocalDateTime.now(); }
}

package com.college.events.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

/** Event-scoped readiness obligation; distinct from volunteer shift/certificate tasks. */
@Entity
@Table(name = "event_readiness_tasks", indexes = {
        @Index(name = "idx_ert_event_due", columnList = "event_id, due_at")
}, uniqueConstraints = @UniqueConstraint(name = "uk_ert_event_request", columnNames = {"event_id", "request_key"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReadinessTask {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "owner_user_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdBy;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false, length = 24)
    private String category;

    @Column(nullable = false, length = 20)
    @Builder.Default private String status = "OPEN";

    @Column(length = 300)
    private String completionNote;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "request_key", nullable = false, length = 36)
    private String requestKey;

    @Version
    @Column(nullable = false)
    @Builder.Default private long revision = 0;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist void onCreate() { Instant now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
}

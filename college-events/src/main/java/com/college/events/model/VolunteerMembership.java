package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** Roster membership is separate from event registration and never grants scanning by itself. */
@Entity
@Table(name = "volunteer_memberships", uniqueConstraints = @UniqueConstraint(name = "uk_volunteer_event_subject", columnNames = {"event_id", "user_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VolunteerMembership {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "event_id", nullable = false) private Event event;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false) private User user;
    /** APPLIED, ACCEPTED, REJECTED, REMOVED. */
    @Column(nullable = false, length = 12) @Builder.Default private String status = "APPLIED";
    /** Verified subject identifier copied only at application time; preserve leading zeroes. */
    @Column(nullable = false, length = 64) private String verifiedIdentifier;
    @Version private long revision;
    @Column(nullable = false, updatable = false) private LocalDateTime appliedAt;
    private LocalDateTime decidedAt;
    @PrePersist void created() { appliedAt = LocalDateTime.now(); }
}

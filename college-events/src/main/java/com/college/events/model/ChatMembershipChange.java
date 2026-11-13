package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/** Append-only provenance, private to institutional administration. */
@Entity @Table(name = "chat_membership_changes")
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class ChatMembershipChange {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, updatable = false) private long groupId;
    @Column(nullable = false, updatable = false) private long userId;
    @Column(nullable = false, updatable = false) private long actorId;
    @Column(nullable = false, updatable = false) private long revision;
    @Column(nullable = false, updatable = false) private boolean active;
    @Column(nullable = false, updatable = false, length = 120) private String sourceReference;
    @Column(nullable = false, updatable = false, length = 500) private String reason;
    @Column(nullable = false, updatable = false) private Instant changedAt;
}

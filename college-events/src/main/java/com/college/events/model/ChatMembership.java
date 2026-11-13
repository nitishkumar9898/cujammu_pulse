package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;

/** Explicit chat-only membership; never inferred from editable profile department. */
@Entity
@Table(name = "chat_memberships", uniqueConstraints = @UniqueConstraint(name = "uq_chat_member", columnNames = {"group_id", "user_id"}))
@Getter @Setter @NoArgsConstructor
public class ChatMembership {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "group_id", nullable = false, updatable = false) private Long groupId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", updatable = false) private User user;
    @Version private long revision;
    @Column(nullable = false) private boolean active;
}

package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** Chat group with moderator-controlled posting and night-silence. */
@Entity
@Table(name = "chat_groups", indexes = {
    @Index(name = "idx_cg_type", columnList = "groupType"),
    @Index(name = "idx_cg_event", columnList = "relatedEventId")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 30)
    private String groupType; // PERMANENT_DEPARTMENT, PERMANENT_UNIVERSITY, PERMANENT_FACULTY, EPHEMERAL_EVENT, CLUB

    private Long relatedEventId;

    private String relatedDepartment;

    @Builder.Default
    private boolean onlyAdminsCanPost = false;

    @Builder.Default
    private boolean nightSilenceEnabled = true;

    private String iconUrl;

    @Column(name = "moderator_user_id")
    private Long moderatorUserId;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
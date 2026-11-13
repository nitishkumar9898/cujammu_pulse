package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "event_teams", indexes = {
    @Index(name = "idx_team_event", columnList = "event_id"),
    @Index(name = "idx_team_code", columnList = "inviteCode", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Builder.Default
    private long revision = 0L;

    @Column(nullable = false, columnDefinition = "bigint default 0")
    @Builder.Default
    private long rosterChanges = 0L;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "leader_id", nullable = false)
    private User leader;

    @Column(unique = true, length = 16, nullable = false)
    private String inviteCode; // e.g. "CUJ-TEAM-4921"

    @Builder.Default
    private int minSize = 2;

    @Builder.Default
    private int maxSize = 5;

    @Builder.Default
    private boolean locked = false;

    @OneToMany(mappedBy = "team", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<TeamMember> members = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}

package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "event_judge_assignments", uniqueConstraints = @UniqueConstraint(name = "uk_event_judge", columnNames = {"event_id", "judge_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EventJudgeAssignment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "event_id", nullable = false) private Long eventId;
    @Column(name = "judge_id", nullable = false) private Long judgeId;
    @Column(nullable = false) @Builder.Default private boolean active = true;
    @Column(nullable = false, length = 40) @Builder.Default private String rubricVersion = "FOUR_CRITERIA_25_V1";
    @Version @Builder.Default private long revision = 0L;
    @CreationTimestamp @Column(updatable = false) private LocalDateTime assignedAt;
}

package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** Immutable score snapshot used for corrections, appeals and audit replay. */
@Entity
@Table(name = "judge_score_revisions", uniqueConstraints = @UniqueConstraint(
        name = "uk_judge_score_revision", columnNames = {"score_id", "score_revision"}),
        indexes = @Index(name = "idx_judge_score_revision_event", columnList = "event_id,round_number,participant_id"))
@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class JudgeScoreRevision {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "score_id", nullable = false, updatable = false) private JudgeScore score;
    @Column(name = "event_id", nullable = false, updatable = false) private Long eventId;
    @Column(name = "judge_id", nullable = false, updatable = false) private Long judgeId;
    @Column(name = "participant_id", nullable = false, updatable = false) private Long participantId;
    @Column(name = "participant_name", nullable = false, updatable = false, length = 120) private String participantName;
    @Column(name = "round_number", nullable = false, updatable = false) private int roundNumber;
    @Column(name = "score_revision", nullable = false, updatable = false) private long scoreRevision;
    @Column(name = "change_type", nullable = false, length = 24, updatable = false) private String changeType;
    @Column(nullable = false, updatable = false) private double innovationScore;
    @Column(nullable = false, updatable = false) private double technicalDepth;
    @Column(nullable = false, updatable = false) private double executionScore;
    @Column(nullable = false, updatable = false) private double presentationScore;
    @Column(nullable = false, updatable = false) private double totalScore;
    @Column(columnDefinition = "TEXT", updatable = false) private String feedbackNotes;
    @Column(nullable = false, length = 40, updatable = false) private String rubricVersion;
    @Column(length = 36, updatable = false) private String requestKey;
    @CreationTimestamp @Column(updatable = false) private LocalDateTime recordedAt;
}

package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "judge_scores", indexes = {
    @Index(name = "idx_js_event_round", columnList = "eventId, roundNumber"),
    @Index(name = "idx_js_participant", columnList = "participantId")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JudgeScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Builder.Default
    private long revision = 0L;

    @Column(nullable = false)
    private Long eventId;

    @Column(nullable = false)
    private Long judgeId;

    private String judgeName;

    @Column(nullable = false)
    private Long participantId;

    @Column(nullable = false)
    private String participantName;

    @Builder.Default
    private int roundNumber = 1; // 1 = Prelims, 2 = Semi, 3 = Finals

    @Column(nullable = false, length = 40)
    @Builder.Default
    private String rubricVersion = "FOUR_CRITERIA_25_V1";

    @Column(length = 36)
    private String requestKey;

    @Column(length = 64)
    private String requestHash;

    @Builder.Default
    private double innovationScore = 0.0; // 0 - 25

    @Builder.Default
    private double technicalDepth = 0.0;  // 0 - 25

    @Builder.Default
    private double executionScore = 0.0;   // 0 - 25

    @Builder.Default
    private double presentationScore = 0.0; // 0 - 25

    @Column(nullable = false)
    private double totalScore; // 0 - 100

    @Column(columnDefinition = "TEXT")
    private String feedbackNotes;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime scoredAt;
}

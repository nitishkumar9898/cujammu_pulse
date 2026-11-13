package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "sports_commentaries", indexes = {
    @Index(name = "idx_sc_match", columnList = "match_id, id DESC")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SportsCommentary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_id", nullable = false)
    private Long matchId;

    @Column(length = 20)
    private String timelineMark; // e.g. "18.4" or "84'" or "Set 2 - 19:18"

    @Column(columnDefinition = "TEXT", nullable = false)
    private String commentaryText;

    @Column(length = 30)
    private String scoreEvent; // "SIX", "FOUR", "WICKET", "GOAL", "RED_CARD", "POINT"

    @Builder.Default
    private boolean isImportant = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime timestamp;
}

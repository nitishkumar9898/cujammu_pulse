package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "pulse_posts", indexes = {
    @Index(name = "idx_pp_created", columnList = "createdAt DESC"),
    @Index(name = "idx_pp_category", columnList = "category"),
    @Index(name = "idx_pp_author", columnList = "author_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PulsePost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    private String mediaUrl;

    @Column(length = 30)
    @Builder.Default
    private String mediaType = "NONE"; // NONE, IMAGE, PDF, BANNER, VICTORY_SHOWCASE

    @Column(length = 50)
    @Builder.Default
    private String category = "GENERAL"; // GENERAL, EVENT_UPDATE, VICTORY_BANNER, ALUMNI_CORNER, CLUB_NEWS, ACADEMIC_NOTICE

    @Builder.Default
    private boolean isWinnerAnnouncement = false;

    private Long relatedEventId;
    private String relatedEventTitle;

    private String winnerName;
    private String winningPrize;

    @Builder.Default
    private int likesCount = 0;

    @Builder.Default
    private int commentsCount = 0;

    @Builder.Default
    private int sharesCount = 0;

    @Builder.Default
    private boolean pinned = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}

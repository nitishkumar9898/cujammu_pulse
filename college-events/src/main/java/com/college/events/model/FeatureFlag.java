package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "feature_flags", indexes = {
    @Index(name = "idx_ff_key", columnList = "featureKey", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeatureFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The control-plane revision is independent from the wall-clock audit timestamp.
     * Administrators must present this value when changing a switch so a stale browser
     * cannot silently overwrite a newer safety decision.
     */
    @Version
    @Builder.Default
    private long revision = 0L;

    @Column(nullable = false, unique = true, length = 60)
    private String featureKey; // e.g. "MODULE_SOCIAL_PULSE", "MODULE_LIVE_SPORTS", "MODULE_ALUMNI"

    @Column(nullable = false, length = 100)
    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 50, nullable = false)
    @Builder.Default
    private String category = "CORE"; // ACADEMIC, SOCIAL, SPORTS, RESIDENTIAL, LOGISTICS, ADMIN

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(length = 100)
    private String updatedBy;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}

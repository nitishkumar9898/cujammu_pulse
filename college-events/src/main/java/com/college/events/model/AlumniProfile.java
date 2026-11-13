package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "alumni_profiles", indexes = {
    @Index(name = "idx_ap_dept", columnList = "department"),
    @Index(name = "idx_ap_grad_year", columnList = "graduationYear")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlumniProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, length = 80)
    private String department;

    @Column(nullable = false)
    private int graduationYear;

    @Column(length = 80)
    private String degree; // e.g. "M.Tech CSE", "MBA", "Integrated M.Sc"

    @Column(length = 120)
    private String currentCompany; // e.g. "Google", "Amazon", "ISRO", "Govt of J&K"

    @Column(length = 100)
    private String currentDesignation; // e.g. "Software Development Engineer II"

    @Column(length = 100)
    private String location; // e.g. "Bengaluru, India"

    private String linkedInUrl;

    @Builder.Default
    private boolean willingToMentor = false;

    @Builder.Default
    private boolean willingToRefer = false;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Builder.Default
    private boolean verifiedAlumni = false;

    // Nullable additions preserve old rows; null is always interpreted as private.
    private Boolean directoryVisible;
    private Boolean showCareer;
    private Boolean showLocation;
    private Boolean showBio;
    private Boolean showLinkedIn;
    private Long verifiedById;
    private LocalDateTime verifiedAt;

    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long version;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}

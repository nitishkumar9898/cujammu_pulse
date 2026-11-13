package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "alumni_job_postings", indexes = {
    @Index(name = "idx_ajp_dept", columnList = "targetDepartment"),
    @Index(name = "idx_ajp_active", columnList = "active")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlumniJobPosting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "posted_by_id", nullable = false)
    private User postedBy;

    @Column(nullable = false, length = 120)
    private String companyName;

    @Column(nullable = false, length = 120)
    private String jobTitle;

    @Column(length = 40, nullable = false)
    @Builder.Default
    private String jobType = "FULL_TIME"; // FULL_TIME, INTERNSHIP, REMOTE, HYBRID

    @Column(length = 100)
    private String location;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(length = 250)
    private String applicationLinkOrEmail;

    @Column(length = 80)
    private String targetDepartment; // e.g. "CSIT", "ALL"

    @Builder.Default
    private boolean active = true;

    @Column(length = 36)
    private String requestKey;

    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long version;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime postedAt;
}

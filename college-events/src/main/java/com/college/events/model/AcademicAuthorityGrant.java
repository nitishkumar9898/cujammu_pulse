package com.college.events.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "academic_authority_grants", uniqueConstraints = @UniqueConstraint(
        name = "uq_academic_authority_scope", columnNames = {"user_id", "capability", "department_name"}),
        indexes = @Index(name = "idx_academic_authority_lookup", columnList = "user_id, capability, department_name, enabled"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AcademicAuthorityGrant {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(nullable = false, length = 24)
    private String capability;
    @Column(name = "department_name", nullable = false, length = 120)
    private String departmentName;
    @Column(nullable = false) @Builder.Default
    private boolean enabled = true;
    @Column(nullable = false, length = 255)
    private String grantedBy;
    @CreationTimestamp @Column(updatable = false)
    private LocalDateTime createdAt;
    private LocalDateTime revokedAt;
}

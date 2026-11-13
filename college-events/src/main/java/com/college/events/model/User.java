package com.college.events.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_user_email", columnList = "email", unique = true),
    @Index(name = "idx_user_role", columnList = "role")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Shared optimistic version: profile writes must not resurrect replaced credentials. */
    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    @JsonIgnore
    @ToString.Exclude
    private long credentialVersion;

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    @JsonIgnore
    @ToString.Exclude
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    private String department;
    /** Office-verified registry binding; self-edited profile department never grants scope. */
    @JsonIgnore @ToString.Exclude
    private Long verifiedDepartmentId;

    private String phone;

    /** Enrollment / Roll number for student QR tickets */
    private String enrollmentNo;

    /** Identifier checked by an authorized administrator against the official student source. */
    @Column(unique = true, length = 64)
    @JsonIgnore
    @ToString.Exclude
    private String verifiedEnrollmentNo;

    @JsonIgnore
    private LocalDateTime enrollmentIdentityVerifiedAt;

    @JsonIgnore
    @ToString.Exclude
    private String enrollmentIdentityVerifiedBy;

    @JsonIgnore
    @ToString.Exclude
    @Column(length = 120)
    private String enrollmentIdentitySourceReference;

    @JsonIgnore
    @ToString.Exclude
    @Column(length = 240)
    private String enrollmentIdentityReviewReason;

    private String hostelName;

    @Column(length = 20)
    @Builder.Default
    private String academicLevel = "UG"; // UG, PG, PHD, FACULTY, STAFF

    @Column(length = 20)
    private String semester; // e.g. "Sem 3"

    private LocalDate courseCompletionDate;

    @Builder.Default
    private boolean isAlumni = false;

    @Builder.Default
    private boolean enabled = true;

    private String profilePicture;

    @Builder.Default
    private int gamificationPoints = 0;

    /** Version-prefixed SHA-256 reset-token digest; never a raw bearer token. */
    @Column(unique = true)
    @JsonIgnore
    @ToString.Exclude
    private String passwordResetToken;

    /** UTC expiry time of the password reset token (30 minutes). */
    @JsonIgnore
    private LocalDateTime passwordResetTokenExpiry;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    // ---- UserDetails Methods ----

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}

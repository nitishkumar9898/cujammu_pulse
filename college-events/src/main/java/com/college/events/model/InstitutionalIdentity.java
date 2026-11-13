package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;

/** Explicitly provisioned federation link. Email and token roles never create a mapping. */
@Entity
@Table(name = "institutional_identities", uniqueConstraints = @UniqueConstraint(
        name = "uk_identity_issuer_subject", columnNames = {"issuer", "subject"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InstitutionalIdentity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 512, updatable = false)
    private String issuer;
    @Column(nullable = false, length = 255, updatable = false)
    private String subject;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;
    @Builder.Default @Column(nullable = false)
    private boolean enabled = false;
    @Version private long version;
}

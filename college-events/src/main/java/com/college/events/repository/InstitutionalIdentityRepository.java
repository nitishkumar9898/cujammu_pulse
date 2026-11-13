package com.college.events.repository;

import com.college.events.model.InstitutionalIdentity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface InstitutionalIdentityRepository extends JpaRepository<InstitutionalIdentity, Long> {
    @EntityGraph(attributePaths = "user")
    Optional<InstitutionalIdentity> findByIssuerAndSubjectAndEnabledTrue(String issuer, String subject);
}

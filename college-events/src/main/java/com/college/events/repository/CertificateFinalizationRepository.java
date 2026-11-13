package com.college.events.repository;
import com.college.events.model.CertificateFinalization;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface CertificateFinalizationRepository extends JpaRepository<CertificateFinalization, Long> {
    Optional<CertificateFinalization> findByEventId(long eventId);
}

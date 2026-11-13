package com.college.events.repository;

import com.college.events.model.HostelGateEvidence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HostelGateEvidenceRepository extends JpaRepository<HostelGateEvidence, Long> {
    Optional<HostelGateEvidence> findByPassIdAndAction(Long passId, String action);
}

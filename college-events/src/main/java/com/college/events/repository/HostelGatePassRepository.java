package com.college.events.repository;

import com.college.events.model.HostelGatePass;
import com.college.events.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HostelGatePassRepository extends JpaRepository<HostelGatePass, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from HostelGatePass p where p.id = :id")
    Optional<HostelGatePass> findForDecision(@org.springframework.data.repository.query.Param("id") Long id);
    List<HostelGatePass> findByUserOrderByCreatedAtDesc(User user);
    List<HostelGatePass> findByHostelIdOrderByCreatedAtDesc(Long hostelId);
    List<HostelGatePass> findByStatusOrderByCreatedAtDesc(String status);
    List<HostelGatePass> findByHostelIdAndStatusOrderByCreatedAtAsc(Long hostelId, String status);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<HostelGatePass> findByQrToken(String qrToken);
}

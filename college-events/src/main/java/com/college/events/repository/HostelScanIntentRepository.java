package com.college.events.repository;

import com.college.events.model.HostelScanIntent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface HostelScanIntentRepository extends JpaRepository<HostelScanIntent, Long> {
    Optional<HostelScanIntent> findByRequestKey(String requestKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from HostelScanIntent i join fetch i.pass p join fetch p.user where i.intentKey = :intentKey")
    Optional<HostelScanIntent> findForConfirmation(String intentKey);

    List<HostelScanIntent> findByPassIdAndStatusOrderByCreatedAtDesc(Long passId, String status);
}

package com.college.events.repository;

import com.college.events.model.HostelPassCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface HostelPassCredentialRepository extends JpaRepository<HostelPassCredential, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from HostelPassCredential c where c.id = :id")
    Optional<HostelPassCredential> findForUpdate(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from HostelPassCredential c join fetch c.pass p join fetch p.hostel where c.tokenDigest = :digest and c.active = true")
    Optional<HostelPassCredential> findActiveForUpdate(String digest);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from HostelPassCredential c where c.pass.id = :passId and c.action = :action and c.active = true")
    Optional<HostelPassCredential> findActiveForUpdate(Long passId, String action);
}

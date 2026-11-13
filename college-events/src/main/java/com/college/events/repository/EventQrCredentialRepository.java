package com.college.events.repository;

import com.college.events.model.EventQrCredential;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface EventQrCredentialRepository extends JpaRepository<EventQrCredential, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from EventQrCredential c join fetch c.registration r join fetch c.event e where c.tokenDigest = :digest")
    Optional<EventQrCredential> findForUpdateByDigest(@Param("digest") String digest);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from EventQrCredential c join fetch c.registration r join fetch c.event e where c.registration.id = :registrationId and c.action = :action")
    Optional<EventQrCredential> findForUpdateByRegistrationAndAction(@Param("registrationId") long registrationId,
                                                                       @Param("action") String action);
}

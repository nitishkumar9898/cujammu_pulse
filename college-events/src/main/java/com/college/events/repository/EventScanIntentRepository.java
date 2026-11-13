package com.college.events.repository;

import com.college.events.model.EventScanIntent;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EventScanIntentRepository extends JpaRepository<EventScanIntent, Long> {
    @Query("select i.event.id from EventScanIntent i where i.intentKey = :intentKey and i.registration.user.id = :ownerId")
    Optional<Long> findOwnedEventId(@Param("intentKey") String intentKey, @Param("ownerId") long ownerId);
    Optional<EventScanIntent> findByEventIdAndRequestKey(long eventId, String requestKey);
    @Query("select case when count(i) > 0 then true else false end from EventScanIntent i where i.credential.id = :credentialId and i.status = 'PENDING' and i.expiresAt > :now")
    boolean existsLivePendingByCredentialId(@Param("credentialId") long credentialId, @Param("now") Instant now);
    @Query("select count(i) from EventScanIntent i where i.credential.id = :credentialId")
    long countByCredentialId(@Param("credentialId") long credentialId);
    @Modifying(flushAutomatically = true)
    @Query("update EventScanIntent i set i.status = 'REVOKED', i.revision = i.revision + 1 where i.credential.id = :credentialId and i.status = 'PENDING'")
    int revokePendingByCredentialId(@Param("credentialId") long credentialId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from EventScanIntent i join fetch i.registration r join fetch i.event e join fetch i.credential c where i.intentKey = :intentKey")
    Optional<EventScanIntent> findForUpdateByIntentKey(@Param("intentKey") String intentKey);

    @Query("select i from EventScanIntent i join fetch i.registration r join fetch i.event e where r.user.id = :userId and i.status = 'PENDING' and i.expiresAt > :now order by i.expiresAt asc")
    List<EventScanIntent> findPendingForOwner(@Param("userId") long userId, @Param("now") Instant now);
}

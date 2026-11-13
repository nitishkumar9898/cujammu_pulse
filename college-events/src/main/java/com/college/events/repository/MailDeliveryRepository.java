package com.college.events.repository;

import com.college.events.model.MailDelivery;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;

public interface MailDeliveryRepository extends JpaRepository<MailDelivery,String> {
    @Query("select m.id from MailDelivery m where ((m.status in :ready and (m.nextAttemptAt <= :now or m.nextAttemptAt is null)) or (m.status = :leased and (m.leaseUntil <= :now or m.leaseUntil is null))) order by m.createdAt, m.id")
    List<String> findDue(@Param("ready") Collection<MailDelivery.Status> ready,
        @Param("leased") MailDelivery.Status leased,@Param("now") Instant now, Pageable page);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MailDelivery m where m.id=:id")
    Optional<MailDelivery> lock(@Param("id") String id);
    long countByStatusIn(Collection<MailDelivery.Status> statuses);
    @Query("select m.id from MailDelivery m where m.expiresAt <= :now and m.recipient is not null order by m.expiresAt")
    List<String> findExpiredPayload(@Param("now") Instant now, Pageable page);
    @Query("select m.id from MailDelivery m where m.completedAt < :before and m.recipient is null order by m.completedAt")
    List<String> findExpiredReceipts(@Param("before") Instant before,Pageable page);
}

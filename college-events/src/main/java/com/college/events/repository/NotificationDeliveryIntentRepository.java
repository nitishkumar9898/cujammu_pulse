package com.college.events.repository;

import com.college.events.model.NotificationDeliveryIntent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import java.util.List;

import java.util.Optional;

public interface NotificationDeliveryIntentRepository extends JpaRepository<NotificationDeliveryIntent, Long> {
    Optional<NotificationDeliveryIntent> findByNotificationIdAndChannel(long notificationId, String channel);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from NotificationDeliveryIntent i where i.status in ('QUEUED','INBOX_CREATED','RETRY_WAIT','CLAIMED') "
            + "and (i.leaseUntil is null or i.leaseUntil < :now) "
            + "and (i.nextAttemptAt is null or i.nextAttemptAt <= :now) order by i.id asc")
    List<NotificationDeliveryIntent> findDueForClaim(@Param("now") LocalDateTime now, Pageable pageable);
}

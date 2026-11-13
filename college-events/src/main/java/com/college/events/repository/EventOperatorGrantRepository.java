package com.college.events.repository;

import com.college.events.model.EventOperatorGrant;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface EventOperatorGrantRepository extends JpaRepository<EventOperatorGrant, Long> {
    Optional<EventOperatorGrant> findByEventIdAndUserIdAndGrantType(long eventId, long userId, String grantType);
    List<EventOperatorGrant> findByEventIdAndGrantType(long eventId, String grantType);
    @Query(value = "select g from EventOperatorGrant g join fetch g.event e where g.user.id = :userId and g.grantType = 'EXPORT' and g.enabled = true and (g.expiresAt is null or g.expiresAt > :now) and e.status in (com.college.events.model.EventStatus.APPROVED, com.college.events.model.EventStatus.COMPLETED) order by e.eventDate desc, e.id asc",
            countQuery = "select count(g) from EventOperatorGrant g where g.user.id = :userId and g.grantType = 'EXPORT' and g.enabled = true and (g.expiresAt is null or g.expiresAt > :now) and g.event.status in (com.college.events.model.EventStatus.APPROVED, com.college.events.model.EventStatus.COMPLETED)")
    Page<EventOperatorGrant> findEnabledExportGrantsForUser(@Param("userId") long userId, @Param("now") LocalDateTime now, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from EventOperatorGrant g where g.event.id = :eventId and g.user.id = :userId and g.grantType = :kind")
    Optional<EventOperatorGrant> findForUpdate(@Param("eventId") long eventId, @Param("userId") long userId, @Param("kind") String kind);
}

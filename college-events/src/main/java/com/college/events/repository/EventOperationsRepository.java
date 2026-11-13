package com.college.events.repository;

import com.college.events.model.EventOperations;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface EventOperationsRepository extends JpaRepository<EventOperations, Long> {
    Optional<EventOperations> findByEventId(Long eventId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from EventOperations o where o.event.id = :eventId")
    Optional<EventOperations> findForUpdate(@Param("eventId") Long eventId);
}

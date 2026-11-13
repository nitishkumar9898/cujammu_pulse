package com.college.events.repository;

import com.college.events.model.SupportTicketCounter;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface SupportTicketCounterRepository extends JpaRepository<SupportTicketCounter,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from SupportTicketCounter c where c.monthKey = :month")
    Optional<SupportTicketCounter> lockMonth(@Param("month") String month);
}

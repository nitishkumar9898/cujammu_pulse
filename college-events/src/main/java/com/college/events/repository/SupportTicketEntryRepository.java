package com.college.events.repository;

import com.college.events.model.SupportTicketEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;

public interface SupportTicketEntryRepository extends JpaRepository<SupportTicketEntry,Long> {
    Optional<SupportTicketEntry> findByTicketIdAndRequestKey(Long id,String requestKey);
    Page<SupportTicketEntry> findByTicketIdOrderByIdAsc(Long id,Pageable pageable);
}

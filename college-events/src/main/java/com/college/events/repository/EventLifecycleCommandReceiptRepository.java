package com.college.events.repository;

import com.college.events.model.EventLifecycleCommandReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EventLifecycleCommandReceiptRepository extends JpaRepository<EventLifecycleCommandReceipt, Long> {
    Optional<EventLifecycleCommandReceipt> findByEventIdAndRequestKey(long eventId, String requestKey);
}

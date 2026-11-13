package com.college.events.repository;

import com.college.events.model.SportsScoreCommandReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SportsScoreCommandReceiptRepository extends JpaRepository<SportsScoreCommandReceipt, Long> {
    Optional<SportsScoreCommandReceipt> findByAggregateTypeAndEventIdAndRequestKey(
            String aggregateType, Long eventId, String requestKey);
}

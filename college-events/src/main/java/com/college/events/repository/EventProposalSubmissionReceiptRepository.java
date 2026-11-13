package com.college.events.repository;

import com.college.events.model.EventProposalSubmissionReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface EventProposalSubmissionReceiptRepository extends JpaRepository<EventProposalSubmissionReceipt, Long> {
    Optional<EventProposalSubmissionReceipt> findByOrganizerIdAndRequestKey(long organizerId, String requestKey);
}

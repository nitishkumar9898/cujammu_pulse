package com.college.events.repository;
import com.college.events.model.ProposalAttachmentReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface ProposalAttachmentReceiptRepository extends JpaRepository<ProposalAttachmentReceipt,Long> {
    Optional<ProposalAttachmentReceipt> findByOwnerIdAndRequestKey(Long ownerId,String requestKey);
    boolean existsByOwnerIdAndEventIdAndKindAndContentHash(Long ownerId,Long eventId,String kind,String contentHash);
}

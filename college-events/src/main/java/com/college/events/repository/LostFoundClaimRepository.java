package com.college.events.repository;

import com.college.events.model.LostFoundClaim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface LostFoundClaimRepository extends JpaRepository<LostFoundClaim,Long> {
    Optional<LostFoundClaim> findByItemIdAndRequestKey(long itemId,String requestKey);
    Optional<LostFoundClaim> findByItemIdAndClaimantId(long itemId,long claimantId);
    Optional<LostFoundClaim> findByItemIdAndHandoverRequestKey(long itemId,String requestKey);
    List<LostFoundClaim> findByItemIdOrderByIdAsc(long itemId,Pageable pageable);
    List<LostFoundClaim> findByItemIdAndClaimantIdOrderByIdAsc(long itemId,long claimantId,Pageable pageable);
    long countByItemId(long itemId);
    List<LostFoundClaim> findByItemIdAndStatusOrderByIdAsc(long itemId,String status);
}

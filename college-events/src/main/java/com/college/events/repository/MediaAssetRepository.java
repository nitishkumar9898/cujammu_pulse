package com.college.events.repository;

import com.college.events.model.MediaAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.*;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {
    Optional<MediaAsset> findByObjectKey(String objectKey);
    boolean existsByOwnerUserIdAndResourceIdAndResourceTypeAndSha256AndIdNot(Long ownerUserId, Long resourceId, String resourceType, String sha256, Long id);
    List<MediaAsset> findByExpiresAtLessThanEqualAndStatusOrderByExpiresAtAsc(Instant time, String status);
}

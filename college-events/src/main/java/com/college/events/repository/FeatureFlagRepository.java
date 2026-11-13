package com.college.events.repository;

import com.college.events.model.FeatureFlag;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, Long> {
    Optional<FeatureFlag> findByFeatureKey(String featureKey);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FeatureFlag f where f.featureKey = :key")
    Optional<FeatureFlag> findByFeatureKeyForUpdate(@Param("key") String featureKey);
    boolean existsByFeatureKey(String featureKey);
    List<FeatureFlag> findByCategory(String category);
    List<FeatureFlag> findAllByOrderByCategoryAscDisplayNameAsc();
}

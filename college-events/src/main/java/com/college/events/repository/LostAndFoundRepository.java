package com.college.events.repository;

import com.college.events.model.LostAndFoundItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

@Repository
public interface LostAndFoundRepository extends JpaRepository<LostAndFoundItem, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from LostAndFoundItem i where i.id = :id")
    Optional<LostAndFoundItem> findForUpdate(@Param("id") Long id);
    Optional<LostAndFoundItem> findByRequestKey(String key);
    Page<LostAndFoundItem> findAllByOrderByIdDesc(Pageable pageable);
    Page<LostAndFoundItem> findByCategoryOrderByIdDesc(String category,Pageable pageable);
    List<LostAndFoundItem> findByStatus(String status);
    List<LostAndFoundItem> findByCategory(String category);
    List<LostAndFoundItem> findByReportedById(Long userId);
    List<LostAndFoundItem> findAllByOrderByReportedAtDesc();
}

package com.college.events.repository;

import com.college.events.model.CustomEventFormResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomEventFormResponseRepository extends JpaRepository<CustomEventFormResponse, Long> {
    List<CustomEventFormResponse> findByEventId(Long eventId);
    Optional<CustomEventFormResponse> findByEventIdAndUserId(Long eventId, Long userId);
    boolean existsByEventIdAndUserId(Long eventId, Long userId);
    int countByEventId(Long eventId);
}

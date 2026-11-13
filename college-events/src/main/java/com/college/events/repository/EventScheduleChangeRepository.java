package com.college.events.repository;

import com.college.events.model.EventScheduleChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventScheduleChangeRepository extends JpaRepository<EventScheduleChange, Long> {
    List<EventScheduleChange> findByEventIdOrderByChangeNumberAsc(long eventId);
    java.util.Optional<EventScheduleChange> findTopByEventIdOrderByChangeNumberDesc(long eventId);
}

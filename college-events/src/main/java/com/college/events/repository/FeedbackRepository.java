package com.college.events.repository;

import com.college.events.model.Event;
import com.college.events.model.Feedback;
import com.college.events.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    List<Feedback> findByEventOrderByCreatedAtDesc(Event event);
    Optional<Feedback> findByUserAndEvent(User user, Event event);
    boolean existsByUserAndEvent(User user, Event event);
    List<Feedback> findByEventOrderByCreatedAtDesc(Event event, Pageable page);
    long countByEvent(Event event);
    @Query("select avg(f.rating) from Feedback f where f.event = :event")
    Double averageForEvent(@Param("event") Event event);
}

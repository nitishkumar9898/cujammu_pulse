package com.college.events.repository;

import com.college.events.model.Event;
import com.college.events.model.Feedback;
import com.college.events.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    List<Feedback> findByEventOrderByCreatedAtDesc(Event event);
    Optional<Feedback> findByUserAndEvent(User user, Event event);
    boolean existsByUserAndEvent(User user, Event event);
}

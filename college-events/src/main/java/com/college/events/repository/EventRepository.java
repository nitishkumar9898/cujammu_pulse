package com.college.events.repository;

import com.college.events.model.Event;
import com.college.events.model.EventStatus;
import com.college.events.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    @Query("SELECT e FROM Event e LEFT JOIN FETCH e.category LEFT JOIN FETCH e.organizer WHERE e.id = :id")
    java.util.Optional<Event> findWithDetailsById(@Param("id") Long id);

    @Query("SELECT e FROM Event e LEFT JOIN FETCH e.category LEFT JOIN FETCH e.organizer WHERE e.status = :status")
    List<Event> findByStatus(@Param("status") EventStatus status);

    @Query("SELECT e FROM Event e LEFT JOIN FETCH e.category LEFT JOIN FETCH e.organizer WHERE e.organizer = :organizer")
    List<Event> findByOrganizer(@Param("organizer") User organizer);

    @Query("SELECT e FROM Event e LEFT JOIN FETCH e.category LEFT JOIN FETCH e.organizer WHERE e.organizer = :organizer AND e.status = :status")
    List<Event> findByOrganizerAndStatus(@Param("organizer") User organizer, @Param("status") EventStatus status);

    @Query("SELECT e FROM Event e LEFT JOIN FETCH e.category WHERE e.status = 'APPROVED' AND e.eventDate > :now ORDER BY e.eventDate ASC")
    List<Event> findUpcomingApprovedEvents(@Param("now") LocalDateTime now);

    @Query("SELECT e FROM Event e LEFT JOIN FETCH e.category WHERE e.status = 'APPROVED' AND e.eventDate <= :now ORDER BY e.eventDate DESC")
    List<Event> findPastApprovedEvents(@Param("now") LocalDateTime now);

    @Query("SELECT e FROM Event e LEFT JOIN FETCH e.category WHERE e.status = 'APPROVED' AND e.eventDate > :now " +
           "AND (LOWER(e.title) LIKE LOWER(CONCAT('%',:keyword,'%')) " +
           "OR LOWER(e.description) LIKE LOWER(CONCAT('%',:keyword,'%')) " +
           "OR LOWER(e.venue) LIKE LOWER(CONCAT('%',:keyword,'%'))) " +
           "ORDER BY e.eventDate ASC")
    List<Event> searchApprovedEvents(@Param("keyword") String keyword, @Param("now") LocalDateTime now);

    @Query("SELECT e FROM Event e LEFT JOIN FETCH e.category WHERE e.status = 'APPROVED' AND e.eventDate > :now " +
           "AND e.category.id = :categoryId ORDER BY e.eventDate ASC")
    List<Event> findByStatusAndCategoryId(@Param("categoryId") Long categoryId, @Param("now") LocalDateTime now);

    @Query("SELECT e FROM Event e LEFT JOIN FETCH e.category WHERE e.status = 'APPROVED' AND e.eventDate > :now " +
           "AND e.category.id = :categoryId " +
           "AND (LOWER(e.title) LIKE LOWER(CONCAT('%',:keyword,'%')) " +
           "OR LOWER(e.description) LIKE LOWER(CONCAT('%',:keyword,'%'))) " +
           "ORDER BY e.eventDate ASC")
    List<Event> searchApprovedEventsByCategory(
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId,
            @Param("now") LocalDateTime now);

    long countByStatus(EventStatus status);

    @Query("SELECT e FROM Event e WHERE e.status = 'APPROVED' ORDER BY e.createdAt DESC")
    List<Event> findRecentApprovedEvents();
}

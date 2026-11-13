package com.college.events.repository;

import com.college.events.model.Event;
import com.college.events.model.EventStatus;
import com.college.events.model.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    org.springframework.data.domain.Slice<Event> findAllByStatus(EventStatus status, Pageable pageable);
    org.springframework.data.domain.Slice<Event> findAllByOrganizerIdOrderByIdDesc(Long organizerId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Event e WHERE e.id = :id")
    java.util.Optional<Event> findForApproval(@Param("id") Long id);

    List<Event> findByStatusOrderByEventDateDesc(EventStatus status, Pageable pageable);

    @Query("SELECT e FROM Event e LEFT JOIN FETCH e.category LEFT JOIN FETCH e.organizer WHERE e.id = :id")
    java.util.Optional<Event> findWithDetailsById(@Param("id") Long id);

    @Query("SELECT e FROM Event e LEFT JOIN FETCH e.category LEFT JOIN FETCH e.organizer WHERE e.status = :status")
    List<Event> findByStatus(@Param("status") EventStatus status);

    @Query("SELECT e FROM Event e LEFT JOIN FETCH e.category LEFT JOIN FETCH e.organizer WHERE e.organizer = :organizer")
    List<Event> findByOrganizer(@Param("organizer") User organizer);

    @Query("SELECT e FROM Event e LEFT JOIN FETCH e.category LEFT JOIN FETCH e.organizer WHERE e.organizer = :organizer AND e.status = :status")
    List<Event> findByOrganizerAndStatus(@Param("organizer") User organizer, @Param("status") EventStatus status);
    @Query("select e from Event e where e.organizer = :organizer and e.status = :status order by e.eventDate desc")
    List<Event> findByOrganizerAndStatus(@Param("organizer") User organizer, @Param("status") EventStatus status, Pageable pageable);

    @Query("SELECT e FROM Event e LEFT JOIN FETCH e.category WHERE e.status = 'APPROVED' AND e.eventDate > :now ORDER BY e.eventDate ASC")
    List<Event> findUpcomingApprovedEvents(@Param("now") LocalDateTime now);

    @Query("SELECT e FROM Event e LEFT JOIN FETCH e.category WHERE e.status = 'APPROVED' AND e.eventDate > :now ORDER BY e.eventDate ASC, e.id ASC")
    List<Event> findUpcomingApprovedEvents(@Param("now") LocalDateTime now, Pageable pageable);

    @Query("SELECT e FROM Event e LEFT JOIN FETCH e.category WHERE e.status = 'APPROVED' AND e.eventDate <= :now ORDER BY e.eventDate DESC")
    List<Event> findPastApprovedEvents(@Param("now") LocalDateTime now);

    @Query("SELECT e FROM Event e LEFT JOIN FETCH e.category WHERE e.status = 'APPROVED' AND e.eventDate <= :now ORDER BY e.eventDate DESC, e.id DESC")
    List<Event> findPastApprovedEvents(@Param("now") LocalDateTime now, Pageable pageable);

    long countByStatusAndEventDateAfter(EventStatus status, LocalDateTime now);

    @Query("SELECT e FROM Event e WHERE e.status = 'APPROVED' AND e.eventDate > :now AND (e.fees IS NULL OR e.fees <= 0) ORDER BY e.eventDate ASC, e.id ASC")
    List<Event> findUpcomingFreeApprovedEvents(@Param("now") LocalDateTime now, Pageable pageable);

    @Query("SELECT e FROM Event e WHERE e.status = 'APPROVED' AND e.eventDate >= :start AND e.eventDate < :end ORDER BY e.eventDate ASC, e.id ASC")
    List<Event> findApprovedEventsInDay(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, Pageable pageable);

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

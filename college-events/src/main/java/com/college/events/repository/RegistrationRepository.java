package com.college.events.repository;

import com.college.events.model.Event;
import com.college.events.model.Registration;
import com.college.events.model.RegistrationStatus;
import com.college.events.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RegistrationRepository extends JpaRepository<Registration, Long> {
    @Query("select r.event.id from Registration r where r.id = :id and r.user.id = :userId")
    Optional<Long> findOwnedEventId(@Param("id") Long id, @Param("userId") Long userId);
    @Query("select r from Registration r join fetch r.user where r.event = :event and r.status in :statuses order by r.registeredAt asc")
    List<Registration> findNotifiableByEvent(@Param("event") Event event,
            @Param("statuses") java.util.Collection<RegistrationStatus> statuses);
    boolean existsByEvent(Event event);
    long countByEvent(Event event);

    boolean existsByUserAndEvent(User user, Event event);
    boolean existsByUserIdAndEventIdAndStatusIn(Long userId, Long eventId, java.util.Collection<RegistrationStatus> statuses);

    @Query("SELECT r FROM Registration r JOIN FETCH r.event JOIN FETCH r.user WHERE r.user = :user AND r.event = :event")
    Optional<Registration> findByUserAndEvent(@Param("user") User user, @Param("event") Event event);

    @Query("SELECT r FROM Registration r JOIN FETCH r.event JOIN FETCH r.user WHERE r.id = :id")
    Optional<Registration> findWithEventById(@Param("id") Long id);


    @Query("SELECT r FROM Registration r JOIN FETCH r.event JOIN FETCH r.user WHERE r.event = :event")
    List<Registration> findByEvent(@Param("event") Event event);

    @Query("SELECT r FROM Registration r JOIN FETCH r.event JOIN FETCH r.user WHERE r.event = :event ORDER BY r.registeredAt ASC")
    List<Registration> findByEventOrderByRegisteredAtAsc(@Param("event") Event event);

    @Query("SELECT r FROM Registration r JOIN FETCH r.event JOIN FETCH r.user WHERE r.user = :user")
    List<Registration> findByUser(@Param("user") User user);

    @Query("SELECT r FROM Registration r JOIN FETCH r.event JOIN FETCH r.user WHERE r.user = :user ORDER BY r.registeredAt DESC")
    List<Registration> findByUserOrderByRegisteredAtDesc(@Param("user") User user);

    @Query("SELECT r FROM Registration r JOIN FETCH r.event e WHERE r.user.id = :userId " +
            "ORDER BY e.eventDate ASC, r.id ASC")
    Slice<Registration> findCalendarPageByUserId(@Param("userId") Long userId, Pageable pageable);

    /** Unknown end times stay candidates, including older starts; never infer a free interval from them. */
    @Query("SELECT r FROM Registration r JOIN FETCH r.event e WHERE r.user.id = :userId " +
            "AND e.id <> :targetId AND r.status IN ('REGISTERED', 'ATTENDED') AND e.status = 'APPROVED' " +
            "AND (:endUnknown = true OR e.eventDate < :targetEnd) " +
            "AND (e.eventEndsAt IS NULL OR e.eventEndsAt <= e.eventDate OR e.eventEndsAt > :targetStart) " +
            "ORDER BY CASE WHEN :endUnknown = false AND e.eventEndsAt > e.eventDate THEN 0 ELSE 1 END, e.eventDate ASC, r.id ASC")
    Slice<Registration> findOwnClashCandidates(@Param("userId") Long userId, @Param("targetId") Long targetId,
            @Param("targetStart") java.time.LocalDateTime targetStart,
            @Param("targetEnd") java.time.LocalDateTime targetEnd,
            @Param("endUnknown") boolean endUnknown, Pageable pageable);

    @Query("SELECT COUNT(r) FROM Registration r WHERE r.event = :event AND r.status IN ('REGISTERED', 'ATTENDED')")
    long countConfirmedByEvent(@Param("event") Event event);

    @Query("SELECT COUNT(r) FROM Registration r WHERE r.event = :event AND r.status = 'WAITLISTED'")
    long countWaitlistedByEvent(@Param("event") Event event);

    Optional<Registration> findFirstByEventAndStatusAndUser_EnabledTrueOrderByIdAsc(
            Event event, RegistrationStatus status);

    @Query("SELECT r FROM Registration r JOIN FETCH r.event JOIN FETCH r.user WHERE r.event = :event AND r.status = :status")
    List<Registration> findByEventAndStatus(@Param("event") Event event, @Param("status") RegistrationStatus status);

    @Query("SELECT COUNT(r) FROM Registration r WHERE r.event = :event AND r.status = :status")
    long countByEventAndStatus(@Param("event") Event event, @Param("status") RegistrationStatus status);

    @Query("SELECT r FROM Registration r JOIN FETCH r.event JOIN FETCH r.user WHERE r.user = :user AND r.status NOT IN ('CANCELLED')")
    List<Registration> findActiveByUser(@Param("user") User user);


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Registration r JOIN r.user u WHERE r.event.id = :eventId AND u.verifiedEnrollmentNo = :identifier AND r.status = com.college.events.model.RegistrationStatus.REGISTERED")
    Optional<Registration> findConfirmedByVerifiedIdentifierForUpdate(@Param("eventId") Long eventId, @Param("identifier") String identifier);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Registration r JOIN FETCH r.event JOIN FETCH r.user WHERE r.id = :id")
    Optional<Registration> findForUpdate(@Param("id") Long id);

    long countByStatus(RegistrationStatus status);
}

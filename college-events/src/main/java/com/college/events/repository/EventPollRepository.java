package com.college.events.repository;

import com.college.events.model.EventPoll;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.time.Instant;
import com.college.events.model.Event;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

@Repository
public interface EventPollRepository extends JpaRepository<EventPoll, Long> {
    List<EventPoll> findByEventId(Long eventId);
    List<EventPoll> findByEventIdAndActiveTrue(Long eventId);

    @Query("select p.eventId from EventPoll p where p.id = :id")
    Optional<Long> findEventId(@Param("id") Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from EventPoll p where p.id = :id")
    Optional<EventPoll> findForUpdate(@Param("id") Long id);
    Optional<EventPoll> findByEventIdAndCreatorIdAndRequestKey(Long eventId, Long creatorId, String requestKey);

    @Query("select p.id from EventPoll p where p.protocolVersion = 1 and p.state = 'OPEN' " +
            "and p.closesAt <= :deadline order by p.closesAt asc, p.id asc")
    List<Long> findExpiredOpenPollIds(@Param("deadline") Instant deadline, Pageable pageable);

    @Query("select p from EventPoll p where p.eventId = :eventId and p.protocolVersion = 1 " +
            "and ((:manager = true and p.state = 'DRAFT') or p.state in ('OPEN', 'CLOSED')) order by p.id desc")
    Slice<EventPoll> findVisible(@Param("eventId") Long eventId, @Param("manager") boolean manager, Pageable pageable);

    @Query("select e from Event e where :admin = true or (:ownerRole = true and e.organizer.id = :userId) " +
            "or (:student = true and exists (select r.id from Registration r where r.event.id = e.id " +
            "and r.user.id = :userId and r.status in ('REGISTERED', 'ATTENDED'))) order by e.id desc")
    Slice<Event> findAccessibleEvents(@Param("userId") Long userId, @Param("admin") boolean admin,
            @Param("ownerRole") boolean ownerRole, @Param("student") boolean student, Pageable pageable);
}

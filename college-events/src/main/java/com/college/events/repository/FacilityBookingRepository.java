package com.college.events.repository;

import com.college.events.model.Facility;
import com.college.events.model.FacilityBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FacilityBookingRepository extends JpaRepository<FacilityBooking, Long> {

    Optional<FacilityBooking> findByRequestKey(String requestKey);

    List<FacilityBooking> findByEventIdOrderByStartTimeAsc(Long eventId);

    @Query("select b from FacilityBooking b where b.event.id = :eventId and b.status in ('PENDING', 'CONFIRMED') order by b.startTime asc")
    List<FacilityBooking> findActiveByEventIdOrderByStartTimeAsc(@Param("eventId") Long eventId);

    Slice<FacilityBooking> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);

    @Query("select b from FacilityBooking b join fetch b.facility where b.bookedBy.id = :userId order by b.createdAt desc")
    Slice<FacilityBooking> findMine(@Param("userId") Long userId, Pageable pageable);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from FacilityBooking b where b.id = :id")
    Optional<FacilityBooking> findForReview(@Param("id") Long id);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from FacilityBooking b where b.id = :id")
    Optional<FacilityBooking> findForUpdate(@Param("id") Long id);

    @Query("select b.facility.id from FacilityBooking b where b.id = :id")
    Optional<Long> findFacilityIdByBookingId(@Param("id") Long id);

    @Query("SELECT b FROM FacilityBooking b WHERE b.facility.id = :facilityId " +
           "AND b.status NOT IN ('CANCELLED', 'PREEMPTED', 'REJECTED') " +
           "AND b.startTime < :endTime AND b.endTime > :startTime")
    List<FacilityBooking> findConflictingBookings(@Param("facilityId") Long facilityId,
                                                @Param("startTime") LocalDateTime startTime,
                                                @Param("endTime") LocalDateTime endTime);

    @Query("SELECT b FROM FacilityBooking b WHERE b.facility.id = :facilityId AND b.id <> :excludedId " +
           "AND b.status NOT IN ('CANCELLED', 'PREEMPTED', 'REJECTED') " +
           "AND b.startTime < :endTime AND b.endTime > :startTime")
    List<FacilityBooking> findConflictingBookingsExcept(@Param("facilityId") Long facilityId,
            @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime,
            @Param("excludedId") Long excludedId);

    List<FacilityBooking> findByFacilityAndStartTimeBetweenOrderByStartTimeAsc(
            Facility facility, LocalDateTime fromDate, LocalDateTime toDate);

    List<FacilityBooking> findByStartTimeBetweenOrderByStartTimeAsc(
            LocalDateTime fromDate, LocalDateTime toDate);
}

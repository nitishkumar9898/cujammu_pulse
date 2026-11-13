package com.college.events.repository;

import com.college.events.model.VolunteerMembership;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface VolunteerMembershipRepository extends JpaRepository<VolunteerMembership, Long> {
    @Query("select count(m) from VolunteerMembership m join m.user u where m.event.id=:eventId and m.status='ACCEPTED' and u.enabled=true and u.role=com.college.events.model.Role.STUDENT and u.verifiedEnrollmentNo=m.verifiedIdentifier and u.verifiedEnrollmentNo<>''")
    long countEligibleAcceptedByEventId(@Param("eventId") Long eventId);
    @Query("select m from VolunteerMembership m join m.user u where m.event.id=:eventId and m.status=:status and u.enabled=true and u.role=com.college.events.model.Role.STUDENT and u.verifiedEnrollmentNo=m.verifiedIdentifier and u.verifiedEnrollmentNo<>''")
    Page<VolunteerMembership> findEligible(@Param("eventId") Long eventId, @Param("status") String status, Pageable page);
    Optional<VolunteerMembership> findByEventIdAndUserId(Long eventId, Long userId);
    @Query(value = "select m from VolunteerMembership m join fetch m.event e where m.user.id=:userId and e.status in :statuses order by m.appliedAt desc, m.id desc",
            countQuery = "select count(m) from VolunteerMembership m where m.user.id=:userId and m.event.status in :statuses")
    Page<VolunteerMembership> findOwnApplications(@Param("userId") Long userId,
            @Param("statuses") Collection<com.college.events.model.EventStatus> statuses, Pageable page);
    @Query("select m from VolunteerMembership m join fetch m.user where m.event.id = :eventId order by m.appliedAt asc")
    List<VolunteerMembership> findByEventId(@Param("eventId") Long eventId);
    long countByEventIdAndStatusIn(Long eventId, Collection<String> statuses);
    @Query(value = "select m from VolunteerMembership m join fetch m.event e where m.user.id = :userId and m.status = 'ACCEPTED' and e.status = com.college.events.model.EventStatus.APPROVED order by m.appliedAt desc",
            countQuery = "select count(m) from VolunteerMembership m where m.user.id = :userId and m.status = 'ACCEPTED' and m.event.status = com.college.events.model.EventStatus.APPROVED")
    Page<VolunteerMembership> findApprovedEventMemberships(@Param("userId") Long userId, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from VolunteerMembership m where m.id = :id")
    Optional<VolunteerMembership> findForUpdate(@Param("id") Long id);
}

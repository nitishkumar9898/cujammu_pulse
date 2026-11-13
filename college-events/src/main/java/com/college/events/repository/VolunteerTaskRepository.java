package com.college.events.repository;

import com.college.events.model.User;
import com.college.events.model.VolunteerTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

import java.util.List;

@Repository
public interface VolunteerTaskRepository extends JpaRepository<VolunteerTask, Long> {
    long countByEventIdAndStatusIn(Long eventId, List<String> statuses);
    long countByEventIdAndStatus(Long eventId, String status);
    @Query("select t.eventId from VolunteerTask t where t.id=:id")
    Optional<Long> findEventId(@Param("id") Long id);
    org.springframework.data.domain.Page<VolunteerTask> findByEventId(Long eventId, org.springframework.data.domain.Pageable page);
    org.springframework.data.domain.Page<VolunteerTask> findByAssignedUserId(Long userId, org.springframework.data.domain.Pageable page);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id=:id")
    Optional<User> lockVolunteer(@Param("id") Long id);
    @Query("select count(t) from VolunteerTask t where t.assignedUser.id=:userId and t.status='COMPLETED' and t.reportedShiftStart < :end and t.reportedShiftEnd > :start and t.id<>:taskId")
    long countCreditedOverlap(@Param("userId") Long userId,@Param("taskId") Long taskId,@Param("start") java.time.Instant start,@Param("end") java.time.Instant end);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from VolunteerTask t where t.id = :id")
    Optional<VolunteerTask> findForUpdate(@Param("id") Long id);
    List<VolunteerTask> findByEventId(Long eventId);
    List<VolunteerTask> findByAssignedUser(User user);
    List<VolunteerTask> findByAssignedUserId(Long userId);
    List<VolunteerTask> findByEventIdAndStatus(Long eventId, String status);
}

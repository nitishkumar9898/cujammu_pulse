package com.college.events.repository;

import com.college.events.model.Event;
import com.college.events.model.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {
    Optional<Team> findByInviteCode(String inviteCode);
    @Query("select t.event.id from Team t where t.inviteCode = :code")
    Optional<Long> findEventIdByCode(@Param("code") String code);
    @Query("select t.event.id from Team t where t.id = :id")
    Optional<Long> findEventIdByTeamId(@Param("id") Long id);
    @Query("select t from Team t where t.event.id = :eventId and exists (select m.id from TeamMember m where m.team = t and m.user.id = :userId)")
    List<Team> findOwnByEvent(@Param("eventId") Long eventId, @Param("userId") Long userId);
    @Query("select t from Team t where exists (select m.id from TeamMember m where m.team = t and m.user.id = :userId) order by t.id desc")
    org.springframework.data.domain.Slice<Team> findOwnHistory(@Param("userId") long userId, org.springframework.data.domain.Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Team t where t.inviteCode = :inviteCode")
    Optional<Team> findByInviteCodeForUpdate(@Param("inviteCode") String inviteCode);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Team t join fetch t.event join fetch t.leader where t.id = :id")
    Optional<Team> findForUpdate(@Param("id") Long id);
    List<Team> findByEvent(Event event);
    List<Team> findByEventId(Long eventId);
    List<Team> findByLeaderId(Long leaderId);
}

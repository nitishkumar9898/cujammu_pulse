package com.college.events.repository;

import com.college.events.model.SportsLiveMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

@Repository
public interface SportsLiveMatchRepository extends JpaRepository<SportsLiveMatch, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from SportsLiveMatch m where m.id = :id")
    Optional<SportsLiveMatch> findForUpdate(Long id);
    List<SportsLiveMatch> findByStatusOrderByUpdatedAtDesc(String status);
    List<SportsLiveMatch> findBySportTypeOrderByCreatedAtDesc(String sportType);
    List<SportsLiveMatch> findAllByOrderByStatusAscUpdatedAtDesc();
    List<SportsLiveMatch> findByEventId(Long eventId);
}

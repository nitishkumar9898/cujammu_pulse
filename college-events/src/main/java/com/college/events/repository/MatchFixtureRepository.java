package com.college.events.repository;

import com.college.events.model.MatchFixture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

@Repository
public interface MatchFixtureRepository extends JpaRepository<MatchFixture, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from MatchFixture f where f.id = :id")
    Optional<MatchFixture> findForUpdate(Long id);
    List<MatchFixture> findByEventId(Long eventId);
    List<MatchFixture> findByEventIdAndRoundNumberOrderByMatchNumberAsc(Long eventId, int roundNumber);
    List<MatchFixture> findByEventIdOrderByRoundNumberAscMatchNumberAsc(Long eventId);
}

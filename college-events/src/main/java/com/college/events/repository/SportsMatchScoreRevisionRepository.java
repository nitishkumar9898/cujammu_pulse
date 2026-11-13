package com.college.events.repository;

import com.college.events.model.SportsMatchScoreRevision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SportsMatchScoreRevisionRepository extends JpaRepository<SportsMatchScoreRevision, Long> {
    List<SportsMatchScoreRevision> findByMatchIdOrderByMatchRevisionAsc(Long matchId);
}

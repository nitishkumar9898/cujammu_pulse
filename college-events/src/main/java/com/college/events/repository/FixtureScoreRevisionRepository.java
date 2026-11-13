package com.college.events.repository;

import com.college.events.model.FixtureScoreRevision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FixtureScoreRevisionRepository extends JpaRepository<FixtureScoreRevision, Long> {
    List<FixtureScoreRevision> findByFixtureIdOrderByFixtureRevisionAsc(Long fixtureId);
}

package com.college.events.repository;

import com.college.events.model.SportsCommentary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SportsCommentaryRepository extends JpaRepository<SportsCommentary, Long> {
    List<SportsCommentary> findByMatchIdOrderByIdDesc(Long matchId);
    List<SportsCommentary> findTop20ByMatchIdOrderByIdDesc(Long matchId);
}

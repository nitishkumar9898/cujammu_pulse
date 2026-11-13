package com.college.events.repository;

import com.college.events.model.PollVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PollVoteRepository extends JpaRepository<PollVote, Long> {
    Optional<PollVote> findByPollIdAndVoterId(Long pollId, Long voterId);
    boolean existsByPollIdAndVoterId(Long pollId, Long voterId);
    List<PollVote> findByPollId(Long pollId);
    long countByPollId(Long pollId);

    @Query("SELECT v.selectedOption, COUNT(v.id) FROM PollVote v WHERE v.pollId = :pollId GROUP BY v.selectedOption")
    List<Object[]> countVotesByOption(@Param("pollId") Long pollId);
}

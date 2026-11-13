package com.college.events.repository;

import com.college.events.model.PulsePost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PulsePostRepository extends JpaRepository<PulsePost, Long> {
    Page<PulsePost> findAllByOrderByPinnedDescCreatedAtDesc(Pageable pageable);
    List<PulsePost> findByCategoryOrderByCreatedAtDesc(String category);
    List<PulsePost> findByIsWinnerAnnouncementTrueOrderByCreatedAtDesc();
    List<PulsePost> findByAuthorIdOrderByCreatedAtDesc(Long authorId);
}

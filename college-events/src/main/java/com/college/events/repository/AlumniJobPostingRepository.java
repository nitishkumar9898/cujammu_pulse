package com.college.events.repository;

import com.college.events.model.AlumniJobPosting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AlumniJobPostingRepository extends JpaRepository<AlumniJobPosting, Long> {
    List<AlumniJobPosting> findByActiveTrueOrderByPostedAtDesc();
    List<AlumniJobPosting> findByTargetDepartmentAndActiveTrueOrderByPostedAtDesc(String department);
    List<AlumniJobPosting> findByPostedById(Long userId);

    @Query("select j from AlumniJobPosting j where j.active = true and j.postedBy.enabled = true " +
           "and exists (select p.id from AlumniProfile p where p.user = j.postedBy and p.verifiedAlumni = true " +
           "and p.verifiedById is not null and p.verifiedById <> p.user.id and p.verifiedAt is not null) " +
           "and (:department is null or j.targetDepartment = :department or j.targetDepartment = 'ALL') " +
           "order by j.postedAt desc, j.id desc")
    Page<AlumniJobPosting> findVisible(@Param("department") String department, Pageable pageable);

    Page<AlumniJobPosting> findByPostedByIdOrderByIdDesc(Long userId, Pageable pageable);
    Optional<AlumniJobPosting> findByPostedByIdAndRequestKey(Long userId, String requestKey);
}

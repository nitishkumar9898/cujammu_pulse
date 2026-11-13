package com.college.events.repository;

import com.college.events.model.AcademicBatchSubmission;
import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AcademicBatchSubmissionRepository extends JpaRepository<AcademicBatchSubmission, Long> {
    Optional<AcademicBatchSubmission> findByRequestKey(String requestKey);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from AcademicBatchSubmission b where b.id = :id")
    Optional<AcademicBatchSubmission> findForReview(@Param("id") Long id);
    Slice<AcademicBatchSubmission> findByStatusAndDepartmentNameInAndSubmittedByNotOrderByCreatedAtAsc(
            String status, List<String> departmentNames, String excludedSubmitter, Pageable pageable);
}

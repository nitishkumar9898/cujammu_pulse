package com.college.events.repository;

import com.college.events.model.StudentScoreCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentScoreCardRepository extends JpaRepository<StudentScoreCard, Long> {
    List<StudentScoreCard> findByRollNumberOrderByPushedAtDesc(String rollNumber);
    Optional<StudentScoreCard> findTopByRollNumberAndSemesterAndAcademicYearOrderByPublicationVersionDesc(
            String rollNumber, String semester, String academicYear);
    Optional<StudentScoreCard> findBySubmissionRequestKey(String submissionRequestKey);
    @Query("select s from StudentScoreCard s where s.id = :id")
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<StudentScoreCard> findForReview(@Param("id") Long id);
    org.springframework.data.domain.Slice<StudentScoreCard> findByPublicationStatusAndDepartmentNameAndBatchRequestKeyIsNullOrderByPushedAtAsc(
            String publicationStatus, String departmentName, org.springframework.data.domain.Pageable pageable);
    @Query("select s from StudentScoreCard s where s.batchRequestKey = :batchKey order by s.batchRowNumber asc")
    List<StudentScoreCard> findByBatchForDisplay(@Param("batchKey") String batchKey);
    @Query("select s from StudentScoreCard s where s.batchRequestKey = :batchKey order by s.batchRowNumber asc")
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    List<StudentScoreCard> findBatchForReview(@Param("batchKey") String batchKey);
    @Query("select s from StudentScoreCard s where s.rollNumber = :roll and s.publicationStatus = 'PUBLISHED' and s.reviewedBy is not null and s.reviewedAt is not null and s.uploadedBy is not null and lower(s.reviewedBy) <> lower(s.uploadedBy) and not exists (select newer.id from StudentScoreCard newer where newer.rollNumber = s.rollNumber and newer.semester = s.semester and newer.academicYear = s.academicYear and newer.publicationVersion > s.publicationVersion and newer.publicationStatus = 'PUBLISHED' and newer.reviewedBy is not null and newer.reviewedAt is not null and newer.uploadedBy is not null and lower(newer.reviewedBy) <> lower(newer.uploadedBy)) order by s.pushedAt desc")
    List<StudentScoreCard> findCurrentPublishedByRollNumber(@Param("roll") String rollNumber);
    List<StudentScoreCard> findByDepartmentNameAndSemesterOrderByPushedAtDesc(
            String departmentName, String semester);
}

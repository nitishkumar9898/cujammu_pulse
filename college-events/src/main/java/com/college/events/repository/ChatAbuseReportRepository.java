package com.college.events.repository;

import com.college.events.model.ChatAbuseReport;
import com.college.events.model.ChatAbuseReport.ReportStatus;
import com.college.events.model.ChatAbuseReport.ReviewedBy;
import com.college.events.model.ChatGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Repository for chat abuse reports. */
@Repository
public interface ChatAbuseReportRepository extends JpaRepository<ChatAbuseReport, Long> {

    Optional<ChatAbuseReport> findFirstByGroupAndStatusOrderByIdAsc(ChatGroup group, ReportStatus status);

    @Transactional
    @Modifying
    @Query("UPDATE ChatAbuseReport r SET r.status = 'REVIEWED', r.reviewedBy = :reviewer, r.reviewedAt = CURRENT_TIMESTAMP "
            + "WHERE r.id = :reportId AND r.status = 'PENDING'")
    int markReviewed(@Param("reportId") Long reportId, @Param("reviewer") ReviewedBy reviewer);

    @Transactional
    @Modifying
    @Query("UPDATE ChatAbuseReport r SET r.status = 'RESOLVED', r.resolvedReason = :reason, r.reviewedBy = :reviewer, r.reviewedAt = CURRENT_TIMESTAMP "
            + "WHERE r.id = :reportId AND r.status IN ('PENDING', 'REVIEWED')")
    int markResolved(@Param("reportId") Long reportId, @Param("reason") String reason, @Param("reviewer") ReviewedBy reviewer);

    @Transactional
    @Modifying
    @Query("UPDATE ChatAbuseReport r SET r.status = 'HIDDEN_HOLD' WHERE r.id = :reportId")
    int applyHiddenHold(@Param("reportId") Long reportId);
}

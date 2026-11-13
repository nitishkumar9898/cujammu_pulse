package com.college.events.service;

import com.college.events.model.*;
import com.college.events.repository.ExamScheduleRepository;
import com.college.events.repository.StudentScoreCardRepository;
import com.college.events.repository.UserRepository;
import com.college.events.security.DomainAccess;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Academic records remain private to the verified institutional subject. */
@Service
@RequiredArgsConstructor
public class CoEAcademicService {
    private final ExamScheduleRepository examScheduleRepository;
    private final StudentScoreCardRepository studentScoreCardRepository;
    private final UserRepository users;
    private final DomainAccess access;
    private final AuditService audit;
    private final AcademicAuthorityService academicAuthority;
    private final NotificationService notifications;
    private final com.college.events.repository.DepartmentRepository departments;

    @Transactional
    public ExamSchedule publishExamSchedule(String title, String departmentName, String programName,
                                            String semester, String academicYear, String pdfUrl,
                                            String ignoredPublishedBy) {
        User actor = access.currentUser();
        validateText(title, 160, "title");
        validateText(departmentName, 120, "department");
        validateText(programName, 120, "program");
        validateText(semester, 20, "semester");
        validateText(academicYear, 20, "academic year");
        rejectUnmanagedFileUrl(pdfUrl);
        requireAcademicCapability(actor, AcademicAuthorityService.Capability.PUBLISH, departmentName);
        ExamSchedule schedule = ExamSchedule.builder().title(title.trim())
                .departmentName(departmentName.trim()).programName(programName.trim())
                .semester(semester.trim()).academicYear(academicYear.trim()).timetablePdfUrl(null)
                .publishedBy(actor.getEmail()).build();
        ExamSchedule saved = examScheduleRepository.save(schedule);
        audit.logAction(actor.getEmail(), actor.getRole().name(), "ACADEMIC_TIMETABLE_PUBLISHED",
                "scheduleId=" + saved.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ScheduleView> getSchedulesByDepartment(String departmentName) {
        if (departmentName == null || departmentName.isBlank() || departmentName.length() > 120)
            throw new IllegalArgumentException("Invalid department");
        return examScheduleRepository.findByDepartmentNameOrderByPublishedAtDesc(departmentName.trim()).stream()
                .map(CoEAcademicService::scheduleView).toList();
    }

    @Transactional(readOnly = true)
    public List<ScheduleView> getAllExamSchedules() {
        return examScheduleRepository.findAllByOrderByPublishedAtDesc().stream()
                .map(CoEAcademicService::scheduleView).toList();
    }

    @Transactional
    public SubmissionReceipt distributeScoreCard(String rollNumber, String program, String semester,
                                                String academicYear, Double sgpa, Double cgpa,
                                                String resultStatus, String pdfUrl, String requestKey,
                                                String correctionReason) {
        User actor = access.currentUser();
        validateText(rollNumber, 64, "verified student identifier");
        validateText(semester, 20, "semester");
        validateText(academicYear, 20, "academic year");
        rejectUnmanagedFileUrl(pdfUrl);
        validateText(requestKey, 36, "submission request key");
        try { requestKey = java.util.UUID.fromString(requestKey).toString(); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Invalid submission request key"); }
        String canonicalTerm = semester.trim();
        String canonicalYear = academicYear.trim();
        String canonicalProgram = clean(program, 120);
        String cleanCorrection = clean(correctionReason, 500);
        String status = resultStatus == null ? "PASS" : resultStatus.trim().toUpperCase(java.util.Locale.ROOT);
        if (!validGrade(sgpa) || !validGrade(cgpa) || !java.util.Set.of("PASS", "PROMOTED", "REAPPEAR").contains(status))
            throw new IllegalArgumentException("Invalid published grade or result status");
        User student = users.findByVerifiedEnrollmentNoForAcademicUpdate(rollNumber.trim()).filter(User::isEnabled)
                .filter(user -> user.getRole() == Role.STUDENT)
                .orElseThrow(() -> new IllegalArgumentException("No active student is mapped to that verified identifier"));
        if (student.getEnrollmentIdentityVerifiedAt() == null) throw DomainAccess.denied();
        String verifiedDepartment=verifiedDepartment(student);
        requireAcademicCapability(actor, AcademicAuthorityService.Capability.PUBLISH, verifiedDepartment);
        // Locking the verified student's row serializes submissions for the same term.
        var replay = studentScoreCardRepository.findBySubmissionRequestKey(requestKey);
        if (replay.isPresent()) {
            StudentScoreCard prior = replay.get();
            if (!actor.getEmail().equals(prior.getUploadedBy()) || !student.getVerifiedEnrollmentNo().equals(prior.getRollNumber())
                    || !canonicalTerm.equals(prior.getSemester()) || !canonicalYear.equals(prior.getAcademicYear())
                    || !java.util.Objects.equals(canonicalProgram, prior.getProgramName()) || !java.util.Objects.equals(sgpa, prior.getSgpa())
                    || !java.util.Objects.equals(cgpa, prior.getCgpa()) || !status.equals(prior.getResultStatus())
                    || !java.util.Objects.equals(cleanCorrection, prior.getCorrectionReason()))
                throw new IllegalStateException("Submission request was reused with different data");
            return new SubmissionReceipt(prior.getId(), prior.getPublicationStatus(), prior.getPublicationVersion());
        }
        var latest = studentScoreCardRepository.findTopByRollNumberAndSemesterAndAcademicYearOrderByPublicationVersionDesc(
                student.getVerifiedEnrollmentNo(), canonicalTerm, canonicalYear);
        if (latest.isPresent() && "PENDING_REVIEW".equals(latest.get().getPublicationStatus()))
            throw new IllegalStateException("Review the pending term submission before submitting another revision");
        if (latest.isPresent() && cleanCorrection == null)
            throw new IllegalArgumentException("A correction reason is required for a revised result");
        int nextVersion = latest.map(card -> card.getPublicationVersion() + 1).orElse(1);
        StudentScoreCard card = StudentScoreCard.builder()
                .rollNumber(student.getVerifiedEnrollmentNo())
                .studentName(student.getName()).departmentName(verifiedDepartment).programName(canonicalProgram)
                .semester(canonicalTerm).academicYear(canonicalYear).publicationVersion(nextVersion)
                .sgpa(sgpa).cgpa(cgpa).resultStatus(status).scoreCardPdfUrl(null)
                .uploadedBy(actor.getEmail()).submissionRequestKey(requestKey).correctionReason(cleanCorrection)
                .publicationStatus("PENDING_REVIEW")
                .build();
        StudentScoreCard saved = studentScoreCardRepository.save(card);
        audit.logAction(actor.getEmail(), actor.getRole().name(), "ACADEMIC_SCORECARD_SUBMITTED",
                "scoreCardId=" + saved.getId() + ";studentId=" + student.getId());
        return new SubmissionReceipt(saved.getId(), saved.getPublicationStatus(), saved.getPublicationVersion());
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public SubmissionReceipt reviewScoreCard(Long scoreCardId, boolean publish, String reason) {
        User reviewer = access.currentUser();
        StudentScoreCard card = studentScoreCardRepository.findForReview(scoreCardId)
                .orElseThrow(DomainAccess::denied);
        requireAcademicCapability(reviewer, AcademicAuthorityService.Capability.REVIEW, card.getDepartmentName());
        if (card.getBatchRequestKey() != null) throw new IllegalStateException("Review this scorecard through its complete batch");
        if (reviewer.getEmail().equalsIgnoreCase(card.getUploadedBy())) throw DomainAccess.denied();
        if (!"PENDING_REVIEW".equals(card.getPublicationStatus()))
            throw new IllegalStateException("This result is no longer awaiting review");
        if (!publish && (reason == null || reason.isBlank() || reason.length() > 500))
            throw new IllegalArgumentException("A bounded reason is required when returning a result");
        if (publish) {
            card.setPublicationStatus("PUBLISHED"); card.setPushedToDashboard(true); card.setReviewedBy(reviewer.getEmail());
            card.setReviewedAt(LocalDateTime.now()); card.setReviewReason(null);
        } else {
            card.setPublicationStatus("REJECTED"); card.setPushedToDashboard(false); card.setReviewedBy(reviewer.getEmail());
            card.setReviewedAt(LocalDateTime.now()); card.setReviewReason(reason.trim());
        }
        StudentScoreCard saved = studentScoreCardRepository.save(card);
        audit.logAction(reviewer.getEmail(), reviewer.getRole().name(), publish ? "ACADEMIC_SCORECARD_PUBLISHED" : "ACADEMIC_SCORECARD_RETURNED",
                "scoreCardId=" + saved.getId());
        if (publish) notifications.createNotification(users.findByVerifiedEnrollmentNo(saved.getRollNumber()).orElseThrow(DomainAccess::denied),
                "An official scorecard has been published to your account.", "/app/");
        return new SubmissionReceipt(saved.getId(), saved.getPublicationStatus(), saved.getPublicationVersion());
    }

    @Transactional(readOnly = true)
    public ReviewPage pendingReviews(String departmentName, int page) {
        User reviewer = access.currentUser();
        requireAcademicCapability(reviewer, AcademicAuthorityService.Capability.REVIEW, departmentName);
        if (page < 0 || page > 100_000) throw new IllegalArgumentException("Invalid review page");
        var result = studentScoreCardRepository.findByPublicationStatusAndDepartmentNameAndBatchRequestKeyIsNullOrderByPushedAtAsc("PENDING_REVIEW", departmentName.trim(),
                org.springframework.data.domain.PageRequest.of(page, 50));
        List<ReviewItem> items = result.getContent().stream().map(card -> new ReviewItem(card.getId(), card.getStudentName(), card.getRollNumber(),
                        card.getDepartmentName(), card.getProgramName(), card.getSemester(), card.getAcademicYear(),
                        card.getSgpa(), card.getCgpa(), card.getResultStatus(), card.getUploadedBy(), card.getPushedAt(),
                        card.getPublicationVersion(), card.getCorrectionReason()))
                .toList();
        return new ReviewPage(items, page, result.hasNext());
    }

    @Transactional(readOnly = true)
    public List<String> myReviewDepartments() {
        return academicAuthority.myDepartments(AcademicAuthorityService.Capability.REVIEW);
    }

    @Transactional(readOnly = true)
    public List<String> myPublishDepartments() {
        return academicAuthority.myDepartments(AcademicAuthorityService.Capability.PUBLISH);
    }

    @Transactional(readOnly = true)
    public List<ScoreCardView> getMyScoreCards() {
        User student = access.currentUser();
        if (student.getRole() != Role.STUDENT) throw DomainAccess.denied();
        if (student.getVerifiedEnrollmentNo() == null || student.getEnrollmentIdentityVerifiedAt() == null) return List.of();
        return studentScoreCardRepository.findCurrentPublishedByRollNumber(student.getVerifiedEnrollmentNo())
                .stream().map(card -> new ScoreCardView(card.getId(), card.getStudentName(), card.getDepartmentName(),
                        card.getProgramName(), card.getSemester(), card.getAcademicYear(), card.getSgpa(), card.getCgpa(),
                        card.getResultStatus(), card.getPushedAt(), card.getPublicationVersion())).toList();
    }

    public record ScoreCardView(Long id, String studentName, String department, String program, String semester,
                                String academicYear, Double sgpa, Double cgpa, String resultStatus,
                                LocalDateTime publishedAt, int version) {}
    public record SubmissionReceipt(Long id, String status, int version) {}
    public record ReviewItem(Long id, String studentName, String verifiedIdentifier, String department, String program,
                             String semester, String academicYear, Double sgpa, Double cgpa, String resultStatus,
                             String submittedBy, LocalDateTime submittedAt, int version, String correctionReason) {}
    public record ReviewPage(List<ReviewItem> items, int page, boolean hasNext) {}
    public record ScheduleView(Long id, String title, String departmentName, String programName, String semester,
                               String academicYear, LocalDateTime publishedAt) {}

    private static ScheduleView scheduleView(ExamSchedule schedule) {
        return new ScheduleView(schedule.getId(), schedule.getTitle(), schedule.getDepartmentName(),
                schedule.getProgramName(), schedule.getSemester(), schedule.getAcademicYear(), schedule.getPublishedAt());
    }

    private void requireAcademicCapability(User actor, AcademicAuthorityService.Capability capability, String department) {
        if (!academicAuthority.hasCapability(actor, capability, department)) throw DomainAccess.denied();
    }

    private static boolean validGrade(Double value) { return value != null && Double.isFinite(value) && value >= 0 && value <= 10; }
    private String verifiedDepartment(User student) {
        if(student.getVerifiedDepartmentId()==null) throw new IllegalStateException("Institutional department membership needs office verification");
        return departments.findById(student.getVerifiedDepartmentId()).filter(Department::isActive).map(Department::getName)
                .orElseThrow(DomainAccess::denied);
    }
    private static String cleanRequired(String value, String field, int max) {
        validateText(value, max, field); return value.trim();
    }
    private static String clean(String value, int max) {
        if (value == null || value.isBlank()) return null;
        validateText(value, max, "optional field"); return value.trim();
    }
    private static void validateText(String value, int max, String field) {
        if (value == null || value.isBlank() || value.length() > max) throw new IllegalArgumentException("Invalid " + field);
    }
    private static void rejectUnmanagedFileUrl(String value) {
        if (value != null && !value.isBlank()) throw new IllegalArgumentException("Use an approved private academic upload workflow");
    }
}

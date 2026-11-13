package com.college.events.service;

import com.college.events.model.Role;
import com.college.events.model.AcademicBatchSubmission;
import com.college.events.model.StudentScoreCard;
import com.college.events.model.User;
import com.college.events.repository.AcademicBatchSubmissionRepository;
import com.college.events.repository.StudentScoreCardRepository;
import com.college.events.repository.UserRepository;
import com.college.events.security.DomainAccess;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Bounded CSV preflight and all-or-none submission; raw CSV content is never persisted. */
@Service
@RequiredArgsConstructor
public class AcademicBatchPreviewService {
    private static final int MAX_ROWS = 250;
    private static final List<String> HEADERS = List.of(
            "verifiedIdentifier", "semester", "academicYear", "program", "sgpa", "cgpa", "resultStatus", "correctionReason");
    private final UserRepository users;
    private final StudentScoreCardRepository scoreCards;
    private final AcademicBatchSubmissionRepository batches;
    private final AcademicAuthorityService authority;
    private final DomainAccess access;
    private final AuditService audit;
    private final NotificationService notifications;
    private final com.college.events.repository.DepartmentRepository departments;

    public record PreviewRow(int rowNumber, String verifiedIdentifier, boolean identityMatched, String issue) {}
    public record Preview(String department, int totalRows, int matchedRows, int rejectedRows, List<PreviewRow> rows,
                          boolean persisted) {}
    public record SubmissionReceipt(Long batchId, String status, int rowCount, long version) {}
    public record BatchQueueItem(Long batchId, String department, String submittedBy, int rowCount, String status,
                                 java.time.LocalDateTime submittedAt, long version) {}
    public record BatchDetail(BatchQueueItem batch, List<CoEAcademicService.ReviewItem> rows) {}
    public record BatchQueuePage(List<BatchQueueItem> items, int page, boolean hasNext) {}
    private record InputRow(int rowNumber, String identifier, String semester, String year, String program,
                            double sgpa, double cgpa, String resultStatus, String correctionReason, String issue) {}

    @Transactional(readOnly = true)
    public Preview preview(String department, String csvContent) {
        User actor = access.currentUser();
        if (department == null || department.isBlank() || department.length() > 120
                || csvContent == null || csvContent.isBlank()
                || csvContent.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 500_000)
            throw new IllegalArgumentException("Invalid academic batch preview request");
        String scope = department.trim();
        if (!authority.hasCapability(actor, AcademicAuthorityService.Capability.PUBLISH, scope)) throw DomainAccess.denied();
        List<InputRow> parsed = parse(csvContent);
        Map<String, List<Integer>> terms = new HashMap<>();
        List<InputRow> rows = new ArrayList<>(parsed.size());
        for (InputRow row : parsed) {
            if (row.identifier() != null && row.semester() != null && row.year() != null) {
                String termKey = row.identifier() + "\u0000" + row.semester() + "\u0000" + row.year();
                terms.computeIfAbsent(termKey, ignored -> new ArrayList<>()).add(row.rowNumber());
            }
            rows.add(new InputRow(row.rowNumber(), row.identifier(), row.semester(), row.year(), row.program(),
                    row.sgpa(), row.cgpa(), row.resultStatus(), row.correctionReason(), row.issue()));
        }
        Set<Integer> duplicateRows = new java.util.HashSet<>();
        terms.values().stream().filter(matchingRows -> matchingRows.size() > 1).forEach(duplicateRows::addAll);
        if (!duplicateRows.isEmpty()) rows = rows.stream().map(row -> duplicateRows.contains(row.rowNumber())
                ? new InputRow(row.rowNumber(), row.identifier(), row.semester(), row.year(), row.program(), row.sgpa(),
                        row.cgpa(), row.resultStatus(), row.correctionReason(), join(row.issue(), "DUPLICATE_STUDENT_TERM_IN_FILE"))
                : row).toList();
        List<String> validIds = rows.stream().filter(row -> row.issue() == null).map(InputRow::identifier).distinct().toList();
        Map<String, User> byIdentifier = new HashMap<>();
        if (!validIds.isEmpty()) users.findByVerifiedEnrollmentNoIn(validIds).forEach(user -> {
            if (user.getVerifiedEnrollmentNo() != null) byIdentifier.put(user.getVerifiedEnrollmentNo(), user);
        });
        List<PreviewRow> output = new ArrayList<>(rows.size());
        int matched = 0;
        for (InputRow row : rows) {
            String issue = row.issue();
            User student = row.identifier() == null ? null : byIdentifier.get(row.identifier());
            boolean identityMatched = student != null && student.isEnabled() && student.getRole() == Role.STUDENT
                    && student.getEnrollmentIdentityVerifiedAt() != null && scope.equals(verifiedDepartment(student));
            if (issue == null && !identityMatched) issue = student == null ? "NO_VERIFIED_STUDENT_MATCH" :
                    !student.isEnabled() || student.getRole() != Role.STUDENT ? "ACCOUNT_NOT_ELIGIBLE" :
                            student.getEnrollmentIdentityVerifiedAt() == null ? "IDENTITY_NOT_VERIFIED" : "DEPARTMENT_SCOPE_MISMATCH";
            if (issue == null) matched++;
            output.add(new PreviewRow(row.rowNumber(), row.identifier(), identityMatched, issue));
        }
        return new Preview(scope, output.size(), matched, output.size() - matched, List.copyOf(output), false);
    }

    @Transactional
    public SubmissionReceipt submit(String department, String csvContent, String suppliedRequestKey) {
        Preview checked = preview(department, csvContent);
        if (checked.rejectedRows() != 0) throw new IllegalArgumentException("Correct all rejected rows before submitting this batch");
        java.util.UUID batchUuid;
        try { batchUuid = java.util.UUID.fromString(suppliedRequestKey); }
        catch (RuntimeException invalid) { throw new IllegalArgumentException("Invalid batch request key"); }
        String canonicalKey = batchUuid.toString();
        String digest = digest(checked.department() + "\n" + csvContent);
        List<InputRow> parsed = parse(csvContent);
        List<String> identifiers = parsed.stream().map(InputRow::identifier).distinct().sorted().toList();
        Map<String, User> lockedStudents = new HashMap<>();
        for (String identifier : identifiers) {
            User locked = users.findByVerifiedEnrollmentNoForAcademicUpdate(identifier)
                    .filter(User::isEnabled).filter(user -> user.getRole() == Role.STUDENT)
                    .filter(user -> user.getEnrollmentIdentityVerifiedAt() != null)
                    .filter(user -> checked.department().equals(verifiedDepartment(user)))
                    .orElseThrow(() -> new IllegalStateException("A student identity changed during batch submission; preview again"));
            lockedStudents.put(identifier, locked);
        }
        User actor = access.currentUser();
        var replay = batches.findByRequestKey(canonicalKey);
        if (replay.isPresent()) return replayReceipt(replay.get(), actor, checked.department(), digest);

        Map<String, StudentScoreCard> latestByTerm = new HashMap<>();
        for (InputRow row : parsed) {
            String termKey = row.identifier() + "\u0000" + row.semester() + "\u0000" + row.year();
            StudentScoreCard latest = latestByTerm.computeIfAbsent(termKey, ignored -> scoreCards
                    .findTopByRollNumberAndSemesterAndAcademicYearOrderByPublicationVersionDesc(
                            row.identifier(), row.semester(), row.year()).orElse(null));
            if (latest != null && "PENDING_REVIEW".equals(latest.getPublicationStatus()))
                throw new IllegalStateException("A result for this student and term is already awaiting review");
            if (latest != null && (row.correctionReason() == null || row.correctionReason().isBlank()))
                throw new IllegalArgumentException("Every revised result needs a correction reason");
            if (latest == null && row.correctionReason() != null && !row.correctionReason().isBlank())
                throw new IllegalArgumentException("Correction reason is only valid for an existing result");
        }
        List<StudentScoreCard> submissions = new ArrayList<>(parsed.size());
        for (InputRow row : parsed) {
            String termKey = row.identifier() + "\u0000" + row.semester() + "\u0000" + row.year();
            StudentScoreCard latest = latestByTerm.get(termKey);
            User student = lockedStudents.get(row.identifier());
            StudentScoreCard card = StudentScoreCard.builder().rollNumber(student.getVerifiedEnrollmentNo())
                    .studentName(student.getName()).departmentName(verifiedDepartment(student)).programName(row.program())
                    .semester(row.semester()).academicYear(row.year()).sgpa(row.sgpa()).cgpa(row.cgpa())
                    .resultStatus(row.resultStatus()).publicationStatus("PENDING_REVIEW")
                    .publicationVersion(latest == null ? 1 : latest.getPublicationVersion() + 1)
                    .correctionReason(row.correctionReason()).batchRequestKey(canonicalKey).batchRowNumber(row.rowNumber())
                    .uploadedBy(actor.getEmail()).pushedToDashboard(false).pushedToEmail(false).scoreCardPdfUrl(null).build();
            submissions.add(card);
        }
        scoreCards.saveAll(submissions);
        AcademicBatchSubmission batch = batches.save(AcademicBatchSubmission.builder().requestKey(canonicalKey)
                .contentDigest(digest).departmentName(checked.department()).submittedBy(actor.getEmail())
                .rowCount(submissions.size()).status("PENDING_REVIEW").build());
        audit.logAction(actor.getEmail(), actor.getRole().name(), "ACADEMIC_SCORECARD_BATCH_SUBMITTED",
                "batchId=" + batch.getId() + ";rowCount=" + submissions.size() + ";department=" + checked.department());
        return new SubmissionReceipt(batch.getId(), batch.getStatus(), batch.getRowCount(), batch.getVersion());
    }

    @Transactional(readOnly = true)
    public BatchQueuePage pendingReviewBatches(int page) {
        if (page < 0 || page > 100_000) throw new IllegalArgumentException("Invalid academic review page");
        List<String> departments = authority.myDepartments(AcademicAuthorityService.Capability.REVIEW);
        if (departments.isEmpty()) return new BatchQueuePage(List.of(), page, false);
        User reviewer = access.currentUser();
        var result = batches.findByStatusAndDepartmentNameInAndSubmittedByNotOrderByCreatedAtAsc("PENDING_REVIEW", departments,
                reviewer.getEmail(), org.springframework.data.domain.PageRequest.of(page, 25));
        return new BatchQueuePage(result.getContent().stream().map(AcademicBatchPreviewService::queueItem).toList(), page, result.hasNext());
    }

    @Transactional(readOnly = true)
    public BatchDetail batchForReview(Long batchId) {
        User reviewer = access.currentUser();
        AcademicBatchSubmission batch = batches.findById(batchId).orElseThrow(DomainAccess::denied);
        requireReview(reviewer, batch.getDepartmentName());
        if (reviewer.getEmail().equalsIgnoreCase(batch.getSubmittedBy())) throw DomainAccess.denied();
        List<StudentScoreCard> cards = scoreCards.findByBatchForDisplay(batch.getRequestKey());
        if (cards.size() != batch.getRowCount()) throw new IllegalStateException("Academic batch integrity check failed");
        List<CoEAcademicService.ReviewItem> rows = cards.stream().map(card -> new CoEAcademicService.ReviewItem(
                card.getId(), card.getStudentName(), card.getRollNumber(), card.getDepartmentName(), card.getProgramName(),
                card.getSemester(), card.getAcademicYear(), card.getSgpa(), card.getCgpa(), card.getResultStatus(),
                card.getUploadedBy(), card.getPushedAt(), card.getPublicationVersion(), card.getCorrectionReason())).toList();
        return new BatchDetail(queueItem(batch), rows);
    }

    @Transactional
    public SubmissionReceipt reviewBatch(Long batchId, boolean publish, long expectedVersion, String reason) {
        User reviewer = access.currentUser();
        AcademicBatchSubmission batch = batches.findForReview(batchId).orElseThrow(DomainAccess::denied);
        requireReview(reviewer, batch.getDepartmentName());
        if (reviewer.getEmail().equalsIgnoreCase(batch.getSubmittedBy())) throw DomainAccess.denied();
        if (batch.getVersion() != expectedVersion) throw new IllegalStateException("Academic batch changed; reload before reviewing");
        if (!"PENDING_REVIEW".equals(batch.getStatus())) throw new IllegalStateException("Academic batch is no longer awaiting review");
        if (!publish && (reason == null || reason.isBlank() || reason.length() > 500))
            throw new IllegalArgumentException("A bounded reason is required when returning the batch");
        List<StudentScoreCard> cards = scoreCards.findBatchForReview(batch.getRequestKey());
        if (cards.size() != batch.getRowCount() || cards.stream().anyMatch(card ->
                !"PENDING_REVIEW".equals(card.getPublicationStatus())
                        || !batch.getDepartmentName().equals(card.getDepartmentName())
                        || !batch.getSubmittedBy().equals(card.getUploadedBy())))
            throw new IllegalStateException("Batch rows changed; no records were modified");
        for (StudentScoreCard card : cards) {
            card.setPublicationStatus(publish ? "PUBLISHED" : "REJECTED");
            card.setPushedToDashboard(publish);
            card.setReviewedBy(reviewer.getEmail());
            card.setReviewedAt(java.time.LocalDateTime.now());
            card.setReviewReason(publish ? null : reason.trim());
        }
        scoreCards.saveAll(cards);
        batch.setStatus(publish ? "PUBLISHED" : "REJECTED");
        AcademicBatchSubmission saved = batches.save(batch);
        audit.logAction(reviewer.getEmail(), reviewer.getRole().name(),
                publish ? "ACADEMIC_SCORECARD_BATCH_PUBLISHED" : "ACADEMIC_SCORECARD_BATCH_RETURNED",
                "batchId=" + saved.getId() + ";rowCount=" + cards.size());
        List<String> identifiers = cards.stream().map(StudentScoreCard::getRollNumber).distinct().toList();
        Map<String, User> studentByIdentifier = new HashMap<>();
        users.findByVerifiedEnrollmentNoIn(identifiers).forEach(user -> {
            if (user.isEnabled() && user.getVerifiedEnrollmentNo() != null) studentByIdentifier.put(user.getVerifiedEnrollmentNo(), user);
        });
        String message = publish ? "An official scorecard has been published to your account."
                : "An academic result submission was returned for correction. Contact the academic office for details.";
        cards.stream().map(StudentScoreCard::getRollNumber).distinct().map(studentByIdentifier::get)
                .filter(java.util.Objects::nonNull).forEach(student -> notifications.createNotification(student, message, "/app/"));
        return new SubmissionReceipt(saved.getId(), saved.getStatus(), saved.getRowCount(), saved.getVersion());
    }

    private void requireReview(User reviewer, String department) {
        if (!authority.hasCapability(reviewer, AcademicAuthorityService.Capability.REVIEW, department)) throw DomainAccess.denied();
    }

    private static BatchQueueItem queueItem(AcademicBatchSubmission batch) {
        return new BatchQueueItem(batch.getId(), batch.getDepartmentName(), batch.getSubmittedBy(), batch.getRowCount(),
                batch.getStatus(), batch.getCreatedAt(), batch.getVersion());
    }

    private static SubmissionReceipt replayReceipt(AcademicBatchSubmission batch, User actor, String department, String digest) {
        if (!actor.getEmail().equals(batch.getSubmittedBy()) || !department.equals(batch.getDepartmentName())
                || !digest.equals(batch.getContentDigest()))
            throw new IllegalStateException("Batch request key has already been used for different content");
        return new SubmissionReceipt(batch.getId(), batch.getStatus(), batch.getRowCount(), batch.getVersion());
    }

    private static String digest(String content) {
        try {
            byte[] value = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(value);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Required digest support is unavailable");
        }
    }

    private static List<InputRow> parse(String content) {
        try (CSVParser parser = CSVParser.parse(content, CSVFormat.DEFAULT.builder().setHeader()
                .setSkipHeaderRecord(true).setIgnoreEmptyLines(true).setTrim(true).build())) {
            if (!HEADERS.equals(parser.getHeaderNames())) throw new IllegalArgumentException("CSV headers must exactly match the published import template");
            List<InputRow> rows = new ArrayList<>();
            int line = 1;
            for (CSVRecord record : parser) {
                line++;
                if (rows.size() >= MAX_ROWS) throw new IllegalArgumentException("Academic batch preview is limited to 250 rows");
                if (!record.isConsistent()) throw new IllegalArgumentException("CSV row " + line + " has an invalid number of columns");
                String identifier = bounded(record.get("verifiedIdentifier"), 64);
                String semester = bounded(record.get("semester"), 20);
                String year = bounded(record.get("academicYear"), 20);
                String program = boundedOptional(record.get("program"), 120);
                String result = record.get("resultStatus").toUpperCase(Locale.ROOT);
                String reason = boundedOptional(record.get("correctionReason"), 500);
                Double sgpa = number(record.get("sgpa")); Double cgpa = number(record.get("cgpa"));
                String issue = identifier == null ? "INVALID_IDENTIFIER" : semester == null || year == null ? "INVALID_TERM"
                        : program == INVALID_TEXT || reason == INVALID_TEXT ? "FIELD_TOO_LONG"
                        : sgpa == null || cgpa == null || sgpa < 0 || sgpa > 10 || cgpa < 0 || cgpa > 10 ? "INVALID_GPA"
                        : result.length() > 20 || !Set.of("PASS", "PROMOTED", "REAPPEAR").contains(result) ? "INVALID_RESULT_STATUS" : null;
                rows.add(new InputRow(line, identifier, semester, year, program == INVALID_TEXT ? null : program,
                        sgpa == null ? -1 : sgpa, cgpa == null ? -1 : cgpa, result,
                        reason == INVALID_TEXT ? null : reason, issue));
            }
            if (rows.isEmpty()) throw new IllegalArgumentException("CSV contains no data rows");
            return rows;
        } catch (IOException e) {
            throw new IllegalArgumentException("CSV could not be parsed safely");
        }
    }

    private static final String INVALID_TEXT = "\u0000";
    private static String bounded(String value, int max) { return value == null || value.isBlank() || value.length() > max ? null : value.trim(); }
    private static String boundedOptional(String value, int max) { return value == null || value.isBlank() ? null : value.length() > max ? INVALID_TEXT : value.trim(); }
    private static Double number(String value) {
        try { double parsed = Double.parseDouble(value); return Double.isFinite(parsed) ? parsed : null; }
        catch (NumberFormatException e) { return null; }
    }
    private static String join(String left, String right) { return left == null ? right : left + "," + right; }
    private String verifiedDepartment(User student) {
        if(student.getVerifiedDepartmentId()==null) return null;
        return departments.findById(student.getVerifiedDepartmentId()).filter(com.college.events.model.Department::isActive)
                .map(com.college.events.model.Department::getName).orElse(null);
    }
}

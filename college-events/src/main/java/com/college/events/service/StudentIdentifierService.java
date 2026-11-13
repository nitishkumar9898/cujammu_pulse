package com.college.events.service;

import com.college.events.dto.StudentIdentifierDtos.*;
import com.college.events.model.*;
import com.college.events.repository.UserRepository;
import com.college.events.security.DomainAccess;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Staff-entered verification after checking the institutional student source; never copies self-reported profile data. */
@Service
@RequiredArgsConstructor
public class StudentIdentifierService {
    private final UserRepository users;
    private final DomainAccess access;
    private final AuditService audit;
    private final com.college.events.repository.DepartmentRepository departments;
    private final NotificationService notifications;

    @Transactional(readOnly = true)
    public Candidate lookup(Lookup command) {
        access.requireRole(Role.ADMIN);
        User student = users.findByEmail(command.email().trim().toLowerCase(java.util.Locale.ROOT))
                .filter(user -> user.isEnabled() && user.getRole() == Role.STUDENT)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        return new Candidate(student.getId(), student.getName(), student.getEmail(), student.getVerifiedEnrollmentNo(),
                student.getVerifiedDepartmentId(), complete(student) ? "VERIFIED" : hasBindingEvidence(student) ? "REVIEW_REQUIRED" : "UNVERIFIED", student.getCredentialVersion());
    }

    @Transactional
    public Result verify(long studentId, Verify command) {
        User administrator = access.requireRole(Role.ADMIN);
        User student = users.findForUpdate(studentId).orElseThrow(DomainAccess::denied);
        if (!student.isEnabled() || student.getRole() != Role.STUDENT) throw DomainAccess.denied();
        String identifier = required(command.identifier(), 64);
        String source = required(command.officialSourceReference(), 120);
        String reason = required(command.reviewReason(), 240);
        if (complete(student) && identifier.equals(student.getVerifiedEnrollmentNo())
                && Objects.equals(command.departmentId(), student.getVerifiedDepartmentId())) return receipt(student);
        // Existing bindings determine historical academic access and attendance evidence.
        // Corrections need a separate reviewed reconciliation workflow, never an overwrite.
        if (hasBindingEvidence(student)) throw new IllegalStateException("Existing identity requires reviewed correction");
        if (student.getCredentialVersion() != command.expectedCredentialVersion())
            throw new IllegalStateException("Student account changed; refresh and retry");
        if(command.departmentId()==null) throw new IllegalArgumentException("An office-verified department registry binding is required");
        var department=departments.findById(command.departmentId()).filter(Department::isActive).orElseThrow(DomainAccess::denied);
        if (users.existsByVerifiedEnrollmentNo(identifier)
                && !identifier.equals(student.getVerifiedEnrollmentNo()))
            throw new IllegalArgumentException("This official identifier is already assigned");
        student.setVerifiedEnrollmentNo(identifier);
        student.setVerifiedDepartmentId(department.getId());
        student.setEnrollmentIdentityVerifiedAt(LocalDateTime.now());
        student.setEnrollmentIdentityVerifiedBy(administrator.getEmail());
        student.setEnrollmentIdentitySourceReference(safeRef(source));
        student.setEnrollmentIdentityReviewReason(safeRef(reason));
        users.saveAndFlush(student);
        audit.logAction(administrator.getEmail(), administrator.getRole().name(), "STUDENT_IDENTIFIER_VERIFIED",
                "studentId=" + studentId + ";source=ADMIN_VERIFIED");
        notifications.createNotification(student, "Your university student identifier and department have been verified. Contact the university office if this is unexpected.", "/app/");
        return receipt(student);
    }

    private static Result receipt(User student) { return new Result(student.getId(), student.getVerifiedEnrollmentNo(), student.getEnrollmentIdentityVerifiedAt(), student.getCredentialVersion()); }
    private static boolean complete(User student) { return student.getVerifiedEnrollmentNo() != null && !student.getVerifiedEnrollmentNo().isBlank()
            && student.getVerifiedDepartmentId() != null && student.getEnrollmentIdentityVerifiedAt() != null
            && present(student.getEnrollmentIdentityVerifiedBy()) && present(student.getEnrollmentIdentitySourceReference())
            && present(student.getEnrollmentIdentityReviewReason()); }
    private static boolean present(String value) { return value != null && !value.isBlank(); }
    private static boolean hasBindingEvidence(User student) { return student.getVerifiedEnrollmentNo() != null || student.getVerifiedDepartmentId() != null
            || student.getEnrollmentIdentityVerifiedAt() != null || student.getEnrollmentIdentityVerifiedBy() != null
            || student.getEnrollmentIdentitySourceReference() != null || student.getEnrollmentIdentityReviewReason() != null; }
    private static String required(String value, int max) {
        if(value == null || value.trim().isEmpty() || value.trim().length() > max || value.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid verification field");
        return value.trim();
    }

    private static String safeRef(String value) { return value.trim().replaceAll("[\\r\\n;]", " "); }
}

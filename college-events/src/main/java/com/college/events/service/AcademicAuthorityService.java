package com.college.events.service;

import com.college.events.model.*;
import com.college.events.repository.AcademicAuthorityGrantRepository;
import com.college.events.repository.DepartmentRepository;
import com.college.events.repository.UserRepository;
import com.college.events.security.DomainAccess;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AcademicAuthorityService {
    public enum Capability { PUBLISH, REVIEW }
    public record GrantView(Long id, Long userId, String name, String email, String department,
                            String capability, LocalDateTime grantedAt, String grantedBy) {}

    private final AcademicAuthorityGrantRepository grants;
    private final UserRepository users;
    private final DepartmentRepository departments;
    private final DomainAccess access;
    private final AuditService audit;

    @Transactional
    public GrantView grant(Long userId, String departmentName, Capability capability) {
        User administrator = access.requireRole(Role.ADMIN);
        String department = validateDepartment(departmentName);
        User recipient = users.findById(userId).filter(User::isEnabled)
                .filter(user -> user.getRole() == Role.STAFF || user.getRole() == Role.FACULTY || user.getRole() == Role.ORGANIZER)
                .orElseThrow(DomainAccess::denied);
        AcademicAuthorityGrant grant = grants.findByUserIdAndCapabilityAndDepartmentName(userId, capability.name(), department)
                .orElseGet(() -> AcademicAuthorityGrant.builder().user(recipient).capability(capability.name())
                        .departmentName(department).grantedBy(administrator.getEmail()).build());
        grant.setEnabled(true); grant.setGrantedBy(administrator.getEmail()); grant.setRevokedAt(null);
        AcademicAuthorityGrant saved = grants.save(grant);
        audit.logAction(administrator.getEmail(), administrator.getRole().name(), "ACADEMIC_AUTHORITY_GRANTED",
                "userId=" + userId + ";capability=" + capability + ";department=" + department);
        return view(saved);
    }

    @Transactional
    public void revoke(Long grantId) {
        User administrator = access.requireRole(Role.ADMIN);
        AcademicAuthorityGrant grant = grants.findById(grantId).orElseThrow(DomainAccess::denied);
        if (grant.isEnabled()) {
            grant.setEnabled(false); grant.setRevokedAt(LocalDateTime.now()); grants.save(grant);
            audit.logAction(administrator.getEmail(), administrator.getRole().name(), "ACADEMIC_AUTHORITY_REVOKED",
                    "grantId=" + grantId + ";capability=" + grant.getCapability() + ";department=" + grant.getDepartmentName());
        }
    }

    @Transactional(readOnly = true)
    public List<GrantView> activeGrants() {
        access.requireRole(Role.ADMIN);
        return grants.findByEnabledTrueOrderByDepartmentNameAscCapabilityAsc().stream().map(AcademicAuthorityService::view).toList();
    }

    @Transactional(readOnly = true)
    public List<String> activeDepartments() {
        access.requireRole(Role.ADMIN);
        return departments.findByActiveTrueOrderByNameAsc().stream().map(Department::getName).toList();
    }

    @Transactional(readOnly = true)
    public List<String> myDepartments(Capability capability) {
        User actor = access.currentUser();
        if (!academicOperator(actor)) return List.of();
        return grants.findByUserIdAndCapabilityAndEnabledTrueOrderByDepartmentNameAsc(actor.getId(), capability.name())
                .stream().map(AcademicAuthorityGrant::getDepartmentName).distinct().toList();
    }

    @Transactional(readOnly = true)
    public boolean hasCapability(User actor, Capability capability, String departmentName) {
        if (!academicOperator(actor)) return false; // Generic administrators and student accounts cannot publish or review academic records.
        String department = validateDepartment(departmentName);
        return grants.findActiveCapabilityForUse(actor.getId(), capability.name(), department).isPresent();
    }

    private String validateDepartment(String value) {
        if (value == null || value.isBlank() || value.length() > 120) throw new IllegalArgumentException("Invalid academic department");
        return departments.findByName(value.trim()).filter(Department::isActive).map(Department::getName)
                .orElseThrow(() -> new IllegalArgumentException("Department is not in the active institutional directory"));
    }

    private static boolean academicOperator(User user) {
        return user.getRole() == Role.STAFF || user.getRole() == Role.FACULTY || user.getRole() == Role.ORGANIZER;
    }

    private static GrantView view(AcademicAuthorityGrant grant) {
        User user = grant.getUser();
        return new GrantView(grant.getId(), user.getId(), user.getName(), user.getEmail(), grant.getDepartmentName(),
                grant.getCapability(), grant.getCreatedAt(), grant.getGrantedBy());
    }
}

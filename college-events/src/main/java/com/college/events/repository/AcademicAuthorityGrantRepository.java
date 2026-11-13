package com.college.events.repository;

import com.college.events.model.AcademicAuthorityGrant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademicAuthorityGrantRepository extends JpaRepository<AcademicAuthorityGrant, Long> {
    Optional<AcademicAuthorityGrant> findByUserIdAndCapabilityAndDepartmentName(Long userId, String capability, String departmentName);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_READ)
    @org.springframework.data.jpa.repository.Query("select g from AcademicAuthorityGrant g where g.user.id = :userId and g.capability = :capability and g.departmentName = :department and g.enabled = true")
    Optional<AcademicAuthorityGrant> findActiveCapabilityForUse(@org.springframework.data.repository.query.Param("userId") Long userId,
            @org.springframework.data.repository.query.Param("capability") String capability,
            @org.springframework.data.repository.query.Param("department") String department);
    boolean existsByUserIdAndCapabilityAndDepartmentNameAndEnabledTrue(Long userId, String capability, String departmentName);
    List<AcademicAuthorityGrant> findByEnabledTrueOrderByDepartmentNameAscCapabilityAsc();
    List<AcademicAuthorityGrant> findByUserIdAndCapabilityAndEnabledTrueOrderByDepartmentNameAsc(Long userId, String capability);
}

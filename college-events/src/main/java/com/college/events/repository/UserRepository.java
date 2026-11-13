package com.college.events.repository;

import com.college.events.model.Role;
import com.college.events.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Collection;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    org.springframework.data.domain.Slice<User> findAllByRoleAndEnabledTrue(Role role, org.springframework.data.domain.Pageable pageable);
    Optional<User> findByEmail(String email);
    /** Scalar query avoids returning a stale first-level cached account during after-commit SSE dispatch. */
    @Query("select u.enabled as enabled, u.role as role, u.email as email, u.password as password from User u where u.id = :id")
    Optional<StreamAccountState> findCurrentStreamAccount(@Param("id") Long id);
    interface StreamAccountState {
        boolean getEnabled();
        Role getRole();
        String getEmail();
        String getPassword();
    }
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForNotificationClaim(@Param("id") Long id);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findForUpdate(@Param("id") Long id);
    Optional<User> findByVerifiedEnrollmentNo(String verifiedEnrollmentNo);
    List<User> findByVerifiedEnrollmentNoIn(Collection<String> verifiedEnrollmentNos);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.verifiedEnrollmentNo = :identifier")
    Optional<User> findByVerifiedEnrollmentNoForAcademicUpdate(@org.springframework.data.repository.query.Param("identifier") String identifier);
    boolean existsByVerifiedEnrollmentNo(String verifiedEnrollmentNo);
    boolean existsByEmail(String email);
    List<User> findByRoleNot(Role role);
    List<User> findByRole(Role role);
    List<User> findTop50ByRoleAndEnabledTrueOrderByNameAsc(Role role);
    List<User> findByDepartment(String department);
    List<User> findByRoleAndDepartment(Role role, String department);
    List<User> findByRoleAndVerifiedDepartmentId(Role role, Long verifiedDepartmentId);
    @Query("select u.id from User u where u.role = :role and u.id > :afterId order by u.id")
    List<Long> findAnnouncementUserIdsAfter(@Param("role") Role role, @Param("afterId") Long afterId,
                                            org.springframework.data.domain.Pageable page);
    @Query("select u.id from User u where u.role = :role and u.verifiedDepartmentId = :departmentId and u.id > :afterId order by u.id")
    List<Long> findAnnouncementUserIdsByDepartmentAfter(@Param("role") Role role,
            @Param("departmentId") Long departmentId, @Param("afterId") Long afterId,
            org.springframework.data.domain.Pageable page);
    long countByRole(Role role);
    long countByRoleAndDepartment(Role role, String department);
    long countByRoleAndVerifiedDepartmentId(Role role, Long verifiedDepartmentId);
    Optional<User> findByPasswordResetToken(String token);
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.passwordResetToken = :digest, u.passwordResetTokenExpiry = :expiry, u.credentialVersion = u.credentialVersion + 1 where u.id = :id and u.enabled = true")
    int issuePasswordReset(@Param("id") Long id, @Param("digest") String digest,
                           @Param("expiry") java.time.LocalDateTime expiry);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.password = :password, u.passwordResetToken = null, u.passwordResetTokenExpiry = null, u.credentialVersion = u.credentialVersion + 1 "
            + "where u.passwordResetToken = :digest and u.passwordResetTokenExpiry > :now and u.enabled = true")
    int consumePasswordReset(@Param("digest") String digest, @Param("now") java.time.LocalDateTime now,
                             @Param("password") String password);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.password = :password, u.passwordResetToken = null, u.passwordResetTokenExpiry = null, u.credentialVersion = u.credentialVersion + 1 where u.id = :id and u.password = :expectedPassword and u.enabled = true")
    int replacePasswordAndRevokeReset(@Param("id") Long id, @Param("expectedPassword") String expectedPassword,
                                     @Param("password") String password);
    List<User> findByCourseCompletionDateBeforeAndIsAlumniFalse(java.time.LocalDate date);
}

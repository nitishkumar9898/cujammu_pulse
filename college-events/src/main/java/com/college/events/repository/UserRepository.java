package com.college.events.repository;

import com.college.events.model.Role;
import com.college.events.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findByRoleNot(Role role);
    List<User> findByRole(Role role);
    List<User> findByDepartment(String department);
    List<User> findByRoleAndDepartment(Role role, String department);
    long countByRole(Role role);
    Optional<User> findByPasswordResetToken(String token);
}

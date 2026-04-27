package com.college.events.repository;

import com.college.events.model.Attendance;
import com.college.events.model.Registration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    boolean existsByRegistration(Registration registration);
    Optional<Attendance> findByRegistration(Registration registration);
}

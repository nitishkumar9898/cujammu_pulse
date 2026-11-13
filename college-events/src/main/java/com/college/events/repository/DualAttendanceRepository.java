package com.college.events.repository;

import com.college.events.model.DualAttendance;
import com.college.events.model.Registration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DualAttendanceRepository extends JpaRepository<DualAttendance, Long> {
    Optional<DualAttendance> findByRegistration(Registration registration);

    @Query("SELECT da FROM DualAttendance da WHERE da.registration.event.id = :eventId")
    List<DualAttendance> findByEventId(@Param("eventId") Long eventId);
}

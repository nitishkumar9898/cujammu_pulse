package com.college.events.repository;

import com.college.events.model.Facility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FacilityRepository extends JpaRepository<Facility, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select f from Facility f where f.id = :id")
    Optional<Facility> findForBooking(@org.springframework.data.repository.query.Param("id") Long id);
    Optional<Facility> findByName(String name);
    List<Facility> findByFacilityTypeAndActiveTrueOrderByNameAsc(String facilityType);
    List<Facility> findByActiveTrueOrderByNameAsc();
}

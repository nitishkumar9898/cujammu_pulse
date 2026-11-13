package com.college.events.repository;

import com.college.events.model.HostelDevice;
import com.college.events.model.Hostel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Repository for hostel gate-scanning device management. */
@Repository
public interface HostelDeviceRepository extends JpaRepository<HostelDevice, Long> {

    Optional<HostelDevice> findByDeviceId(String deviceId);

    Optional<HostelDevice> findByHostelAndDeviceId(Hostel hostel, String deviceId);

    @Modifying
    @Transactional
    @Query("UPDATE HostelDevice d SET d.active = false WHERE d.deviceId = :deviceId AND d.hostel.id = :hostelId")
    int disableByDeviceId(@Param("deviceId") String deviceId, @Param("hostelId") Long hostelId);

    @Modifying
    @Transactional
    @Query("UPDATE HostelDevice d SET d.active = false WHERE d.id = :deviceId")
    int disableById(@Param("deviceId") Long deviceId);
}

package com.college.events.repository;

import com.college.events.model.Certificate;
import com.college.events.model.Event;
import com.college.events.model.Registration;
import com.college.events.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CertificateRepository extends JpaRepository<Certificate, Long> {

    List<Certificate> findByUserOrderByIssuedAtDesc(User user);

    List<Certificate> findByEventOrderByIssuedAtDesc(Event event);

    Optional<Certificate> findByCertificateNumber(String certificateNumber);

    Optional<Certificate> findByRegistration(Registration registration);

    boolean existsByRegistration(Registration registration);

    long countByEvent(Event event);
}

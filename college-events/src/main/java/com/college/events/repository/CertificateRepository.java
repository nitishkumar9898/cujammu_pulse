package com.college.events.repository;

import com.college.events.model.Certificate;
import com.college.events.model.Event;
import com.college.events.model.Registration;
import com.college.events.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CertificateRepository extends JpaRepository<Certificate, Long> {
    @Query("select c from Certificate c join fetch c.finalization where c.user.id = :userId and c.certificateType = 'POINT_PARTICIPATION' order by c.id desc")
    org.springframework.data.domain.Slice<Certificate> findProvenOwned(@Param("userId") long userId, org.springframework.data.domain.Pageable pageable);

    List<Certificate> findByUserOrderByIssuedAtDesc(User user);

    List<Certificate> findByEventOrderByIssuedAtDesc(Event event);

    Optional<Certificate> findByCertificateNumber(String certificateNumber);

    Optional<Certificate> findByRegistration(Registration registration);

    boolean existsByRegistration(Registration registration);

    long countByEvent(Event event);
}

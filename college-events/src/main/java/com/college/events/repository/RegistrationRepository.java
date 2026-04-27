package com.college.events.repository;

import com.college.events.model.Event;
import com.college.events.model.Registration;
import com.college.events.model.RegistrationStatus;
import com.college.events.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RegistrationRepository extends JpaRepository<Registration, Long> {

    boolean existsByUserAndEvent(User user, Event event);

    @Query("SELECT r FROM Registration r JOIN FETCH r.event JOIN FETCH r.user WHERE r.user = :user AND r.event = :event")
    Optional<Registration> findByUserAndEvent(@Param("user") User user, @Param("event") Event event);

    @Query("SELECT r FROM Registration r JOIN FETCH r.event JOIN FETCH r.user WHERE r.id = :id")
    Optional<Registration> findWithEventById(@Param("id") Long id);

    @Query("SELECT r FROM Registration r JOIN FETCH r.event JOIN FETCH r.user WHERE r.qrToken = :qrToken")
    Optional<Registration> findByQrToken(@Param("qrToken") String qrToken);

    @Query("SELECT r FROM Registration r JOIN FETCH r.event JOIN FETCH r.user WHERE r.event = :event")
    List<Registration> findByEvent(@Param("event") Event event);

    @Query("SELECT r FROM Registration r JOIN FETCH r.event JOIN FETCH r.user WHERE r.event = :event ORDER BY r.registeredAt ASC")
    List<Registration> findByEventOrderByRegisteredAtAsc(@Param("event") Event event);

    @Query("SELECT r FROM Registration r JOIN FETCH r.event JOIN FETCH r.user WHERE r.user = :user")
    List<Registration> findByUser(@Param("user") User user);

    @Query("SELECT r FROM Registration r JOIN FETCH r.event JOIN FETCH r.user WHERE r.user = :user ORDER BY r.registeredAt DESC")
    List<Registration> findByUserOrderByRegisteredAtDesc(@Param("user") User user);

    @Query("SELECT COUNT(r) FROM Registration r WHERE r.event = :event AND r.status IN ('REGISTERED', 'ATTENDED')")
    long countConfirmedByEvent(@Param("event") Event event);

    @Query("SELECT COUNT(r) FROM Registration r WHERE r.event = :event AND r.status = 'WAITLISTED'")
    long countWaitlistedByEvent(@Param("event") Event event);

    @Query("SELECT r FROM Registration r JOIN FETCH r.event JOIN FETCH r.user WHERE r.event = :event AND r.status = :status ORDER BY r.registeredAt ASC")
    Optional<Registration> findFirstByEventAndStatusOrderByRegisteredAtAsc(@Param("event") Event event, @Param("status") RegistrationStatus status);

    @Query("SELECT r FROM Registration r JOIN FETCH r.event JOIN FETCH r.user WHERE r.event = :event AND r.status = :status")
    List<Registration> findByEventAndStatus(@Param("event") Event event, @Param("status") RegistrationStatus status);

    @Query("SELECT COUNT(r) FROM Registration r WHERE r.event = :event AND r.status = :status")
    long countByEventAndStatus(@Param("event") Event event, @Param("status") RegistrationStatus status);

    @Query("SELECT r FROM Registration r JOIN FETCH r.event JOIN FETCH r.user WHERE r.user = :user AND r.status NOT IN ('CANCELLED')")
    List<Registration> findActiveByUser(@Param("user") User user);

    long countByStatus(RegistrationStatus status);
}

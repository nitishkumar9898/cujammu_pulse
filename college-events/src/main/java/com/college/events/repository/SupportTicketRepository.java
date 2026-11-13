package com.college.events.repository;

import com.college.events.model.SupportTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import com.college.events.model.User;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {
    Optional<SupportTicket> findByTicketNumber(String ticketNumber);
    List<SupportTicket> findByCreatedById(Long userId);
    List<SupportTicket> findByStatus(String status);
    List<SupportTicket> findByCategory(String category);
    List<SupportTicket> findAllByOrderByCreatedAtDesc();
    Optional<SupportTicket> findByPublicId(String publicId);
    Optional<SupportTicket> findByCreatedByIdAndRequestKey(Long userId, String requestKey);
    Page<SupportTicket> findByCreatedByIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);
    Page<SupportTicket> findByAssignedStaffIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);
    Page<SupportTicket> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from SupportTicket t where t.publicId = :id")
    Optional<SupportTicket> lockTicket(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from SupportTicket t where t.ticketNumber = :number")
    Optional<SupportTicket> lockLegacyTicket(@Param("number") String number);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> lockOwner(@Param("id") Long id);

    @Query("select t.ticketNumber from SupportTicket t where t.ticketNumber like :prefix")
    List<String> findNumberHistory(@Param("prefix") String prefix);
}

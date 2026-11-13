package com.college.events.repository;

import com.college.events.model.LibraryGateLog;
import com.college.events.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface LibraryGateLogRepository extends JpaRepository<LibraryGateLog, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LibraryGateLog l where l.id=:id and l.user.id=:ownerId")
    Optional<LibraryGateLog> findOwnedForSafeExit(@Param("id") long id,@Param("ownerId") long ownerId);
    List<LibraryGateLog> findByUserAndStatusOrderByIdDesc(User user,String status,org.springframework.data.domain.Pageable pageable);
    List<LibraryGateLog> findByUserOrderByEntryTimeDesc(com.college.events.model.User user, org.springframework.data.domain.Pageable pageable);
    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("update LibraryGateLog l set l.mismatchAttemptCount = l.mismatchAttemptCount + 1 where l.id = :id and l.status = 'ACTIVE'")
    int recordActivePurposeMismatch(@Param("id") Long id);

    Optional<LibraryGateLog> findTopByUserAndStatusOrderByIdDesc(User user, String status);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LibraryGateLog l where l.user = :user and l.status = :status order by l.id desc")
    List<LibraryGateLog> findActiveForUpdate(@Param("user") User user, @Param("status") String status,
                                             org.springframework.data.domain.Pageable pageable);
    List<LibraryGateLog> findByLogDateOrderByEntryTimeDesc(LocalDate date);
    List<LibraryGateLog> findByLogDateAndSessionTypeOrderByEntryTimeDesc(LocalDate date, String sessionType);
    List<LibraryGateLog> findByUserOrderByEntryTimeDesc(User user);
    List<LibraryGateLog> findByStatusAndLogDateBefore(String status, LocalDate date);
}

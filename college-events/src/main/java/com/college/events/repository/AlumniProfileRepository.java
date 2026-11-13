package com.college.events.repository;

import com.college.events.model.AlumniProfile;
import com.college.events.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AlumniProfileRepository extends JpaRepository<AlumniProfile, Long> {
    Optional<AlumniProfile> findByUser(User user);
    Optional<AlumniProfile> findByUserId(Long userId);
    List<AlumniProfile> findByDepartmentOrderByGraduationYearDesc(String department);
    List<AlumniProfile> findByWillingToMentorTrue();
    List<AlumniProfile> findAllByOrderByGraduationYearDesc();

    @Query("select p from AlumniProfile p where p.directoryVisible = true and p.verifiedAlumni = true " +
           "and p.verifiedById is not null and p.verifiedById <> p.user.id and p.verifiedAt is not null and p.user.enabled = true " +
           "and (:department is null or p.department = :department) " +
           "and (:mentors = false or p.willingToMentor = true) order by p.graduationYear desc, p.id asc")
    Page<AlumniProfile> findVisible(@Param("department") String department,
                                  @Param("mentors") boolean mentors, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> lockOwner(@Param("id") Long id);

    @Query("select p from AlumniProfile p where p.verifiedAlumni = false or p.verifiedById is null or p.verifiedById = p.user.id " +
           "or p.verifiedAt is null order by p.id asc")
    Page<AlumniProfile> findPendingReview(Pageable pageable);
}

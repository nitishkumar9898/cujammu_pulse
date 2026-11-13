package com.college.events.repository;

import com.college.events.model.Notification;
import com.college.events.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserOrderByCreatedAtDesc(User user);
    List<Notification> findTop20ByUserOrderByCreatedAtDesc(User user);
    Slice<Notification> findByUserIdAndIdLessThanOrderByIdDesc(Long userId, Long beforeId, Pageable pageable);
    Slice<Notification> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);
    List<Notification> findTop5ByUserAndIsReadFalseOrderByCreatedAtDesc(User user);
    long countByUserAndIsReadFalse(User user);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.acknowledged = true, n.isRead = true where n.id = :id and n.user.id = :userId")
    int acknowledgeOwned(@Param("id") long id, @Param("userId") long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.isRead = true where n.id = :id and n.user.id = :userId")
    int markReadOwned(@Param("id") long id, @Param("userId") long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.isRead = true where n.user.id = :userId and n.isRead = false")
    int markAllUnreadOwned(@Param("userId") long userId);
}

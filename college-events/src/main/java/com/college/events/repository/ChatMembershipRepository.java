package com.college.events.repository;

import com.college.events.model.ChatMembership;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import java.util.Optional;

public interface ChatMembershipRepository extends JpaRepository<ChatMembership, Long> {
    Optional<ChatMembership> findByGroupIdAndUserId(long groupId, long userId);
    boolean existsByGroupIdAndUserIdAndActiveTrue(long groupId, long userId);
    @Query("select m from ChatMembership m join fetch m.user where m.groupId = :groupId order by m.id desc")
    Slice<ChatMembership> page(@Param("groupId") long groupId, Pageable pageable);
}

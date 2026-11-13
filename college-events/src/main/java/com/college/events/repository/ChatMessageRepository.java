package com.college.events.repository;

import com.college.events.model.ChatGroup;
import com.college.events.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    java.util.Optional<ChatMessage> findByGroupIdAndSenderIdAndRequestKey(Long groupId, Long senderId, String requestKey);
    long countByGroupIdAndAcceptedAtAfter(Long groupId, java.time.Instant since);
    long countByGroupIdAndSenderIdAndAcceptedAtAfter(Long groupId, Long senderId, java.time.Instant since);
    List<ChatMessage> findByGroupAndQuarantinedFalseOrderByCreatedAtAsc(ChatGroup group);
    List<ChatMessage> findTop100ByGroupAndQuarantinedFalseOrderByCreatedAtDesc(ChatGroup group);
    void deleteByGroupId(Long groupId);
}

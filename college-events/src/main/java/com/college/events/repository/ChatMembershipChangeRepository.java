package com.college.events.repository;
import com.college.events.model.ChatMembershipChange;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ChatMembershipChangeRepository extends JpaRepository<ChatMembershipChange, Long> { }

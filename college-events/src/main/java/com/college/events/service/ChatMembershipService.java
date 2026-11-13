package com.college.events.service;

import com.college.events.dto.ChatMembershipDtos.*;
import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import java.time.Instant;
import java.util.Set;

@Service @RequiredArgsConstructor
public class ChatMembershipService {
    private final ChatMembershipRepository members;
    private final ChatMembershipChangeRepository changes;
    private final ChatGroupRepository groups;
    private final UserRepository users;
    private final DomainAccess access;
    private final FeatureFlagService features;
    private final AuditService audit;
    private final NotificationService notifications;

    @Transactional(readOnly=true)
    public DepartmentGroups departmentGroups() {
        access.requireRole(Role.ADMIN);
        var rows = groups.findByGroupTypeOrderByIdAsc("PERMANENT_DEPARTMENT", PageRequest.of(0, 101));
        if (rows.size() > 100) throw new IllegalStateException("Department catalogue requires pagination");
        return new DepartmentGroups(rows.stream().map(g -> new DepartmentGroup(g.getId(), g.getName(), g.getRelatedDepartment())).toList());
    }

    @Transactional(readOnly=true)
    public Members members(long groupId, int page) {
        access.requireRole(Role.ADMIN);
        if (page < 0 || page > 10000) throw new IllegalArgumentException("Invalid page");
        requireDepartment(groups.findById(groupId).orElseThrow(DomainAccess::denied));
        var result = members.page(groupId, PageRequest.of(page, 20));
        return new Members(result.getContent().stream().map(this::view).toList(), page, result.hasNext());
    }

    @Transactional
    public Member change(long groupId, long userId, Change command) {
        User administrator = access.requireRole(Role.ADMIN);
        if (administrator.getId().equals(userId)) throw DomainAccess.denied();
        if (command.active()) features.requireEnabledForUpdate("MODULE_COMMUNITY_CHAT");
        requireDepartment(groups.findForUpdate(groupId).orElseThrow(DomainAccess::denied));
        User subject = users.findById(userId).orElseThrow(DomainAccess::denied);
        if (command.active() && (!subject.isEnabled()
                || !Set.of(Role.STUDENT, Role.FACULTY, Role.STAFF, Role.ORGANIZER).contains(subject.getRole()))) throw DomainAccess.denied();
        String reason = bounded(command.reason(), 3, 500);
        String source = command.active() ? bounded(command.sourceReference(), 3, 120) : "REVOCATION";
        var current = members.findByGroupIdAndUserId(groupId, userId);
        ChatMembership member;
        if (current.isEmpty()) {
            if (command.expectedRevision() != null || !command.active()) throw new IllegalStateException("Membership changed; refresh");
            member = new ChatMembership(); member.setGroupId(groupId); member.setUser(subject);
        } else {
            member = current.get();
            if (command.expectedRevision() == null || member.getRevision() != command.expectedRevision())
                throw new IllegalStateException("Membership changed; refresh");
            if (member.isActive() == command.active()) throw new IllegalStateException("Membership already has this state");
        }
        member.setActive(command.active()); members.saveAndFlush(member);
        changes.save(ChatMembershipChange.builder().groupId(groupId).userId(userId).actorId(administrator.getId())
                .revision(member.getRevision()).active(command.active()).sourceReference(source).reason(reason).changedAt(Instant.now()).build());
        audit.logAction(administrator.getEmail(), administrator.getRole().name(), "CHAT_MEMBERSHIP_CHANGED",
                "groupId="+groupId+";userId="+userId+";active="+command.active()+";revision="+member.getRevision());
        notifications.createNotification(subject, "Your department chat access has changed. Sign in to review your available groups.", "/app/");
        return view(member);
    }

    private Member view(ChatMembership member) {
        return new Member(member.getUser().getId(), member.getUser().getName(), member.isActive(), member.getRevision());
    }
    private static void requireDepartment(ChatGroup group) {
        if (!"PERMANENT_DEPARTMENT".equals(group.getGroupType()) || group.getRelatedEventId() != null
                || group.getRelatedDepartment() == null || group.getRelatedDepartment().isBlank()) throw DomainAccess.denied();
    }
    private static String bounded(String value, int min, int max) {
        if (value == null || value.trim().length() < min || value.length() > max || value.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid verification reference or reason");
        return value.trim();
    }
}

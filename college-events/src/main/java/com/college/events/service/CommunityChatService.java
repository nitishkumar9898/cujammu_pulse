package com.college.events.service;

import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.dto.CommunityChatDtos;
import com.college.events.security.DomainAccess;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class CommunityChatService {

    private final ChatGroupRepository chatGroupRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final RegistrationRepository registrations;
    private final EventRepository events;
    private final DomainAccess access;
    private final FeatureFlagService features;
    private final EventOperationsRepository operations;
    private final ChatMembershipRepository memberships;
    private final ChatPostingPolicy postingPolicy;
    private final java.time.Clock clock;
    private final ZoneId campusZone = ZoneId.of("Asia/Kolkata");

    @org.springframework.beans.factory.annotation.Autowired
    public CommunityChatService(ChatGroupRepository groups, ChatMessageRepository messages,
            RegistrationRepository registrations, EventRepository events, DomainAccess access,
            FeatureFlagService features, EventOperationsRepository operations, ChatMembershipRepository memberships, ChatPostingPolicy postingPolicy) {
        this(groups, messages, registrations, events, access, features, operations, memberships, postingPolicy, java.time.Clock.systemUTC());
    }

    CommunityChatService(ChatGroupRepository groups, ChatMessageRepository messages,
            RegistrationRepository registrations, EventRepository events, DomainAccess access,
            FeatureFlagService features, EventOperationsRepository operations, ChatMembershipRepository memberships, ChatPostingPolicy postingPolicy, java.time.Clock clock) {
        this.chatGroupRepository = groups; this.chatMessageRepository = messages;
        this.registrations = registrations; this.events = events; this.access = access;
        this.features = features; this.operations = operations; this.clock = clock;
        this.memberships = memberships;
        this.postingPolicy = postingPolicy;
    }

    @Transactional
    public CommunityChatDtos.Message postMessage(Long groupId, String content, String requestKey) {
        boolean eventGroup = "EPHEMERAL_EVENT".equals(chatGroupRepository.findTypeById(groupId).orElseThrow(DomainAccess::denied));
        if (eventGroup) features.requireEnabledForUpdate("MODULE_EVENTS");
        features.requireEnabledForUpdate("MODULE_COMMUNITY_CHAT");
        if (eventGroup) operations.findForUpdate(chatGroupRepository.findEventIdById(groupId).orElseThrow(DomainAccess::denied))
                .orElseThrow(() -> new IllegalStateException("Event operations are unavailable"));
        ChatGroup group = chatGroupRepository.findForUpdate(groupId).orElseThrow(DomainAccess::denied);
        User sender = access.currentUser();
        requireMember(group, sender);

        if (content == null || content.isBlank() || content.length() > 2000)
            throw new IllegalArgumentException("Message must contain 1 to 2000 characters");
        if (requestKey == null || !requestKey.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw new IllegalArgumentException("A UUID request key is required");
        String normalizedKey = java.util.UUID.fromString(requestKey).toString();
        String cleanContent = content.trim();
        String fingerprint = fingerprint(cleanContent);
        var previous = chatMessageRepository.findByGroupIdAndSenderIdAndRequestKey(groupId, sender.getId(), normalizedKey);
        if (previous.isPresent()) {
            ChatMessage accepted = previous.get();
            if (accepted.isQuarantined()) throw DomainAccess.denied();
            if (!fingerprint.equals(accepted.getRequestHash())) throw new IllegalStateException("Request key was already used for different content");
            return message(accepted);
        }
        if ("EPHEMERAL_EVENT".equals(group.getGroupType()) && events.findById(group.getRelatedEventId())
                .map(event -> event.getStatus() != EventStatus.APPROVED).orElse(true))
            throw new IllegalStateException("Event chat is read-only after cancellation or completion");

        boolean isAdminOrOrganizer = isModerator(group, sender);

        // Check Admin-Only Lock
        if (group.isOnlyAdminsCanPost() && !isAdminOrOrganizer) {
            throw new IllegalStateException("Only group admins can send messages in this group.");
        }

        // Check Night-Silence Mode (10:00 PM to 07:00 AM)
        if (group.isNightSilenceEnabled()) {
            if (isNightSilenceActive()) {
                throw new IllegalStateException("Night Silence is active (10:00 PM to 07:00 AM).");
            }
        }

        // Capture time after contention, never trust client timestamps. Boundary is strictly after now-60s.
        // Future-stamped rows count conservatively if an operator moves the server clock backwards.
        java.time.Instant acceptedAt = clock.instant();
        java.time.Instant since = acceptedAt.minusSeconds(60);
        if (chatMessageRepository.countByGroupIdAndSenderIdAndAcceptedAtAfter(groupId, sender.getId(), since) >= postingPolicy.senderPerMinute()
                || chatMessageRepository.countByGroupIdAndAcceptedAtAfter(groupId, since) >= postingPolicy.groupPerMinute())
            throw new ChatRateLimitException();

        ChatMessage msg = ChatMessage.builder()
                .group(group)
                .sender(sender)
                .content(cleanContent)
                .requestKey(normalizedKey).requestHash(fingerprint).acceptedAt(acceptedAt)
                // Chat media requires the CF-30 private media gateway and is deliberately unavailable.
                .mediaUrl(null)
                .mediaType("NONE")
                .build();
        return message(chatMessageRepository.saveAndFlush(msg));
    }

    private static String fingerprint(String content) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("Required digest implementation unavailable", unavailable);
        }
    }

    @Transactional
    public CommunityChatDtos.Messages getGroupMessages(Long groupId) {
        requireVisible();
        ChatGroup group = chatGroupRepository.findForUpdate(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Chat group not found: " + groupId));
        User user = access.currentUser();
        requireMember(group, user);
        List<ChatMessage> recent = new ArrayList<>(chatMessageRepository.findTop100ByGroupAndQuarantinedFalseOrderByCreatedAtDesc(group));
        Collections.reverse(recent);
        return new CommunityChatDtos.Messages(recent.stream().map(this::message).toList());
    }

    @Transactional
    public ChatGroup createEventGroup(Event event) {
        features.requireEnabledForUpdate("MODULE_EVENTS");
        features.requireEnabledForUpdate("MODULE_COMMUNITY_CHAT");
        operations.findForUpdate(event.getId()).orElseThrow(() -> new IllegalStateException("Event operations are unavailable"));
        event = access.requireEventManager(event.getId());
        if (event.getStatus() != EventStatus.APPROVED) throw new IllegalStateException("Event group provisioning requires an approved event");
        Optional<ChatGroup> existing = chatGroupRepository.findByRelatedEventId(event.getId());
        if (existing.isPresent()) {
            return existing.get();
        }

        ChatGroup group = ChatGroup.builder()
                .name(event.getTitle() + " (Official Participant Group)")
                .groupType("EPHEMERAL_EVENT")
                .relatedEventId(event.getId())
                .relatedDepartment(event.getTargetDepartment())
                .onlyAdminsCanPost(false)
                .nightSilenceEnabled(true)
                .build();

        return chatGroupRepository.save(group);
    }

    @Transactional
    public void deleteEventGroup(Long eventId) {
        access.requireEventManager(eventId);
        throw new IllegalStateException("Chat cleanup is unavailable until protected-hold and audited purge controls are implemented");
    }

    @Transactional(readOnly = true)
    public CommunityChatDtos.Groups getAllActiveGroups() {
        return new CommunityChatDtos.Groups(groupPage(0).items());
    }

    @Transactional(readOnly = true)
    public CommunityChatDtos.GroupPage groupPage(int page) {
        requireVisible();
        if (page < 0 || page > 10000) throw new IllegalArgumentException("Invalid page");
        User user = access.currentUser();
        var result = chatGroupRepository.visiblePage(user.getId(), user.getRole() == Role.FACULTY || user.getRole() == Role.ORGANIZER,
                features.isEnabled("MODULE_EVENTS"), org.springframework.data.domain.PageRequest.of(page, 20));
        return new CommunityChatDtos.GroupPage(result.getContent().stream()
                .filter(g -> isMember(g, user)).map(g -> group(g, user)).toList(), page, result.hasNext());
    }

    private CommunityChatDtos.Group group(ChatGroup group, User user) {
        boolean adminOrOrganizer = isModerator(group, user);
        boolean silent = group.isNightSilenceEnabled() && isNightSilenceActive();
        return new CommunityChatDtos.Group(group.getId(), group.getName(), group.getGroupType(), group.isNightSilenceEnabled(),
                !(group.isOnlyAdminsCanPost() && !adminOrOrganizer) && !silent
                        && (group.getRelatedEventId() == null || events.findById(group.getRelatedEventId())
                            .map(event -> event.getStatus() == EventStatus.APPROVED).orElse(false)), silent);
    }

    private CommunityChatDtos.Message message(ChatMessage value) {
        return new CommunityChatDtos.Message(value.getId(), value.getSender().getName(), value.getContent(), value.getCreatedAt());
    }

    private void requireMember(ChatGroup group, User user) {
        if (!isMember(group, user)) throw DomainAccess.denied();
    }

    private boolean isMember(ChatGroup group, User user) {
        if ("PERMANENT_DEPARTMENT".equals(group.getGroupType()))
            return group.getRelatedEventId() == null && memberships.existsByGroupIdAndUserIdAndActiveTrue(group.getId(), user.getId());
        if ("EPHEMERAL_EVENT".equals(group.getGroupType()) && group.getRelatedEventId() != null) {
            if (!features.isEnabled("MODULE_EVENTS")) return false;
            return registrations.existsByUserIdAndEventIdAndStatusIn(user.getId(), group.getRelatedEventId(),
                    java.util.EnumSet.of(RegistrationStatus.REGISTERED, RegistrationStatus.ATTENDED))
                    || events.findById(group.getRelatedEventId()).map(event -> access.isEventOwner(user, event)).orElse(false);
        }
        return false;
    }

    private boolean isNightSilenceActive() {
        LocalTime now = LocalTime.now(clock.withZone(campusZone));
        return !now.isBefore(LocalTime.of(22, 0)) || now.isBefore(LocalTime.of(7, 0));
    }

    private boolean isModerator(ChatGroup group, User user) {
        return ("EPHEMERAL_EVENT".equals(group.getGroupType())
                && group.getRelatedEventId() != null
                && (events.findById(group.getRelatedEventId()).map(event -> access.isEventOwner(user, event)).orElse(false)
                || group.getModeratorUserId() != null && group.getModeratorUserId().equals(user.getId())));
    }

    private void requireVisible() {
        if (!features.isEnabled("MODULE_COMMUNITY_CHAT"))
            throw new IllegalStateException("Chat is unavailable");
    }
}

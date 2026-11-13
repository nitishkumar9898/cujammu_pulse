package com.college.events.service;

import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommunityChatPolicyTest {
    private final ChatGroupRepository groups = mock(ChatGroupRepository.class);
    private final ChatMessageRepository messages = mock(ChatMessageRepository.class);
    private final RegistrationRepository registrations = mock(RegistrationRepository.class);
    private final EventRepository events = mock(EventRepository.class);
    private final DomainAccess access = mock(DomainAccess.class);
    private final FeatureFlagService features = mock(FeatureFlagService.class);
    private final EventOperationsRepository operations = mock(EventOperationsRepository.class);
    private final ChatMembershipRepository memberships = mock(ChatMembershipRepository.class);
    private final User owner = User.builder().id(1L).role(Role.FACULTY).name("Synthetic owner").build();
    private final User student = User.builder().id(2L).role(Role.STUDENT).department("CSIT")
            .name("Synthetic student").build();
    private final Event event = Event.builder().id(10L).organizer(owner).status(EventStatus.APPROVED).build();
    private final ChatGroup group = ChatGroup.builder().id(20L).name("Synthetic event group")
            .groupType("EPHEMERAL_EVENT").relatedEventId(10L).nightSilenceEnabled(true).build();

    @BeforeEach
    void setup() {
        when(features.isEnabled("MODULE_EVENTS")).thenReturn(true);
        when(features.isEnabled("MODULE_COMMUNITY_CHAT")).thenReturn(true);
        when(groups.findById(20L)).thenReturn(Optional.of(group));
        when(groups.findForUpdate(20L)).thenReturn(Optional.of(group));
        when(groups.findTypeById(20L)).thenAnswer(i -> Optional.of(group.getGroupType()));
        when(groups.findEventIdById(20L)).thenAnswer(i -> Optional.ofNullable(group.getRelatedEventId()));
        when(groups.visiblePage(anyLong(), anyBoolean(), anyBoolean(), any())).thenReturn(new org.springframework.data.domain.SliceImpl<>(List.of(group)));
        when(events.findById(10L)).thenReturn(Optional.of(event));
        when(operations.findForUpdate(10L)).thenReturn(Optional.of(EventOperations.builder().event(event).build()));
        when(messages.saveAndFlush(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage saved = invocation.getArgument(0);
            saved.setId(30L);
            saved.setCreatedAt(LocalDateTime.of(2030, 1, 1, 12, 0));
            return saved;
        });
        member(student, RegistrationStatus.REGISTERED);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"STUDENT", "FACULTY", "ADMIN"})
    void selfDeclaredDepartmentNeverGrantsDirectoryReadOrPosting(Role role) {
        User actor = User.builder().id(5L).role(role).department("CSIT").build();
        when(access.currentUser()).thenReturn(actor);
        group.setGroupType("PERMANENT_DEPARTMENT");
        group.setRelatedEventId(null);
        group.setRelatedDepartment("CSIT");
        var service = at("12:00:00");

        assertTrue(service.getAllActiveGroups().items().isEmpty());
        assertThrows(AccessDeniedException.class, () -> service.getGroupMessages(20L));
        assertThrows(AccessDeniedException.class, () -> service.postMessage(20L, "Private department message", java.util.UUID.randomUUID().toString()));
        verifyNoInteractions(messages);
    }

    @Test
    void evenAuthorizedCompletedEventManagerCannotInvokeHoldUnawareDeletion() {
        event.setStatus(EventStatus.COMPLETED);
        when(access.requireEventManager(10L)).thenReturn(event);

        assertThrows(IllegalStateException.class, () -> at("12:00:00").deleteEventGroup(10L));

        verify(access).requireEventManager(10L);
        verifyNoInteractions(groups, messages);
    }

    @ParameterizedTest
    @CsvSource({"21:59:59,true", "22:00:00,false", "00:00:00,false", "06:59:59,false", "07:00:00,true"})
    void campusQuietWindowHasExactBoundariesInBothProjectionAndCommand(String localTime, boolean allowed) {
        var service = at(localTime);
        var view = service.getAllActiveGroups().items().get(0);
        assertEquals(allowed, view.canPost());
        assertEquals(!allowed, view.nightSilenceActive());

        if (allowed) {
            assertEquals("Hello", service.postMessage(20L, "  Hello  ", java.util.UUID.randomUUID().toString()).content());
            verify(messages).saveAndFlush(any(ChatMessage.class));
        } else {
            assertThrows(IllegalStateException.class, () -> service.postMessage(20L, "Hello", java.util.UUID.randomUUID().toString()));
            verify(messages, never()).saveAndFlush(any(ChatMessage.class));
        }
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"FACULTY", "ORGANIZER"})
    void unrelatedPrivilegedParticipantCannotBypassGroupAdminOnlyPolicy(Role role) {
        User participant = User.builder().id(7L).role(role).name("Synthetic participant").build();
        member(participant, RegistrationStatus.REGISTERED);
        group.setOnlyAdminsCanPost(true);
        var service = at("12:00:00");

        assertFalse(service.getAllActiveGroups().items().get(0).canPost());
        assertThrows(IllegalStateException.class, () -> service.postMessage(20L, "Unauthorized announcement", java.util.UUID.randomUUID().toString()));
        verify(messages, never()).saveAndFlush(any(ChatMessage.class));
    }

    @Test
    void owningFacultyCanPostDaytimeAnnouncementButCannotBypassQuietWindow() {
        when(access.currentUser()).thenReturn(owner);
        when(access.isEventOwner(owner, event)).thenReturn(true);
        group.setOnlyAdminsCanPost(true);
        var day = at("12:00:00");
        assertTrue(day.getAllActiveGroups().items().get(0).canPost());
        assertEquals("Owner announcement", day.postMessage(20L, "Owner announcement", java.util.UUID.randomUUID().toString()).content());

        var night = at("22:00:00");
        assertFalse(night.getAllActiveGroups().items().get(0).canPost());
        assertThrows(IllegalStateException.class, () -> night.postMessage(20L, "No override", java.util.UUID.randomUUID().toString()));
        verify(messages, times(1)).saveAndFlush(any(ChatMessage.class));
    }

    @Test
    void administratorHasNoImplicitMessageAccess() {
        when(access.currentUser()).thenReturn(User.builder().id(9L).role(Role.ADMIN).build());
        assertTrue(at("12:00:00").getAllActiveGroups().items().isEmpty());
        assertThrows(AccessDeniedException.class, () -> at("12:00:00").getGroupMessages(20L));
        assertThrows(AccessDeniedException.class, () -> at("22:00:00").postMessage(20L, "No override", java.util.UUID.randomUUID().toString()));
        verifyNoInteractions(messages);
    }

    @Test
    void disabledChatRejectsDirectoryReadAndPostBeforeRetrievingPrivateData() {
        when(features.isEnabled("MODULE_COMMUNITY_CHAT")).thenReturn(false);
        doThrow(new IllegalStateException("Feature disabled")).when(features)
                .requireEnabledForUpdate("MODULE_COMMUNITY_CHAT");
        var service = at("12:00:00");

        assertThrows(IllegalStateException.class, service::getAllActiveGroups);
        assertThrows(IllegalStateException.class, () -> service.getGroupMessages(20L));
        assertThrows(IllegalStateException.class, () -> service.postMessage(20L, "Hello", java.util.UUID.randomUUID().toString()));
        verifyNoInteractions(messages, registrations, events, access);
    }

    @Test
    void disabledEventsHideEventChatButKeepExplicitDepartmentMembershipUsable() {
        when(features.isEnabled("MODULE_EVENTS")).thenReturn(false);
        doThrow(new IllegalStateException("Feature disabled")).when(features)
                .requireEnabledForUpdate("MODULE_EVENTS");
        var service = at("12:00:00");
        assertTrue(service.getAllActiveGroups().items().isEmpty());
        assertThrows(AccessDeniedException.class, () -> service.getGroupMessages(20L));
        assertThrows(IllegalStateException.class, () -> service.postMessage(20L, "Hello", java.util.UUID.randomUUID().toString()));
        group.setGroupType("PERMANENT_DEPARTMENT");
        group.setRelatedEventId(null);
        when(memberships.existsByGroupIdAndUserIdAndActiveTrue(20L, student.getId())).thenReturn(true);
        assertEquals(1, service.getAllActiveGroups().items().size());
        assertEquals("Department remains available", service.postMessage(20L, "Department remains available", java.util.UUID.randomUUID().toString()).content());
    }

    @ParameterizedTest
    @EnumSource(value = EventStatus.class, names = {"CANCELLED", "COMPLETED"})
    void terminalEventsKeepAuthorizedHistoryButRejectNewMessages(EventStatus status) {
        event.setStatus(status);
        when(messages.findTop100ByGroupAndQuarantinedFalseOrderByCreatedAtDesc(group)).thenReturn(List.of(
                ChatMessage.builder().id(99L).sender(student).content("Earlier history")
                        .createdAt(LocalDateTime.of(2029, 12, 31, 12, 0)).build()));
        var service = at("12:00:00");
        assertFalse(service.getAllActiveGroups().items().get(0).canPost());
        assertEquals("Earlier history", service.getGroupMessages(20L).items().get(0).content());
        assertThrows(IllegalStateException.class, () -> service.postMessage(20L, "Late message", java.util.UUID.randomUUID().toString()));
        verify(messages, never()).saveAndFlush(any(ChatMessage.class));
    }

    @ParameterizedTest
    @EnumSource(value = RegistrationStatus.class, names = {"WAITLISTED", "CANCELLED"})
    void ineligibleEventRegistrationCannotReadOrWrite(RegistrationStatus status) {
        member(student, status);
        var service = at("12:00:00");
        assertTrue(service.getAllActiveGroups().items().isEmpty());
        assertThrows(AccessDeniedException.class, () -> service.getGroupMessages(20L));
        assertThrows(AccessDeniedException.class, () -> service.postMessage(20L, "Hello", java.util.UUID.randomUUID().toString()));
        verifyNoInteractions(messages);
    }

    @Test
    void serviceRejectsInvalidContentWithoutRelyingOnHttpValidation() {
        var service = at("12:00:00");
        for (String content : new String[] {null, "", "  ", "a".repeat(2001)}) {
            assertThrows(IllegalArgumentException.class, () -> service.postMessage(20L, content, java.util.UUID.randomUUID().toString()));
        }
        verifyNoInteractions(messages);
    }

    @Test
    void missingLifecycleGuardRejectsMessageBeforePersistence() {
        when(operations.findForUpdate(10L)).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class, () -> at("12:00:00").postMessage(20L, "Hello", java.util.UUID.randomUUID().toString()));
        verifyNoInteractions(messages);
    }

    private void member(User user, RegistrationStatus status) {
        when(access.currentUser()).thenReturn(user);
        when(registrations.existsByUserIdAndEventIdAndStatusIn(eq(user.getId()), eq(event.getId()), any()))
                .thenReturn(status == RegistrationStatus.REGISTERED || status == RegistrationStatus.ATTENDED);
    }

    private CommunityChatService at(String localTime) {
        var instant = LocalDate.of(2030, 1, 1).atTime(LocalTime.parse(localTime))
                .atZone(ZoneId.of("Asia/Kolkata")).toInstant();
        return new CommunityChatService(groups, messages, registrations, events, access, features, operations, memberships,
                new ChatPostingPolicy(20, 200), Clock.fixed(instant, ZoneId.of("UTC")));
    }
}

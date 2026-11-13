package com.college.events.service;

import com.college.events.config.FeatureFlagInterceptor;
import com.college.events.model.*;
import com.college.events.repository.FeatureFlagRepository;
import com.college.events.security.DomainAccess;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RuntimeFeaturePolicyTest {
    FeatureFlagRepository flags = mock(FeatureFlagRepository.class);
    DomainAccess access = mock(DomainAccess.class);
    AuditService audit = mock(AuditService.class);
    NotificationService notifications = mock(NotificationService.class);
    FeatureFlagService service = new FeatureFlagService(flags, access, audit, notifications);

    @Test void committedStateIsReadAgainWithoutProcessRestart() {
        when(flags.findAll()).thenReturn(List.of(flag("MODULE_EVENTS", true)), List.of(flag("MODULE_EVENTS", false)));
        assertTrue(service.isEnabled("MODULE_EVENTS"));
        assertFalse(service.isEnabled("MODULE_EVENTS"));
        assertFalse(service.isEnabled("UNKNOWN"));
    }

    @Test void dependencyDisableDoesNotMutateConfiguredChildFlag() {
        var child = flag("MODULE_TEAMS", true);
        when(flags.findAll()).thenReturn(List.of(flag("MODULE_EVENTS", false), child));
        assertFalse(service.getAllFlagsMap().get("MODULE_TEAMS"));
        assertTrue(child.isEnabled());
        verify(flags, never()).save(any());
    }

    @Test void eventActionIsEffectivelyDisabledWithParentButKeepsConfiguredState() {
        var action = flag("ACTION_EVENT_CANCEL", true);
        when(flags.findAll()).thenReturn(List.of(flag("MODULE_EVENTS", false), action));
        assertFalse(service.getAllFlagsMap().get("ACTION_EVENT_CANCEL"));
        assertTrue(action.isEnabled());
        verify(flags, never()).save(any());
    }

    @Test void actionGuardFailsClosedWhenActionIsDisabled() {
        when(flags.findByFeatureKeyForUpdate("ACTION_EVENT_POSTPONE"))
                .thenReturn(Optional.of(flag("ACTION_EVENT_POSTPONE", false)));
        assertThrows(IllegalStateException.class, () -> service.requireEnabledForUpdate("ACTION_EVENT_POSTPONE"));
    }

    @Test void libraryActionIsEffectivelyDisabledWhenLibraryModuleIsOff() {
        var entry = flag("ACTION_LIBRARY_ENTRY", true);
        when(flags.findAll()).thenReturn(List.of(flag("MODULE_LIBRARY_TURNSTILE", false), entry));
        assertFalse(service.getAllFlagsMap().get("ACTION_LIBRARY_ENTRY"));
        assertTrue(entry.isEnabled());
    }

    @Test void unverifiedLibraryScanActionsStayOffEvenIfPersistedAsEnabled() {
        var entry = flag("ACTION_LIBRARY_ENTRY", true);
        var exit = flag("ACTION_LIBRARY_EXIT", true);
        when(flags.findAll()).thenReturn(List.of(flag("MODULE_LIBRARY_TURNSTILE", true), entry, exit));
        var effective = service.getAllFlagsMap();
        assertFalse(effective.get("ACTION_LIBRARY_ENTRY"));
        assertFalse(effective.get("ACTION_LIBRARY_EXIT"));
        assertTrue(entry.isEnabled());
        assertTrue(exit.isEnabled());
        verify(flags, never()).save(any());
    }

    @Test void unverifiedLibraryScanActionsCannotBeEnabledOrInvoked() {
        User admin = User.builder().id(1L).email("admin@example.test").role(Role.ADMIN).build();
        var entry = flag("ACTION_LIBRARY_ENTRY", false);
        var exit = flag("ACTION_LIBRARY_EXIT", false);
        when(access.requireRole(Role.ADMIN)).thenReturn(admin);
        when(flags.findByFeatureKeyForUpdate("ACTION_LIBRARY_ENTRY")).thenReturn(Optional.of(entry));
        when(flags.findByFeatureKeyForUpdate("ACTION_LIBRARY_EXIT")).thenReturn(Optional.of(exit));
        assertThrows(IllegalArgumentException.class,
                () -> service.setFeatureEnabled("ACTION_LIBRARY_ENTRY", true, "ADMIN"));
        assertThrows(IllegalArgumentException.class,
                () -> service.setFeatureEnabled("ACTION_LIBRARY_ENTRY", true, entry.getRevision(), "Enable library"));
        assertThrows(IllegalArgumentException.class,
                () -> service.setFeatureEnabled("ACTION_LIBRARY_EXIT", true, "ADMIN"));
        assertThrows(IllegalArgumentException.class,
                () -> service.setFeatureEnabled("ACTION_LIBRARY_EXIT", true, exit.getRevision(), "Enable library exit"));
        assertThrows(IllegalStateException.class,
                () -> service.requireEnabledForUpdate("ACTION_LIBRARY_ENTRY"));
        assertThrows(IllegalStateException.class,
                () -> service.requireEnabledForUpdate("ACTION_LIBRARY_EXIT"));
        verify(flags, never()).save(any());
    }

    @Test void readGuardFailsClosedWithoutRequiringAWriteLock() {
        when(flags.findAll()).thenReturn(List.of(flag("MODULE_LIBRARY_TURNSTILE", false)));
        assertThrows(IllegalStateException.class, () -> service.requireEnabledForRead("MODULE_LIBRARY_TURNSTILE"));
    }

    @Test void suppliedAdminNameCannotAuthorizeToggle() {
        when(access.requireRole(Role.ADMIN)).thenThrow(new AccessDeniedException("Denied"));
        assertThrows(AccessDeniedException.class, () -> service.setFeatureEnabled("MODULE_EVENTS", false, "ADMIN"));
        verifyNoInteractions(flags);
    }

    @Test void disablingNotificationsClosesCurrentProcessStreams() {
        User admin = User.builder().id(1L).email("admin@example.test").role(Role.ADMIN).build();
        FeatureFlag notificationFlag = flag("MODULE_NOTIFICATIONS", true);
        when(access.requireRole(Role.ADMIN)).thenReturn(admin);
        when(flags.findByFeatureKeyForUpdate("MODULE_NOTIFICATIONS")).thenReturn(Optional.of(notificationFlag));
        when(flags.save(notificationFlag)).thenReturn(notificationFlag);

        service.setFeatureEnabled("MODULE_NOTIFICATIONS", false, "ignored-client-name");

        assertFalse(notificationFlag.isEnabled());
        verify(notifications).closeAllStreams();
    }

    @Test void notificationStreamsCloseOnlyAfterToggleCommits() {
        User admin = User.builder().id(1L).email("admin@example.test").role(Role.ADMIN).build();
        FeatureFlag notificationFlag = flag("MODULE_NOTIFICATIONS", true);
        when(access.requireRole(Role.ADMIN)).thenReturn(admin);
        when(flags.findByFeatureKeyForUpdate("MODULE_NOTIFICATIONS")).thenReturn(Optional.of(notificationFlag));
        when(flags.save(notificationFlag)).thenReturn(notificationFlag);
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            service.setFeatureEnabled("MODULE_NOTIFICATIONS", false, "ignored-client-name");
            verify(notifications, never()).closeAllStreams();
            org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
                    .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);
            verify(notifications).closeAllStreams();
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test void rolledBackNotificationDisableKeepsExistingStreamsOpen() {
        User admin = User.builder().id(1L).email("admin@example.test").role(Role.ADMIN).build();
        FeatureFlag notificationFlag = flag("MODULE_NOTIFICATIONS", true);
        when(access.requireRole(Role.ADMIN)).thenReturn(admin);
        when(flags.findByFeatureKeyForUpdate("MODULE_NOTIFICATIONS")).thenReturn(Optional.of(notificationFlag));
        when(flags.save(notificationFlag)).thenReturn(notificationFlag);
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            service.setFeatureEnabled("MODULE_NOTIFICATIONS", false, "ignored-client-name");
            org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
                    .forEach(sync -> sync.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK));
            verify(notifications, never()).closeAllStreams();
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test void lockedSafeguardsCannotBeDisabledEvenByAdmin() {
        when(access.requireRole(Role.ADMIN)).thenReturn(User.builder().id(1L).role(Role.ADMIN).build());
        when(flags.findByFeatureKeyForUpdate("FOUNDATION_AUTH")).thenReturn(Optional.of(flag("FOUNDATION_AUTH", true)));
        assertThrows(IllegalArgumentException.class, () -> service.setFeatureEnabled("FOUNDATION_AUTH", false, "ADMIN"));
        verify(flags, never()).save(any());
    }

    @Test void nestedTeamEndpointChecksTeamFlagEvenWhenEventsEnabled() throws Exception {
        when(flags.findAll()).thenReturn(List.of(flag("MODULE_EVENTS", true), flag("MODULE_TEAMS", false)));
        var request = new MockHttpServletRequest("GET", "/api/v1/cujammu/events/12/teams");
        var response = new MockHttpServletResponse();
        assertFalse(new FeatureFlagInterceptor(service).preHandle(request, response, new Object()));
        assertEquals(404, response.getStatus());
    }
    @Test void calendarAndFeedbackSelectorUseTheirOwnRuntimeControls() throws Exception {
        when(flags.findAll()).thenReturn(List.of(flag("MODULE_EVENTS", true), flag("MODULE_CALENDAR", false),
                flag("MODULE_INSIGHTS", true)));
        var calendarResponse = new MockHttpServletResponse();
        assertFalse(new FeatureFlagInterceptor(service).preHandle(
                new MockHttpServletRequest("GET", "/api/v1/cujammu/events/calendar/mine"), calendarResponse, new Object()));
        assertEquals(404, calendarResponse.getStatus());

        var insightsResponse = new MockHttpServletResponse();
        assertTrue(new FeatureFlagInterceptor(service).preHandle(
                new MockHttpServletRequest("GET", "/api/v1/cujammu/insights/events/mine"), insightsResponse, new Object()));

        when(flags.findAll()).thenReturn(List.of(flag("MODULE_EVENTS", false), flag("MODULE_CALENDAR", true)));
        assertFalse(service.getAllFlagsMap().get("MODULE_CALENDAR"));
    }
    @Test void readinessIsDisabledByItsEventsParentOnServerRoutes() throws Exception {
        when(flags.findAll()).thenReturn(List.of(flag("MODULE_EVENTS", false), flag("MODULE_READINESS", true)));
        assertFalse(service.isEnabled("MODULE_READINESS"));
        var response = new MockHttpServletResponse();
        assertFalse(new FeatureFlagInterceptor(service).preHandle(
                new MockHttpServletRequest("GET", "/api/v1/cujammu/readiness/events/7/tasks"), response, new Object()));
        assertEquals(404, response.getStatus());
    }
    @Test void readinessMutationLocksEventsParentBeforeChild() {
        when(flags.findByFeatureKeyForUpdate("MODULE_EVENTS")).thenReturn(Optional.of(flag("MODULE_EVENTS", true)));
        when(flags.findByFeatureKeyForUpdate("MODULE_READINESS")).thenReturn(Optional.of(flag("MODULE_READINESS", true)));
        service.requireEnabledForUpdate("MODULE_READINESS", "MODULE_EVENTS");
        var order = inOrder(flags);
        order.verify(flags).findByFeatureKeyForUpdate("MODULE_EVENTS");
        order.verify(flags).findByFeatureKeyForUpdate("MODULE_READINESS");
    }
    @Test void disabledEventsParentStopsReadinessMutationBeforeChildLock() {
        when(flags.findByFeatureKeyForUpdate("MODULE_EVENTS")).thenReturn(Optional.of(flag("MODULE_EVENTS", false)));
        assertThrows(IllegalStateException.class, () -> service.requireEnabledForUpdate("MODULE_READINESS", "MODULE_EVENTS"));
        verify(flags, never()).findByFeatureKeyForUpdate("MODULE_READINESS");
    }
    private FeatureFlag flag(String key, boolean value) { return FeatureFlag.builder().featureKey(key).enabled(value).build(); }
}

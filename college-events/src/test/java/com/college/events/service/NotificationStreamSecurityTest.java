package com.college.events.service;

import com.college.events.model.*;
import com.college.events.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationStreamSecurityTest {
    final NotificationRepository notifications = mock(NotificationRepository.class);
    final FeatureFlagRepository flags = mock(FeatureFlagRepository.class);
    final UserRepository accounts = mock(UserRepository.class);
    final NotificationService service = spy(new NotificationService(notifications, flags));
    final User owner = User.builder().id(41L).role(Role.STUDENT).email("owner@example.invalid")
            .password("synthetic-hash").enabled(true).build();
    final List<SseEmitter> created = new ArrayList<>();

    @BeforeEach void setup() throws Exception {
        ReflectionTestUtils.setField(service, "streamAccounts", accounts);
        when(flags.findByFeatureKey("MODULE_NOTIFICATIONS")).thenReturn(Optional.of(
                FeatureFlag.builder().featureKey("MODULE_NOTIFICATIONS").enabled(true).build()));
        when(accounts.findCurrentStreamAccount(anyLong())).thenReturn(Optional.of(currentAccount(owner)));
        doAnswer(invocation -> {
            SseEmitter emitter = mock(SseEmitter.class);
            created.add(emitter);
            return emitter;
        }).when(service).newEmitter();
    }

    @Test void productionEmitterHasFiniteTimeout() {
        assertEquals(60_000L, new NotificationService(notifications, flags).newEmitter().getTimeout());
    }

    @Test void limitsPerAccountAndReleasesSlotOnCompletion() {
        for (int i = 0; i < 3; i++) service.subscribe(owner);
        assertThrows(NotificationService.StreamCapacityException.class, () -> service.subscribe(owner));
        var completion = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(created.get(0)).onCompletion(completion.capture());
        completion.getValue().run();
        assertDoesNotThrow(() -> service.subscribe(owner));
    }

    @Test void globalLimitCannotBeBypassedUsingDifferentAccountsAndCloseAllReleasesSlots() {
        for (long i = 1; i <= 256; i++) {
            service.subscribe(User.builder().id(i).role(Role.STUDENT).email(owner.getEmail())
                    .password("synthetic-hash").enabled(true).build());
        }
        assertThrows(NotificationService.StreamCapacityException.class,
                () -> service.subscribe(owner));
        service.closeAllStreams();
        assertDoesNotThrow(() -> service.subscribe(owner));
    }

    @Test void staleOrUnavailableAccountFailsClosedBeforeStreamAdmission() {
        when(accounts.findCurrentStreamAccount(41L)).thenReturn(Optional.empty());
        SseEmitter emitter = service.subscribe(owner);
        verify(emitter).complete();
        verify(emitter, never()).onCompletion(any());
    }

    @Test void accountChangeRevokesStreamBeforePrivateEventCanBeSent() throws Exception {
        SseEmitter emitter = service.subscribe(owner);
        clearInvocations(emitter);
        when(accounts.findCurrentStreamAccount(41L)).thenReturn(Optional.empty());
        publish();
        verify(emitter).complete();
        verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
        verify(accounts, times(2)).findCurrentStreamAccount(41L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"enabled", "role", "email", "password"})
    void everyAccountAuthorityChangeInvalidatesImmutableSubscriptionSnapshot(String field) throws Exception {
        SseEmitter emitter = service.subscribe(owner);
        clearInvocations(emitter);
        switch (field) {
            case "enabled" -> owner.setEnabled(false);
            case "role" -> owner.setRole(Role.ADMIN);
            case "email" -> owner.setEmail("OWNER@example.invalid");
            case "password" -> owner.setPassword("SYNTHETIC-HASH");
            default -> throw new AssertionError(field);
        }
        publish();
        verify(emitter).complete();
        verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test void validStreamGetsOnlyGenericInvalidationWithoutPrivateMessageOrResourceId() throws Exception {
        SseEmitter emitter = service.subscribe(owner);
        clearInvocations(emitter);
        publish();
        var event = org.mockito.ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        verify(emitter).send(event.capture());
        var data = event.getValue().build().stream().map(item -> item.getData()).toList();
        assertTrue(data.contains(Map.of("status", "invalidate")));
        assertFalse(data.toString().contains("SYNTHETIC_PRIVATE_MESSAGE"));
        assertFalse(data.toString().contains("/events/918273"));
        assertFalse(data.toString().contains("918273"));
    }

    @Test void databaseFailureClosesStreamWithoutLeakingOrBreakingCommittedInboxWrite() {
        SseEmitter emitter = service.subscribe(owner);
        when(accounts.findCurrentStreamAccount(anyLong()))
                .thenThrow(new IllegalStateException("synthetic-db-failure"));
        assertDoesNotThrow(this::publish);
        verify(emitter).complete();
    }

    private static UserRepository.StreamAccountState currentAccount(User user) {
        return new UserRepository.StreamAccountState() {
            public boolean getEnabled() { return user.isEnabled(); }
            public Role getRole() { return user.getRole(); }
            public String getEmail() { return user.getEmail(); }
            public String getPassword() { return user.getPassword(); }
        };
    }

    private void publish() {
        when(flags.findByFeatureKeyForUpdate("MODULE_NOTIFICATIONS")).thenReturn(Optional.of(
                FeatureFlag.builder().featureKey("MODULE_NOTIFICATIONS").enabled(true).build()));
        when(notifications.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification saved = invocation.getArgument(0); saved.setId(918273L); return saved;
        });
        service.createNotification(owner, "SYNTHETIC_PRIVATE_MESSAGE", "/events/918273");
    }
}

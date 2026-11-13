package com.college.events.service;

import com.college.events.model.Notification;
import com.college.events.model.User;
import com.college.events.repository.NotificationRepository;
import com.college.events.repository.FeatureFlagRepository;
import com.college.events.model.FeatureFlag;
import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceAfterCommitTest {
    @Mock private NotificationRepository repository;
    @Mock private FeatureFlagRepository featureFlags;
    @InjectMocks private NotificationService service;

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void realtimeDeliveryIsRegisteredAfterTheInboxWrite() {
        User recipient = User.builder().id(41L).email("student@example.test").build();
        Notification saved = Notification.builder().id(19L).user(recipient).message("Access updated")
                .actionUrl("/notifications").isRead(false).build();
        when(repository.save(any(Notification.class))).thenReturn(saved);
        when(featureFlags.findByFeatureKeyForUpdate("MODULE_NOTIFICATIONS"))
                .thenReturn(Optional.of(FeatureFlag.builder().featureKey("MODULE_NOTIFICATIONS").enabled(true).build()));
        when(featureFlags.findByFeatureKey("MODULE_NOTIFICATIONS"))
                .thenReturn(Optional.of(FeatureFlag.builder().featureKey("MODULE_NOTIFICATIONS").enabled(true).build()));
        TransactionSynchronizationManager.initSynchronization();

        service.createNotification(recipient, "Access updated", "//external.example/redirect");

        List<TransactionSynchronization> callbacks = TransactionSynchronizationManager.getSynchronizations();
        assertEquals(1, callbacks.size());
        ArgumentCaptor<Notification> savedInput = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(savedInput.capture());
        assertEquals("/notifications", savedInput.getValue().getActionUrl());
        assertDoesNotThrow(() -> callbacks.get(0).afterCommit());
    }

    @Test
    void acknowledgementUsesScopedAtomicRepositoryUpdate() {
        User recipient = User.builder().id(41L).build();
        when(featureFlags.findByFeatureKeyForUpdate("MODULE_NOTIFICATIONS"))
                .thenReturn(Optional.of(FeatureFlag.builder().featureKey("MODULE_NOTIFICATIONS").enabled(true).build()));

        service.acknowledge(19L, recipient);

        verify(repository).acknowledgeOwned(19L, 41L);
    }
}

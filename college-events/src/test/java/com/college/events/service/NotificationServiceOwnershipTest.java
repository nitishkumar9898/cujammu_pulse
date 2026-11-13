package com.college.events.service;

import com.college.events.model.FeatureFlag;
import com.college.events.model.Notification;
import com.college.events.model.User;
import com.college.events.repository.FeatureFlagRepository;
import com.college.events.repository.NotificationRepository;
import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class NotificationServiceOwnershipTest {
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final FeatureFlagRepository flags = mock(FeatureFlagRepository.class);
    private final NotificationService service = new NotificationService(notifications, flags);

    @Test
    void markReadUsesSingleOwnerScopedMutationWithoutLoadingForeignRows() {
        User current = User.builder().id(8L).build();
        when(flags.findByFeatureKeyForUpdate("MODULE_NOTIFICATIONS"))
                .thenReturn(Optional.of(FeatureFlag.builder().featureKey("MODULE_NOTIFICATIONS").enabled(true).build()));

        service.markAsRead(31L, current);

        verify(notifications).markReadOwned(31L, 8L);
        verify(notifications, never()).findById(31L);
    }

    @Test
    void markAllReadUsesOneOwnerScopedBulkMutationWithoutLoadingUnboundedHistory() {
        User current = User.builder().id(8L).build();
        when(flags.findByFeatureKeyForUpdate("MODULE_NOTIFICATIONS"))
                .thenReturn(Optional.of(FeatureFlag.builder().featureKey("MODULE_NOTIFICATIONS").enabled(true).build()));

        service.markAllAsRead(current);

        verify(notifications).markAllUnreadOwned(8L);
        verify(notifications, never()).findByUserOrderByCreatedAtDesc(current);
        verify(notifications, never()).saveAll(anyIterable());
    }

    @Test
    void cursorHistoryNeverAcceptsOrQueriesAnotherRecipient() {
        User current = User.builder().id(8L).build();
        Notification notification = Notification.builder().id(41L).user(current).message("Synthetic").build();
        when(notifications.findByUserIdAndIdLessThanOrderByIdDesc(8L, 42L, PageRequest.of(0, 20)))
                .thenReturn(new SliceImpl<>(List.of(notification), PageRequest.of(0, 20), false));

        var result = service.getPage(current, 42L);

        assertEquals(41L, result.getContent().get(0).getId());
        verify(notifications).findByUserIdAndIdLessThanOrderByIdDesc(8L, 42L, PageRequest.of(0, 20));
        assertThrows(IllegalArgumentException.class, () -> service.getPage(current, 0L));
    }

    @Test
    void controlRevisionDisablesStreamsOnlyAfterPersistedChange() {
        NotificationService monitored = spy(new NotificationService(notifications, flags));
        FeatureFlag disabled = FeatureFlag.builder().featureKey("MODULE_NOTIFICATIONS")
                .enabled(false).revision(7L).build();
        when(flags.findByFeatureKey("MODULE_NOTIFICATIONS")).thenReturn(Optional.of(disabled));

        monitored.reconcileNotificationControl();

        verify(monitored).closeAllStreams();
    }
}

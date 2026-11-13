package com.college.events.service;

import com.college.events.model.FeatureFlag;
import com.college.events.model.Notification;
import com.college.events.model.NotificationDeliveryIntent;
import com.college.events.repository.FeatureFlagRepository;
import com.college.events.repository.NotificationDeliveryIntentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class NotificationIntentReconciliationServiceTest {
    private final NotificationDeliveryIntentRepository intents = mock(NotificationDeliveryIntentRepository.class);
    private final FeatureFlagRepository flags = mock(FeatureFlagRepository.class);
    private final NotificationIntentReconciliationService service = new NotificationIntentReconciliationService(intents, flags);
    private final LocalDateTime now = LocalDateTime.of(2030, 5, 1, 10, 0);

    @Test
    void disabledModuleDoesNotClaimQueuedIntents() {
        when(flags.findByFeatureKey("MODULE_NOTIFICATIONS")).thenReturn(Optional.empty());

        assertEquals(0, service.reconcileDue("worker-a", 10, now));
        verify(intents, never()).findDueForClaim(any(), any());
    }

    @Test
    void claimedInboxIntentReconcilesWithoutClaimingExternalDelivery() {
        when(flags.findByFeatureKey("MODULE_NOTIFICATIONS"))
                .thenReturn(Optional.of(FeatureFlag.builder().featureKey("MODULE_NOTIFICATIONS").enabled(true).build()));
        var notification = Notification.builder().id(8L).message("Synthetic update").build();
        var intent = NotificationDeliveryIntent.builder().id(31L).notification(notification)
                .channel("IN_APP").dedupeKey("IN_APP:8").status("INBOX_CREATED").build();
        when(intents.findDueForClaim(now, PageRequest.of(0, 10))).thenReturn(List.of(intent));
        when(intents.findById(31L)).thenReturn(Optional.of(intent));

        assertEquals(1, service.reconcileDue("worker-a", 10, now));
        assertEquals("RECONCILED", intent.getStatus());
        assertNull(intent.getLeaseOwner());
        assertEquals(1, intent.getAttempts());
        verify(intents, atLeast(2)).save(intent);
    }

    @Test
    void failedClaimsBackoffAndThenDeadLetterAtBoundedAttemptCount() {
        var intent = NotificationDeliveryIntent.builder().id(32L).status("CLAIMED")
                .leaseOwner("worker-a").leaseUntil(now.plusSeconds(60)).attempts(5).build();
        when(intents.findById(32L)).thenReturn(Optional.of(intent));

        service.failClaim("worker-a", 32L, now, "synthetic failure");

        assertEquals("DEAD_LETTER", intent.getStatus());
        assertEquals("synthetic failure", intent.getLastError());
        verify(intents).save(intent);
    }
}

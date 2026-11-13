package com.college.events.config;

import com.college.events.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Per-instance safety net for notification stream invalidation. A deployment
 * may later replace this trigger with a reviewed pub/sub adapter, but every
 * instance must retain the database check as the authoritative fallback.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationControlReconciliationScheduler {
    private final NotificationService notifications;

    @Scheduled(initialDelayString = "${app.notification.control-reconciliation-initial-delay-ms:15000}",
            fixedDelayString = "${app.notification.control-reconciliation-delay-ms:30000}")
    public void reconcile() {
        try {
            notifications.reconcileNotificationControl();
        } catch (RuntimeException failure) {
            log.warn("Notification control reconciliation failed; streams remain governed by request checks", failure);
        }
    }
}

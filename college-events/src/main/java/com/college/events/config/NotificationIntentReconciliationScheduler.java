package com.college.events.config;

import com.college.events.service.NotificationIntentReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Bounded process-local trigger; durable intent state remains the source of truth. */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationIntentReconciliationScheduler {
    private final NotificationIntentReconciliationService service;

    @Scheduled(initialDelayString = "${app.notification.intent-reconciliation-initial-delay-ms:10000}",
            fixedDelayString = "${app.notification.intent-reconciliation-delay-ms:30000}")
    public void reconcile() {
        try {
            int processed = service.reconcileDue();
            if (processed > 0) log.info("Notification intent reconciliation processed {} inbox intents", processed);
        } catch (Exception failure) {
            log.error("Notification intent reconciliation failed; durable state remains for retry", failure);
        }
    }
}

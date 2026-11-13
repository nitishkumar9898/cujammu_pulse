package com.college.events.service;

import com.college.events.model.FeatureFlag;
import com.college.events.model.NotificationDeliveryIntent;
import com.college.events.repository.FeatureFlagRepository;
import com.college.events.repository.NotificationDeliveryIntentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Reconciles the durable in-app handoff without claiming provider delivery.
 * Claims expire so a crashed worker cannot strand an intent permanently.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationIntentReconciliationService {
    static final int MAX_ATTEMPTS = 5;
    private static final int DEFAULT_BATCH = 50;
    private static final int LEASE_SECONDS = 90;

    private final NotificationDeliveryIntentRepository intents;
    private final FeatureFlagRepository featureFlags;
    /** Optional so isolated service tests do not need an actuator registry. */
    @Autowired(required = false) private MeterRegistry meterRegistry;

    @Transactional
    public int reconcileDue() {
        return reconcileDue("notification-worker-" + UUID.randomUUID(), DEFAULT_BATCH, LocalDateTime.now());
    }

    /** Runs one bounded claim/process pass and returns processed intent count. */
    @Transactional
    public int reconcileDue(String workerId, int limit, LocalDateTime now) {
        if (!notificationsEnabled()) return 0;
        if (workerId == null || workerId.isBlank() || workerId.length() > 80) {
            throw new IllegalArgumentException("Invalid notification worker identifier");
        }
        if (limit < 1 || limit > 200) throw new IllegalArgumentException("Notification batch limit is out of range");
        LocalDateTime claimedAt = now == null ? LocalDateTime.now() : now;
        List<Long> ids = claim(workerId, limit, claimedAt);
        int processed = 0;
        for (Long id : ids) {
            if (reconcileClaim(workerId, id, claimedAt)) processed++;
        }
        metric("pass", processed);
        return processed;
    }

    @Transactional
    protected List<Long> claim(String workerId, int limit, LocalDateTime now) {
        List<NotificationDeliveryIntent> due = intents.findDueForClaim(now, PageRequest.of(0, limit));
        LocalDateTime leaseUntil = now.plusSeconds(LEASE_SECONDS);
        for (NotificationDeliveryIntent intent : due) {
            intent.setStatus("CLAIMED");
            intent.setLeaseOwner(workerId);
            intent.setLeaseUntil(leaseUntil);
            intent.setAttempts(intent.getAttempts() + 1);
            intents.save(intent);
        }
        return due.stream().map(NotificationDeliveryIntent::getId).toList();
    }

    @Transactional
    protected boolean reconcileClaim(String workerId, Long id, LocalDateTime now) {
        NotificationDeliveryIntent intent = intents.findById(id).orElse(null);
        if (intent == null || !"CLAIMED".equals(intent.getStatus()) || !workerId.equals(intent.getLeaseOwner())) {
            return false;
        }
        if (intent.getLeaseUntil() == null || intent.getLeaseUntil().isBefore(now)) return false;
        if (intent.getNotification() != null && intent.getNotification().getId() != null) {
            intent.setStatus("RECONCILED");
            intent.setReconciledAt(now);
            intent.setLeaseOwner(null);
            intent.setLeaseUntil(null);
            intent.setNextAttemptAt(null);
            intent.setLastError(null);
            intents.save(intent);
            metric("reconciled", 1);
            return true;
        }
        failClaim(intent, now, "Inbox notification reference is unavailable");
        return false;
    }

    @Transactional
    protected void failClaim(String workerId, Long id, LocalDateTime now, String reason) {
        NotificationDeliveryIntent intent = intents.findById(id).orElse(null);
        if (intent == null || !"CLAIMED".equals(intent.getStatus()) || !workerId.equals(intent.getLeaseOwner())) return;
        failClaim(intent, now, reason);
    }

    private void failClaim(NotificationDeliveryIntent intent, LocalDateTime now, String reason) {
        String bounded = reason == null ? "Reconciliation failed" : reason.trim();
        if (bounded.length() > 240) bounded = bounded.substring(0, 240);
        intent.setLastError(bounded);
        intent.setLeaseOwner(null);
        intent.setLeaseUntil(null);
        if (intent.getAttempts() >= MAX_ATTEMPTS) {
            intent.setStatus("DEAD_LETTER");
            intent.setNextAttemptAt(null);
            metric("dead_letter", 1);
        } else {
            intent.setStatus("RETRY_WAIT");
            long delaySeconds = Math.min(3600L, 60L * (1L << Math.min(intent.getAttempts() - 1, 5)));
            intent.setNextAttemptAt(now.plusSeconds(delaySeconds));
            metric("retry_wait", 1);
        }
        intents.save(intent);
    }

    private void metric(String outcome, int amount) {
        if (meterRegistry != null && amount > 0) {
            meterRegistry.counter("cujammu.notifications.intent.reconciliation", "outcome", outcome).increment(amount);
        }
    }

    private boolean notificationsEnabled() {
        return featureFlags.findByFeatureKey("MODULE_NOTIFICATIONS")
                .map(FeatureFlag::isEnabled).orElse(false);
    }
}

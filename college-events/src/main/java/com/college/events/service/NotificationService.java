package com.college.events.service;

import com.college.events.model.Notification;
import com.college.events.model.NotificationDeliveryIntent;
import com.college.events.model.User;
import com.college.events.repository.NotificationRepository;
import com.college.events.repository.FeatureFlagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Objects;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final FeatureFlagRepository featureFlagRepository;
    /** Optional for source-compatible isolated unit tests; injected in production. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.college.events.repository.NotificationDeliveryIntentRepository deliveryIntents;
    /** Fail closed for live subscriptions when isolated tests omit account access. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.college.events.repository.UserRepository streamAccounts;
    private final Map<Long, CopyOnWriteArrayList<StreamSubscription>> emittersByUser = new ConcurrentHashMap<>();
    static final int MAX_STREAMS_PER_USER = 3;
    static final int MAX_STREAMS_TOTAL = 256;
    static final long STREAM_TIMEOUT_MILLIS = 60_000L;
    /** Last persisted control-plane revision observed by this application instance. */
    private volatile long observedNotificationRevision = -1L;
    private record StreamSubscription(SseEmitter emitter, AccountSnapshot account) { }
    private static final class AccountSnapshot {
        private final Long id;
        private final com.college.events.model.Role role;
        private final String email;
        private final String password;
        private AccountSnapshot(User user) {
            id = user.getId(); role = user.getRole(); email = user.getEmail(); password = user.getPassword();
        }
        private boolean matches(com.college.events.repository.UserRepository.StreamAccountState account) {
            // Java exact equality also defeats case-insensitive legacy SQL collations.
            return account.getEnabled() && role == account.getRole() && Objects.equals(email, account.getEmail())
                    && Objects.equals(password, account.getPassword());
        }
    }
    public static final class StreamCapacityException extends RuntimeException { }

    @Transactional
    public Notification createNotification(User user, String message, String actionUrl) {
        if (!notificationsEnabledForUpdate()) return null;
        String boundedMessage = message == null ? "Campus update" : message;
        if (boundedMessage.length() > 240) boundedMessage = boundedMessage.substring(0, 240);
        String safeActionUrl = actionUrl != null && actionUrl.startsWith("/") && !actionUrl.startsWith("//")
                && !actionUrl.contains("\\") && actionUrl.indexOf('\r') < 0 && actionUrl.indexOf('\n') < 0
                ? actionUrl : "/notifications";
        Notification notification = Notification.builder()
            .user(user)
            .message(boundedMessage)
            .actionUrl(safeActionUrl)
            .isRead(false)
            .build();
        Notification saved = notificationRepository.save(notification);
        recordInAppIntent(saved);
        publishAfterCommit(saved);
        log.debug("Created inbox notification id={} for userId={}", saved.getId(), user.getId());
        return saved;
    }

    /**
     * The university inbox is the durable source of truth. This row is committed
     * with it so a later provider/realtime worker can reconcile without guessing
     * whether an alert existed. It intentionally does not claim push delivery.
     */
    private void recordInAppIntent(Notification saved) {
        if (deliveryIntents == null || saved.getId() == null) return;
        deliveryIntents.findByNotificationIdAndChannel(saved.getId(), "IN_APP")
                .orElseGet(() -> deliveryIntents.save(NotificationDeliveryIntent.builder()
                        .notification(saved).channel("IN_APP")
                        .dedupeKey("IN_APP:" + saved.getId())
                        .status("INBOX_CREATED").attempts(1)
                        .nextAttemptAt(null).build()));
    }

    @Transactional
    public void createNotifications(List<User> users, String message, String actionUrl) {
        users.forEach(user -> createNotification(user, message, actionUrl));
    }

    public List<Notification> getRecentUnread(User user) {
        return notificationRepository.findTop5ByUserAndIsReadFalseOrderByCreatedAtDesc(user);
    }

    public List<Notification> getRecent(User user) {
        return notificationRepository.findTop20ByUserOrderByCreatedAtDesc(user);
    }

    /** ID cursor is stable across concurrent notification inserts and remains recipient-scoped. */
    public Slice<Notification> getPage(User user, Long beforeId) {
        if (beforeId != null && beforeId < 1) throw new IllegalArgumentException("Invalid notification cursor");
        var request = PageRequest.of(0, 20);
        return beforeId == null
                ? notificationRepository.findByUserIdOrderByIdDesc(user.getId(), request)
                : notificationRepository.findByUserIdAndIdLessThanOrderByIdDesc(user.getId(), beforeId, request);
    }

    public long getUnreadCount(User user) {
        return notificationRepository.countByUserAndIsReadFalse(user);
    }

    @Transactional
    public void markAsRead(Long id, User user) {
        if (!notificationsEnabledForUpdate()) return;
        notificationRepository.markReadOwned(id, user.getId());
    }

    @Transactional
    public void markAllAsRead(User user) {
        if (!notificationsEnabledForUpdate()) return;
        notificationRepository.markAllUnreadOwned(user.getId());
    }

    @Transactional
    public void acknowledge(long id, User user) {
        if (!notificationsEnabledForUpdate()) return;
        notificationRepository.acknowledgeOwned(id, user.getId());
    }

    public SseEmitter subscribe(User user) {
        SseEmitter emitter = newEmitter();
        if (user == null || user.getId() == null || !notificationsEnabled()
                || !accountStillValid(new AccountSnapshot(user))) {
            emitter.complete();
            return emitter;
        }
        registerEmitter(user, emitter);

        emitter.onCompletion(() -> removeEmitter(user.getId(), emitter));
        emitter.onTimeout(() -> removeEmitter(user.getId(), emitter));
        emitter.onError(error -> removeEmitter(user.getId(), emitter));

        try {
            emitter.send(SseEmitter.event().name("ready").data(Map.of("status", "connected")));
        } catch (IOException | IllegalStateException exception) {
            removeEmitter(user.getId(), emitter);
            log.debug("Failed to initialize notification stream for userId={}", user.getId(), exception);
        }

        return emitter;
    }

    SseEmitter newEmitter() { return new SseEmitter(STREAM_TIMEOUT_MILLIS); }

    private synchronized void registerEmitter(User user, SseEmitter emitter) {
        int total = emittersByUser.values().stream().mapToInt(List::size).sum();
        var existing = emittersByUser.get(user.getId());
        if (total >= MAX_STREAMS_TOTAL || (existing != null && existing.size() >= MAX_STREAMS_PER_USER)) {
            throw new StreamCapacityException();
        }
        emittersByUser.computeIfAbsent(user.getId(), ignored -> new CopyOnWriteArrayList<>())
                .add(new StreamSubscription(emitter, new AccountSnapshot(user)));
    }

    private boolean accountStillValid(AccountSnapshot snapshot) {
        try {
            return streamAccounts != null && streamAccounts.findCurrentStreamAccount(snapshot.id)
                    .filter(snapshot::matches).isPresent();
        } catch (RuntimeException unavailable) {
            log.warn("Notification stream account validation unavailable");
            return false;
        }
    }

    private void publish(Notification notification) {
        if (!notificationsEnabled()) return;
        List<StreamSubscription> emitters = emittersByUser.get(notification.getUser().getId());
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        // A connection may outlive logout before its timeout. Never send private
        // content or identifiers: clients must re-fetch the authorized inbox.
        for (StreamSubscription subscription : emitters) {
            if (!accountStillValid(subscription.account())
                    || !sendNotificationEvent(subscription.emitter(), Map.of("status", "invalidate"))) {
                removeEmitter(notification.getUser().getId(), subscription.emitter());
                subscription.emitter().complete();
            }
        }
    }

    /** Close in-process live streams immediately when the admin disables this module. */
    public synchronized void closeAllStreams() {
        var all = emittersByUser.values().stream().flatMap(List::stream).toList();
        emittersByUser.clear();
        all.forEach(subscription -> subscription.emitter().complete());
    }

    /**
     * Bounded cross-instance invalidation fallback. Each instance polls the
     * authoritative feature row; Redis/cache is deliberately not used as the
     * permission source. Disabled notifications close local SSE streams, while
     * re-enablement requires clients to reconnect and re-fetch the manifest.
     */
    @Transactional(readOnly = true)
    public void reconcileNotificationControl() {
        featureFlagRepository.findByFeatureKey("MODULE_NOTIFICATIONS").ifPresent(flag -> {
            long revision = flag.getRevision();
            boolean changed = revision != observedNotificationRevision;
            observedNotificationRevision = revision;
            if (changed && !flag.isEnabled()) closeAllStreams();
        });
    }

    private boolean notificationsEnabled() {
        return featureFlagRepository.findByFeatureKey("MODULE_NOTIFICATIONS")
                .map(flag -> flag.isEnabled()).orElse(false);
    }

    /** The shared feature-row lock linearizes inbox mutations against administrator OFF. */
    private boolean notificationsEnabledForUpdate() {
        return featureFlagRepository.findByFeatureKeyForUpdate("MODULE_NOTIFICATIONS")
                .map(flag -> flag.isEnabled()).orElse(false);
    }

    /** Inbox row commits with its domain transaction; realtime delivery is best-effort after commit. */
    private void publishAfterCommit(Notification notification) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publish(notification);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { publish(notification); }
        });
    }

    private boolean sendNotificationEvent(SseEmitter emitter, Map<String, Object> payload) {
        try {
            emitter.send(SseEmitter.event().name("notification").data(payload));
            return true;
        } catch (IOException | IllegalStateException exception) {
            log.debug("Removing stale notification emitter", exception);
            return false;
        }
    }

    private synchronized void removeEmitter(Long userId, SseEmitter emitter) {
        emittersByUser.computeIfPresent(userId, (ignored, emitters) -> {
            emitters.removeIf(subscription -> subscription.emitter() == emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }
}

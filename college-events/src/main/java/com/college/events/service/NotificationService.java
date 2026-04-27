package com.college.events.service;

import com.college.events.model.Notification;
import com.college.events.model.User;
import com.college.events.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
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
    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();
    private static final DateTimeFormatter NOTIFICATION_TIME_FORMAT =
        DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    @Transactional
    public Notification createNotification(User user, String message, String actionUrl) {
        Notification notification = Notification.builder()
            .user(user)
            .message(message)
            .actionUrl(actionUrl)
            .isRead(false)
            .build();
        Notification saved = notificationRepository.save(notification);
        publish(saved);
        log.info("Created notification for user {}: {}", user.getEmail(), message);
        return saved;
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

    public long getUnreadCount(User user) {
        return notificationRepository.countByUserAndIsReadFalse(user);
    }

    @Transactional
    public void markAsRead(Long id, User user) {
        notificationRepository.findById(id).ifPresent(notification -> {
            if (notification.getUser().getId().equals(user.getId())) {
                notification.setRead(true);
                notificationRepository.save(notification);
            }
        });
    }

    @Transactional
    public void markAllAsRead(User user) {
        List<Notification> unread = notificationRepository.findByUserOrderByCreatedAtDesc(user).stream()
            .filter(n -> !n.isRead()).toList();
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
    }

    public SseEmitter subscribe(User user) {
        SseEmitter emitter = new SseEmitter(0L);
        emittersByUser.computeIfAbsent(user.getId(), ignored -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(user.getId(), emitter));
        emitter.onTimeout(() -> removeEmitter(user.getId(), emitter));
        emitter.onError(error -> removeEmitter(user.getId(), emitter));

        try {
            emitter.send(SseEmitter.event().name("ready").data(Map.of("status", "connected")));
        } catch (IOException exception) {
            removeEmitter(user.getId(), emitter);
            log.debug("Failed to initialize notification stream for {}", user.getEmail(), exception);
        }

        return emitter;
    }

    private void publish(Notification notification) {
        List<SseEmitter> emitters = emittersByUser.get(notification.getUser().getId());
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        Map<String, Object> payload = Map.of(
            "id", notification.getId(),
            "message", notification.getMessage(),
            "actionUrl", notification.getActionUrl() != null ? notification.getActionUrl() : "/notifications",
            "createdAt", notification.getCreatedAt() != null
                ? notification.getCreatedAt().format(NOTIFICATION_TIME_FORMAT)
                : ""
        );

        emitters.removeIf(emitter -> !sendNotificationEvent(emitter, payload));
    }

    private boolean sendNotificationEvent(SseEmitter emitter, Map<String, Object> payload) {
        try {
            emitter.send(SseEmitter.event().name("notification").data(payload));
            return true;
        } catch (IOException exception) {
            log.debug("Removing stale notification emitter", exception);
            return false;
        }
    }

    private void removeEmitter(Long userId, SseEmitter emitter) {
        emittersByUser.computeIfPresent(userId, (ignored, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }
}

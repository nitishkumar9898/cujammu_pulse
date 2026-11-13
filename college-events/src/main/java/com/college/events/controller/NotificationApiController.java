package com.college.events.controller;

import com.college.events.model.Notification;
import com.college.events.model.User;
import com.college.events.service.NotificationService;
import com.college.events.security.DomainAccess;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** User-scoped allowlist API; notification entities and their recipient relation are never serialized. */
@RestController
@RequestMapping("/api/v1/cujammu/notifications")
@RequiredArgsConstructor
public class NotificationApiController {
    private final DomainAccess access;
    private final NotificationService notifications;

    @GetMapping
    public ResponseEntity<NotificationList> mine(@org.springframework.web.bind.annotation.RequestParam(required = false) Long beforeId) {
        User user = access.currentUser();
        var result = notifications.getPage(user, beforeId);
        List<NotificationView> items = result.getContent().stream()
                .map(NotificationApiController::toView).toList();
        Long nextBeforeId = result.hasNext() && !items.isEmpty() ? items.get(items.size() - 1).id() : null;
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new NotificationList(nextBeforeId, items));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable long id) {
        notifications.markAsRead(id, access.currentUser());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/{id}/acknowledge")
    public ResponseEntity<Void> acknowledge(@PathVariable long id) {
        notifications.acknowledge(id, access.currentUser());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private static NotificationView toView(Notification notification) {
        return new NotificationView(notification.getId(), notification.getMessage(),
                notification.isRead(), notification.isAcknowledged(),
                notification.getCreatedAt() == null ? null : notification.getCreatedAt().toString());
    }

    public record NotificationList(Long nextBeforeId, List<NotificationView> items) {}
    public record NotificationView(long id, String message, boolean read, boolean acknowledged, String createdAt) {}
}

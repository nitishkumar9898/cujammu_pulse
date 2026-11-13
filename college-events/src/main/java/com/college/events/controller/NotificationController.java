package com.college.events.controller;

import com.college.events.model.Notification;
import com.college.events.model.User;
import com.college.events.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Controller
@RequestMapping
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/notifications")
    public String notifications(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("notifications", user != null ? notificationService.getRecent(user) : java.util.List.of());
        return "notifications/index";
    }

    @PostMapping("/api/notifications/read-all")
    public String markAllAsRead(@AuthenticationPrincipal User user) {
        if (user != null) {
            notificationService.markAllAsRead(user);
        }
        return "redirect:/notifications";
    }

    @PostMapping("/api/notifications/{id}/open")
    public String openNotification(@PathVariable Long id,
                                   @AuthenticationPrincipal User user) {
        if (user != null) {
            notificationService.markAsRead(id, user);
        }

        Notification target = user != null
            ? notificationService.getRecent(user).stream().filter(notification -> notification.getId().equals(id)).findFirst().orElse(null)
            : null;
        return "redirect:" + safeDestination(target != null ? target.getActionUrl() : null);
    }

    /** Only known read-only landing pages may be reached by persisted legacy links.
     * No query, encoded separator, authority, dot segment or response header can
     * turn an inbox action into an external redirect or another mutation endpoint.
     */
    private static String safeDestination(String actionUrl) {
        if (actionUrl != null && actionUrl.matches(
                "/(?:notifications|app/?|venues|hostel|student/my-tickets|"
                + "(?:student/)?events/[1-9][0-9]*(?:/attendance)?|"
                + "organizer/events/[1-9][0-9]*(?:/registrations)?)")) {
            return actionUrl;
        }
        return "/notifications";
    }

    @GetMapping("/api/notifications/stream")
    public SseEmitter stream(@AuthenticationPrincipal User user) {
        return notificationService.subscribe(user);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(NotificationService.StreamCapacityException.class)
    public org.springframework.http.ResponseEntity<Void> streamCapacityReached() {
        return org.springframework.http.ResponseEntity.status(429).header("Retry-After", "60")
                .cacheControl(org.springframework.http.CacheControl.noStore()).build();
    }
}

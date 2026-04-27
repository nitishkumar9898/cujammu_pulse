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

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.servlet.http.HttpServletRequest;

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

    @GetMapping("/api/notifications/read-all")
    public String markAllAsRead(@AuthenticationPrincipal User user, HttpServletRequest request) {
        if (user != null) {
            notificationService.markAllAsRead(user);
        }
        String referer = request.getHeader("Referer");
        return "redirect:" + (referer != null && !referer.isBlank() ? referer : "/notifications");
    }

    @GetMapping("/api/notifications/{id}/open")
    public String openNotification(@PathVariable Long id,
                                   @AuthenticationPrincipal User user,
                                   HttpServletRequest request) {
        if (user != null) {
            notificationService.markAsRead(id, user);
        }

        String fallback = request.getHeader("Referer");
        Notification target = user != null
            ? notificationService.getRecent(user).stream().filter(notification -> notification.getId().equals(id)).findFirst().orElse(null)
            : null;
        String actionUrl = target != null && target.getActionUrl() != null && !target.getActionUrl().isBlank()
            ? target.getActionUrl()
            : (fallback != null && !fallback.isBlank() ? fallback : "/notifications");
        return "redirect:" + actionUrl;
    }

    @GetMapping("/api/notifications/stream")
    public SseEmitter stream(@AuthenticationPrincipal User user) {
        return notificationService.subscribe(user);
    }
}

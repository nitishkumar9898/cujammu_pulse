package com.college.events.controller;

import com.college.events.model.Notification;
import com.college.events.model.User;
import com.college.events.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Collections;
import java.util.List;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAdvice {

    private final NotificationService notificationService;

    @ModelAttribute("user")
    public User currentUser(@AuthenticationPrincipal User user) {
        return user;
    }

    @ModelAttribute("unreadNotificationsCount")
    public long unreadNotificationsCount(@AuthenticationPrincipal User user) {
        if (user == null) return 0;
        return notificationService.getUnreadCount(user);
    }

    @ModelAttribute("recentNotifications")
    public List<Notification> recentNotifications(@AuthenticationPrincipal User user) {
        if (user == null) return Collections.emptyList();
        return notificationService.getRecentUnread(user);
    }
}

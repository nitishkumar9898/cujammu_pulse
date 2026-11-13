package com.college.events.controller;

import com.college.events.model.Notification;
import com.college.events.model.User;
import com.college.events.service.NotificationService;
import com.college.events.service.FeatureFlagService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAdvice {

    private final NotificationService notificationService;
    private final FeatureFlagService featureFlagService;

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

    @ModelAttribute("featureFlags")
    public Map<String, Boolean> featureFlags() {
        return featureFlagService.getAllFlagStatuses();
    }
}

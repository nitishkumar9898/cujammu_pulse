package com.college.events.config;

import com.college.events.service.FeatureFlagService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class FeatureFlagInterceptor implements HandlerInterceptor {

    private final FeatureFlagService featureFlagService;

    private static final Map<String, String> ROUTE_FLAGS = new LinkedHashMap<>();

    static {
        ROUTE_FLAGS.put("/api/v1/cujammu/events/calendar/mine", "MODULE_CALENDAR");
        ROUTE_FLAGS.put("/api/v1/cujammu/events/calendar/clashes", "ACTION_CALENDAR_CLASH_CHECK");
        ROUTE_FLAGS.put("/api/v1/cujammu/readiness", "MODULE_READINESS");
        ROUTE_FLAGS.put("/api/v1/cujammu/events/", "MODULE_EVENTS");
        ROUTE_FLAGS.put("/api/v1/cujammu/insights", "MODULE_INSIGHTS");
        ROUTE_FLAGS.put("/api/v1/cujammu/notifications", "MODULE_NOTIFICATIONS");
        ROUTE_FLAGS.put("/api/notifications", "MODULE_NOTIFICATIONS");
        ROUTE_FLAGS.put("/notifications", "MODULE_NOTIFICATIONS");
        ROUTE_FLAGS.put("/api/v1/cujammu/facilities", "MODULE_VENUES");
        ROUTE_FLAGS.put("/api/v1/cujammu/library", "MODULE_LIBRARY_TURNSTILE");
        ROUTE_FLAGS.put("/api/v1/cujammu/hostels", "MODULE_HOSTEL_GATEPASS");
        ROUTE_FLAGS.put("/api/v1/cujammu/exams", "MODULE_COE_EXAMS");
        ROUTE_FLAGS.put("/api/v1/cujammu/chat", "MODULE_COMMUNITY_CHAT");
        ROUTE_FLAGS.put("/api/v1/cujammu/teams", "MODULE_TEAMS");
        ROUTE_FLAGS.put("/api/v1/cujammu/volunteers", "MODULE_VOLUNTEERS");
        ROUTE_FLAGS.put("/api/v1/cujammu/lost-and-found", "MODULE_LOST_FOUND");
        ROUTE_FLAGS.put("/api/v1/cujammu/helpdesk", "MODULE_HELPDESK");
        ROUTE_FLAGS.put("/api/v1/cujammu/tournaments", "MODULE_TOURNAMENTS");
        ROUTE_FLAGS.put("/api/v1/cujammu/polls", "MODULE_POLLS");
        ROUTE_FLAGS.put("/api/v1/cujammu/sports", "MODULE_LIVE_SPORTS");
        ROUTE_FLAGS.put("/api/v1/cujammu/alumni", "MODULE_ALUMNI");
        ROUTE_FLAGS.put("/api/v1/cujammu/feed", "MODULE_SOCIAL_PULSE");
        ROUTE_FLAGS.put("/feed", "MODULE_SOCIAL_PULSE");
        ROUTE_FLAGS.put("/sports", "MODULE_LIVE_SPORTS");
        ROUTE_FLAGS.put("/alumni", "MODULE_ALUMNI");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }

        // Protected membership administration remains available for revocation while chat is OFF.
        // Every endpoint authorizes ADMIN inside ChatMembershipService; enabling grants checks chat there.
        if (path.startsWith("/api/v1/cujammu/chat/admin/")) return true;
        for (Map.Entry<String, String> route : ROUTE_FLAGS.entrySet()) {
            String prefix = route.getKey();
            boolean matches = path.equals(prefix) || path.startsWith(prefix.endsWith("/") ? prefix : prefix + "/");
            if (matches && !featureFlagService.isEnabled(route.getValue())) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return false;
            }
        }
        if (path.startsWith("/api/v1/cujammu/events/")) {
            String[] parts = path.split("/");
            if (parts.length > 6) {
                String flag = switch (parts[6]) {
                    case "teams" -> "MODULE_TEAMS";
                    case "judging" -> "MODULE_JUDGING";
                    case "volunteers" -> "MODULE_VOLUNTEERS";
                    case "polls" -> "MODULE_POLLS";
                    case "tournaments" -> "MODULE_TOURNAMENTS";
                    default -> null;
                };
                if (flag != null && !featureFlagService.isEnabled(flag)) {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return false;
                }
            }
        }
        return true;
    }
}

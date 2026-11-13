package com.college.events.controller;

import com.college.events.config.SecurityConfig;
import com.college.events.model.Notification;
import com.college.events.model.Role;
import com.college.events.model.User;
import com.college.events.repository.UserRepository;
import com.college.events.service.NotificationService;
import com.college.events.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(properties = "logging.file.name=target/test-logs/legacy-notifications.log")
@ContextConfiguration(classes = {SecurityConfig.class, NotificationController.class})
class LegacyNotificationSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean NotificationService notifications;
    @MockitoBean UserService userService;
    @MockitoBean UserRepository users;
    @MockitoBean PasswordEncoder encoder;
    private final User owner = User.builder().id(101L).name("Synthetic owner")
            .email("owner@example.invalid").password("synthetic-hash").role(Role.STUDENT).enabled(true).build();

    @BeforeEach void accountRemainsEnabled() {
        when(users.findById(owner.getId())).thenReturn(Optional.of(owner));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/notifications/read-all", "/api/notifications/7/open"})
    void getsCannotMutateEvenWithAuthenticatedSession(String path) throws Exception {
        mvc.perform(get(path).with(user(owner))).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(notifications);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/notifications/read-all", "/api/notifications/7/open"})
    void forgedPostsWithoutCsrfCannotMutate(String path) throws Exception {
        mvc.perform(post(path).with(user(owner)).header("Origin", "https://attacker.invalid"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(notifications);
    }

    @Test void markAllUsesOwnerAndIgnoresUntrustedReferer() throws Exception {
        mvc.perform(post("/api/notifications/read-all").with(user(owner)).with(csrf())
                        .header("Referer", "https://attacker.invalid/phish"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/notifications"));
        verify(notifications).markAllAsRead(owner);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://attacker.invalid", "//attacker.invalid", "/\\attacker.invalid", "/%2f%2fattacker.invalid",
            "/app/../api/notifications/read-all", "/app/?redirect=https://attacker.invalid", "/api/notifications/read-all",
            "/events/7\r\nLocation: https://attacker.invalid", "javascript:alert(1)", "/student/events/7/cancel"})
    void storedDangerousDestinationsFallBack(String destination) throws Exception {
        when(notifications.getRecent(owner)).thenReturn(List.of(Notification.builder().id(7L).actionUrl(destination).build()));
        mvc.perform(post("/api/notifications/7/open").with(user(owner)).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/notifications"));
        verify(notifications).markAsRead(7L, owner);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/app/", "/notifications", "/student/my-tickets", "/student/events/7",
            "/organizer/events/7/registrations", "/events/7/attendance", "/venues", "/hostel"})
    void supportedOwnedDestinationsRemainUsable(String destination) throws Exception {
        when(notifications.getRecent(owner)).thenReturn(List.of(Notification.builder().id(7L).actionUrl(destination).build()));
        mvc.perform(post("/api/notifications/7/open").with(user(owner)).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(destination));
    }

    @Test void unknownOrOtherRecipientsNoticeNeverProvidesRedirect() throws Exception {
        when(notifications.getRecent(owner)).thenReturn(List.of(Notification.builder().id(8L).actionUrl("/events/8").build()));
        mvc.perform(post("/api/notifications/7/open").with(user(owner)).with(csrf())
                        .header("Referer", "https://attacker.invalid"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/notifications"));
        verify(notifications).markAsRead(7L, owner);
    }

    @Test void anonymousPostsCannotMutateWithCsrfAlone() throws Exception {
        mvc.perform(post("/api/notifications/read-all").with(csrf())).andExpect(status().isUnauthorized());
        verifyNoInteractions(notifications);
    }

    @Test void exhaustedStreamCapacityReturnsBoundedRetryResponse() throws Exception {
        when(notifications.subscribe(owner)).thenThrow(new NotificationService.StreamCapacityException());
        mvc.perform(get("/api/notifications/stream").with(user(owner)))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "60"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test void inboxRendersEscapedContentAndCsrfProtectedForms() throws Exception {
        when(notifications.getRecent(owner)).thenReturn(List.of(Notification.builder().id(7L)
                .message("<script>alert('synthetic')</script>").actionUrl("https://attacker.invalid").build()));
        String html = mvc.perform(get("/notifications").with(user(owner)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var forms = Pattern.compile("<form\\b[^>]*method=\"post\"[^>]*>(.*?)</form>", Pattern.DOTALL).matcher(html);
        int count = 0;
        while (forms.find()) {
            count++;
            assertTrue(Pattern.compile("<input\\b[^>]*name=\"_csrf\"[^>]*value=\"[^\"]+\"").matcher(forms.group(1)).find(),
                    "Every mutation form must carry a nonempty CSRF token");
        }
        assertEquals(2, count);
        assertFalse(html.contains("<script"));
        assertTrue(html.contains("&lt;script&gt;"));
        assertFalse(html.contains("https://attacker.invalid"));
    }
}

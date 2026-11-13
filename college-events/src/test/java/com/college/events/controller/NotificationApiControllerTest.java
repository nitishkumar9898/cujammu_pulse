package com.college.events.controller;

import com.college.events.model.Notification;
import com.college.events.model.User;
import com.college.events.security.DomainAccess;
import com.college.events.service.NotificationService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationApiControllerTest {
    private final DomainAccess access = mock(DomainAccess.class);
    private final NotificationService service = mock(NotificationService.class);
    private final NotificationApiController controller = new NotificationApiController(access, service);

    @Test
    void inboxUsesCurrentUserAndReturnsAnAllowlistedNoStoreProjection() {
        User currentUser = User.builder().id(8L).name("Private account").email("person@example.test").build();
        Notification item = Notification.builder().id(31L).user(currentUser).message("Manual access was granted")
                .actionUrl("https://example.test/steal").isRead(false).acknowledged(false)
                .createdAt(LocalDateTime.parse("2026-10-02T10:30:00")).build();
        when(access.currentUser()).thenReturn(currentUser);
        when(service.getPage(currentUser, null)).thenReturn(new SliceImpl<>(List.of(item)));

        var response = controller.mine(null);

        assertTrue(response.getHeaders().getCacheControl().contains("no-store"));
        var result = response.getBody().items().get(0);
        assertEquals(31L, result.id());
        assertFalse(result.read());
        assertFalse(result.acknowledged());
        assertFalse(result.toString().contains("https://example.test/steal"));
        assertFalse(result.toString().contains("person@example.test"));
        assertNull(response.getBody().nextBeforeId());
        verify(service).getPage(currentUser, null);
    }

    @Test
    void paginatedInboxScopesCursorAndReturnsLastVisibleIdOnlyWhenMoreItemsExist() {
        User currentUser = User.builder().id(8L).build();
        var newer = Notification.builder().id(29L).message("Newer update").build();
        var older = Notification.builder().id(21L).message("Older update").build();
        when(access.currentUser()).thenReturn(currentUser);
        when(service.getPage(currentUser, 31L)).thenReturn(
                new SliceImpl<>(List.of(newer, older), PageRequest.of(0, 2), true));

        var response = controller.mine(31L);

        assertEquals(List.of(29L, 21L), response.getBody().items().stream()
                .map(NotificationApiController.NotificationView::id).toList());
        assertEquals(21L, response.getBody().nextBeforeId());
        assertTrue(response.getHeaders().getCacheControl().contains("no-store"));
        verify(service).getPage(currentUser, 31L);
    }

    @Test
    void markReadIsAlwaysScopedToTheAuthenticatedPrincipal() {
        User currentUser = User.builder().id(8L).build();
        when(access.currentUser()).thenReturn(currentUser);

        assertEquals(204, controller.markRead(31L).getStatusCode().value());

        verify(service).markAsRead(31L, currentUser);
    }

    @Test
    void acknowledgeIsAlwaysScopedToTheAuthenticatedPrincipal() {
        User currentUser = User.builder().id(8L).build();
        when(access.currentUser()).thenReturn(currentUser);

        assertEquals(204, controller.acknowledge(31L).getStatusCode().value());

        verify(service).acknowledge(31L, currentUser);
    }
}

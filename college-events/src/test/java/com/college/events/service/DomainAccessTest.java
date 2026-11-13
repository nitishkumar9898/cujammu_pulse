package com.college.events.service;

import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DomainAccessTest {
    UserRepository users = mock(UserRepository.class);
    EventRepository events = mock(EventRepository.class);
    DomainAccess access = new DomainAccess(users, events);
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void requestRoleCannotOverridePersistedRole() {
        User principal = user(1L, Role.ADMIN); authenticate(principal);
        when(users.findById(1L)).thenReturn(Optional.of(user(1L, Role.STUDENT)));
        assertThrows(AccessDeniedException.class, () -> access.requireRole(Role.ADMIN));
    }
    @Test void facultyCanManageOnlyOwnedEvents() {
        User faculty = user(1L, Role.FACULTY); authenticate(faculty);
        when(users.findById(1L)).thenReturn(Optional.of(faculty));
        Event own = Event.builder().id(10L).organizer(faculty).build();
        when(events.findById(10L)).thenReturn(Optional.of(own));
        when(events.findById(11L)).thenReturn(Optional.of(Event.builder().organizer(user(2L, Role.FACULTY)).build()));
        assertSame(own, access.requireEventManager(10L));
        assertThrows(AccessDeniedException.class, () -> access.requireEventManager(11L));
    }
    @Test void disabledAndAnonymousAccountsCannotReadPrivateData() {
        assertThrows(AccessDeniedException.class, access::currentUser);
        User student = user(1L, Role.STUDENT); authenticate(student); student.setEnabled(false);
        when(users.findById(1L)).thenReturn(Optional.of(student));
        assertThrows(AccessDeniedException.class, access::currentUser);
    }
    @Test void selfScopeCannotSelectAnotherAccount() {
        User student = user(1L, Role.STUDENT); authenticate(student);
        when(users.findById(1L)).thenReturn(Optional.of(student));
        assertThrows(AccessDeniedException.class, () -> access.requireSelf(2L));
        assertSame(student, access.requireSelf(1L));
    }
    private User user(Long id, Role role) { return User.builder().id(id).role(role).enabled(true).build(); }
    private void authenticate(User user) { SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities())); }
}

package com.college.events.security;

import com.college.events.model.*;
import com.college.events.repository.EventRepository;
import com.college.events.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.Objects;

/** Fresh account checks shared by domain commands; request-supplied actors confer no authority. */
@Component
@RequiredArgsConstructor
public class DomainAccess {
    private final UserRepository users;
    private final EventRepository events;

    public User currentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof User principal)) throw denied();
        return users.findById(principal.getId()).filter(User::isEnabled)
                .orElseThrow(DomainAccess::denied);
    }

    public User requireRole(Role... roles) {
        User user = currentUser();
        if (Arrays.stream(roles).noneMatch(role -> role == user.getRole())) throw denied();
        return user;
    }

    public User requireSelf(Long id) {
        User user = currentUser();
        if (!Objects.equals(id, user.getId())) throw denied();
        return user;
    }

    public Event requireEventManager(Long eventId) {
        User user = currentUser();
        Event event = events.findById(eventId).orElseThrow(DomainAccess::denied);
        if (user.getRole() != Role.ADMIN && !isEventOwner(user, event)) throw denied();
        return event;
    }

    public boolean isEventOwner(User user, Event event) {
        return (user.getRole() == Role.FACULTY || user.getRole() == Role.ORGANIZER)
                && event.getOrganizer() != null && Objects.equals(user.getId(), event.getOrganizer().getId());
    }

    public static AccessDeniedException denied() { return new AccessDeniedException("Access denied"); }
}

package com.college.events.service;

import com.college.events.dto.ReadinessTaskDtos;
import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReadinessTaskService {
    private final ReadinessTaskRepository tasks;
    private final EventRepository events;
    private final UserRepository users;
    private final DomainAccess access;
    private final FeatureFlagService flags;
    private final AuditService audit;
    private final NotificationService notifications;

    @Transactional(readOnly = true)
    public ReadinessTaskDtos.ListView list(long eventId) {
        flags.requireEnabledForRead("MODULE_READINESS");
        access.requireEventManager(eventId);
        List<ReadinessTask> items = tasks.findTop100ByEventIdOrderByDueAtAscIdAsc(eventId);
        return new ReadinessTaskDtos.ListView(eventId, items.stream().map(this::view).toList(), items.size() == 100);
    }

    @Transactional(readOnly = true)
    public List<ReadinessTaskDtos.Owner> owners(long eventId) {
        flags.requireEnabledForRead("MODULE_READINESS");
        Event event = access.requireEventManager(eventId);
        java.util.Map<Long, User> eligible = new java.util.TreeMap<>();
        if (event.getOrganizer() != null && event.getOrganizer().isEnabled())
            eligible.put(event.getOrganizer().getId(), event.getOrganizer());
        users.findTop50ByRoleAndEnabledTrueOrderByNameAsc(Role.ADMIN)
                .forEach(user -> eligible.put(user.getId(), user));
        return eligible.values().stream().map(user -> new ReadinessTaskDtos.Owner(
                user.getId(), user.getName(), user.getRole().name())).toList();
    }

    @Transactional
    public ReadinessTaskDtos.Item create(long eventId, ReadinessTaskDtos.Create command) {
        flags.requireEnabledForUpdate("MODULE_READINESS", "MODULE_EVENTS");
        access.requireEventManager(eventId);
        Event event = events.findForApproval(eventId).orElseThrow(DomainAccess::denied);
        requireMutableEvent(event);
        User actor = access.currentUser();
        User owner = assignableOwner(event, command.ownerUserId());
        String title = required(command.title(), 120);
        String description = required(command.description(), 500);
        var prior = tasks.findByEventIdAndRequestKey(eventId, command.requestKey());
        if (prior.isPresent()) {
            ReadinessTask task = prior.get();
            if (!sameCreate(task, title, description, command.category(), owner.getId(), command.dueAt()))
                throw new IllegalStateException("Request key was already used with different task details");
            return view(task);
        }
        ReadinessTask task = tasks.saveAndFlush(ReadinessTask.builder().event(event).owner(owner).createdBy(actor)
                .title(title).description(description).category(command.category()).dueAt(command.dueAt())
                .requestKey(command.requestKey()).status("OPEN").build());
        audit.logAction(actor.getUsername(), actor.getRole().name(), "READINESS_TASK_CREATED",
                "eventId=" + eventId + ";taskId=" + task.getId() + ";ownerUserId=" + owner.getId() + ";category=" + task.getCategory());
        notifyOwner(owner, actor, "A readiness task has been assigned to you. Sign in to review your authorized event tasks.");
        return view(task);
    }

    @Transactional
    public ReadinessTaskDtos.Item update(long eventId, long taskId, ReadinessTaskDtos.Update command) {
        flags.requireEnabledForUpdate("MODULE_READINESS", "MODULE_EVENTS");
        access.requireEventManager(eventId);
        Event event = events.findForApproval(eventId).orElseThrow(DomainAccess::denied);
        requireMutableEvent(event);
        ReadinessTask task = tasks.findByEventIdAndId(eventId, taskId).orElseThrow(DomainAccess::denied);
        if (task.getRevision() != command.expectedRevision())
            throw new IllegalStateException("Task changed; refresh before submitting an update");
        User actor = access.currentUser();
        User owner = assignableOwner(event, command.ownerUserId());
        boolean reassigned = !task.getOwner().getId().equals(owner.getId());
        if (!validTransition(task.getStatus(), command.status()))
            throw new IllegalStateException("Task status transition is not allowed");
        if ("DONE".equals(command.status()) && actor.getRole() != Role.ADMIN && !actor.getId().equals(task.getOwner().getId()))
            throw DomainAccess.denied();
        String completionNote = "DONE".equals(command.status()) ? required(command.completionNote(), 300) : null;
        task.setTitle(required(command.title(), 120));
        task.setDescription(required(command.description(), 500));
        task.setCategory(command.category());
        task.setOwner(owner);
        task.setDueAt(command.dueAt());
        task.setStatus(command.status());
        task.setCompletionNote(completionNote);
        tasks.flush();
        audit.logAction(actor.getUsername(), actor.getRole().name(), "READINESS_TASK_UPDATED",
                "eventId=" + eventId + ";taskId=" + taskId + ";revision=" + task.getRevision() + ";status=" + task.getStatus());
        if (reassigned) notifyOwner(owner, actor, "A readiness task has been assigned to you. Sign in to review your authorized event tasks.");
        return view(task);
    }

    private void notifyOwner(User owner, User actor, String message) {
        if (!owner.getId().equals(actor.getId())) notifications.createNotification(owner, message, "/app/");
    }

    private User assignableOwner(Event event, Long ownerId) {
        User owner = users.findForUpdate(ownerId).filter(User::isEnabled).orElseThrow(DomainAccess::denied);
        boolean isAdministrator = owner.getRole() == Role.ADMIN;
        boolean isOrganizer = (owner.getRole() == Role.FACULTY || owner.getRole() == Role.ORGANIZER)
                && event.getOrganizer() != null && owner.getId().equals(event.getOrganizer().getId());
        if (!isAdministrator && !isOrganizer) throw DomainAccess.denied();
        return owner;
    }

    private void requireMutableEvent(Event event) {
        if (event.getStatus() != EventStatus.APPROVED)
            throw new IllegalStateException("Readiness tasks can be changed only for approved events");
    }

    private boolean validTransition(String current, String next) {
        if (current.equals(next)) return true;
        return switch (current) {
            case "OPEN" -> List.of("IN_PROGRESS", "BLOCKED", "DONE").contains(next);
            case "IN_PROGRESS" -> List.of("OPEN", "BLOCKED", "DONE").contains(next);
            case "BLOCKED" -> List.of("OPEN", "IN_PROGRESS", "DONE").contains(next);
            case "DONE" -> "OPEN".equals(next);
            default -> false;
        };
    }

    private boolean sameCreate(ReadinessTask task, String title, String description, String category, Long ownerId, Instant dueAt) {
        return task.getTitle().equals(title) && task.getDescription().equals(description)
                && task.getCategory().equals(category) && task.getOwner().getId().equals(ownerId)
                && task.getDueAt().equals(dueAt);
    }

    private String required(String value, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max)
            throw new IllegalArgumentException("Invalid task text");
        return value.trim();
    }

    private ReadinessTaskDtos.Item view(ReadinessTask task) {
        return new ReadinessTaskDtos.Item(task.getId(), task.getEvent().getId(), task.getTitle(), task.getDescription(),
                task.getCategory(), task.getStatus(), task.getCompletionNote(), task.getOwner().getId(), task.getOwner().getName(),
                task.getOwner().getRole().name(), task.getCreatedBy().getId(), task.getDueAt(), task.getRevision(),
                task.getCreatedAt(), task.getUpdatedAt());
    }
}

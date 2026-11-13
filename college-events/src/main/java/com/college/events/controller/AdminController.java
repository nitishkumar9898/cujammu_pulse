package com.college.events.controller;

import com.college.events.model.*;
import com.college.events.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.List;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final EventService eventService;
    private final UserService userService;
    private final RegistrationService registrationService;
    private final CategoryService categoryService;
    private final CsvExportService csvExportService;
    private final com.college.events.service.FeatureFlagService featureFlagService;
    private final com.college.events.service.AcademicAuthorityService academicAuthorityService;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("totalUsers",       userService.countAll());
        model.addAttribute("totalStudents",    userService.countByRole(Role.STUDENT));
        model.addAttribute("totalOrganizers",  userService.countByRole(Role.ORGANIZER));
        model.addAttribute("totalEvents",      eventService.countAll());
        model.addAttribute("pendingEvents",    eventService.countByStatus(EventStatus.PENDING));
        model.addAttribute("approvedEvents",   eventService.countByStatus(EventStatus.APPROVED));
        model.addAttribute("completedEvents",  eventService.countByStatus(EventStatus.COMPLETED));
        model.addAttribute("rejectedEvents",   eventService.countByStatus(EventStatus.REJECTED));
        model.addAttribute("cancelledEvents",  eventService.countByStatus(EventStatus.CANCELLED));
        model.addAttribute("totalRegistrations", registrationService.countAll());
        model.addAttribute("pendingEventsList",
            eventService.findByStatus(EventStatus.PENDING).stream().limit(5).toList());
        model.addAttribute("recentUsers",
            userService.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(5).toList());
        return "admin/dashboard";
    }

    @GetMapping("/features")
    public String featureFlags(Model model) {
        model.addAttribute("flags", featureFlagService.getAllFlags());
        return "admin/features";
    }

    @GetMapping("/academic-access")
    public String academicAccess(Model model) {
        model.addAttribute("grants", academicAuthorityService.activeGrants());
        model.addAttribute("departments", academicAuthorityService.activeDepartments());
        model.addAttribute("candidates", userService.findAll().stream()
                .filter(User::isEnabled)
                .filter(user -> user.getRole() == Role.STAFF || user.getRole() == Role.FACULTY || user.getRole() == Role.ORGANIZER)
                .toList());
        model.addAttribute("capabilities", com.college.events.service.AcademicAuthorityService.Capability.values());
        return "admin/academic-access";
    }

    @PostMapping("/academic-access/grant")
    public String grantAcademicAccess(@RequestParam Long userId, @RequestParam String departmentName,
                                      @RequestParam String capability, RedirectAttributes ra) {
        try {
            academicAuthorityService.grant(userId, departmentName,
                    com.college.events.service.AcademicAuthorityService.Capability.valueOf(capability));
            ra.addFlashAttribute("successMsg", "Academic access granted for the selected department.");
        } catch (IllegalArgumentException failure) {
            ra.addFlashAttribute("errorMsg", "The selected academic access request is invalid.");
        }
        return "redirect:/admin/academic-access";
    }

    @PostMapping("/academic-access/{grantId}/revoke")
    public String revokeAcademicAccess(@PathVariable Long grantId, RedirectAttributes ra) {
        academicAuthorityService.revoke(grantId);
        ra.addFlashAttribute("successMsg", "Academic access revoked.");
        return "redirect:/admin/academic-access";
    }

    @PostMapping("/features/toggle")
    public String toggleFeature(@RequestParam String key,
                                @RequestParam boolean enabled,
                                @RequestParam long expectedRevision,
                                @RequestParam String reason,
                                @AuthenticationPrincipal User admin,
                                RedirectAttributes ra) {
        try {
            featureFlagService.setFeatureEnabled(key, enabled, expectedRevision, reason);
            ra.addFlashAttribute("successMsg", "Feature switch '" + key + "' successfully updated to " + (enabled ? "ENABLED" : "DISABLED") + ".");
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/features";
    }

    // ─── EVENTS ─────────────────────────────────────────────────────────

    @GetMapping("/events")
    public String allEvents(@RequestParam(required = false) String status, Model model) {
        List<Event> events;
        if (status != null && !status.isBlank()) {
            try {
                events = eventService.findByStatus(EventStatus.valueOf(status.toUpperCase()));
            } catch (Exception e) {
                events = eventService.findAll();
            }
        } else {
            events = eventService.findAll();
        }
        model.addAttribute("events", events);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("statuses", EventStatus.values());
        return "admin/events";
    }

    @GetMapping("/events/pending")
    public String pendingEvents(Model model) {
        model.addAttribute("events", eventService.findByStatus(EventStatus.PENDING));
        return "admin/pending-events";
    }

    @GetMapping("/events/publication-queue")
    public String approvedUnpublishedEvents(@RequestParam(defaultValue = "0") int page, Model model) {
        var result = eventService.findApprovedUnpublished(page);
        model.addAttribute("events", result.getContent());
        model.addAttribute("page", page);
        model.addAttribute("hasNext", result.hasNext());
        return "admin/publication-queue";
    }

    @GetMapping("/events/{id}")
    public String viewEvent(@PathVariable Long id, Model model) {
        Event event = eventService.findById(id)
            .orElseThrow(() -> new RuntimeException("Event not found"));
        List<Registration> registrations = registrationService.findByEvent(event);
        model.addAttribute("event", event);
        model.addAttribute("canReviewProposal", eventService.canReviewProposal(id));
        model.addAttribute("registrations", registrations);
        model.addAttribute("confirmedCount", registrationService.countConfirmed(event));
        model.addAttribute("waitlistedCount", registrationService.countWaitlisted(event));
        return "admin/event-detail";
    }

    @PostMapping("/events/{id}/approve")
    public String approveEvent(@PathVariable Long id, @RequestParam long expectedRevision, RedirectAttributes redirectAttributes) {
        try {
            eventService.approveEvent(id, expectedRevision);
            redirectAttributes.addFlashAttribute("successMsg", "✅ Proposal approved; event remains private until publication.");
        } catch (Exception e) {
            if (e instanceof org.springframework.security.access.AccessDeniedException denied) throw denied;
            redirectAttributes.addFlashAttribute("errorMsg", "Review decision could not be applied. Reload the proposal and check its current state.");
        }
        return "redirect:/admin/events/pending";
    }

    @PostMapping("/events/{id}/reject")
    public String rejectEvent(@PathVariable Long id,
                              @RequestParam String reason,
                              @RequestParam long expectedRevision,
                              RedirectAttributes redirectAttributes) {
        try {
            eventService.rejectEvent(id, reason, expectedRevision);
            redirectAttributes.addFlashAttribute("successMsg", "Event rejected with reason noted.");
        } catch (Exception e) {
            if (e instanceof org.springframework.security.access.AccessDeniedException denied) throw denied;
            redirectAttributes.addFlashAttribute("errorMsg", "Review decision could not be applied. Reload the proposal and check its current state.");
        }
        return "redirect:/admin/events/pending";
    }

    @PostMapping("/events/{id}/publish")
    public String publishEvent(@PathVariable Long id, @RequestParam long expectedRevision, RedirectAttributes redirectAttributes) {
        try {
            eventService.publishApprovedEvent(id, expectedRevision);
            redirectAttributes.addFlashAttribute("successMsg", "Event published; public discovery and approved notices are now enabled.");
        } catch (Exception e) {
            if (e instanceof org.springframework.security.access.AccessDeniedException denied) throw denied;
            redirectAttributes.addFlashAttribute("errorMsg", "Publication could not be applied. Reload the queue and verify the current state.");
        }
        return "redirect:/admin/events/publication-queue";
    }

    @PostMapping("/events/{id}/delete")
    public String deleteEvent(@PathVariable Long id,
                              @AuthenticationPrincipal User user,
                              RedirectAttributes redirectAttributes) {
        try {
            eventService.deleteEvent(id, user);
            redirectAttributes.addFlashAttribute("successMsg", "Event deleted.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/events";
    }

    @GetMapping("/events/{id}/export-csv")
    public ResponseEntity<byte[]> exportCsv(@PathVariable Long id) throws IOException {
        Event event = eventService.findById(id)
            .orElseThrow(() -> new RuntimeException("Event not found"));
        byte[] csv = csvExportService.exportRegistrations(event);
        String filename = event.getTitle().replaceAll("[^a-zA-Z0-9]", "_") + "_registrations.csv";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(csv);
    }

    // ─── USERS ──────────────────────────────────────────────────────────

    @GetMapping("/users")
    public String listUsers(@RequestParam(required = false) String role, Model model) {
        List<User> users;
        if (role != null && !role.isBlank()) {
            try {
                users = userService.findAll().stream()
                    .filter(u -> u.getRole() == Role.valueOf(role.toUpperCase()))
                    .toList();
            } catch (Exception e) {
                users = userService.findAllExceptAdmins();
            }
        } else {
            users = userService.findAllExceptAdmins();
        }
        model.addAttribute("users", users);
        model.addAttribute("selectedRole", role);
        model.addAttribute("roles", Role.values());
        return "admin/users";
    }

    @PostMapping("/users/{id}/toggle-status")
    public String toggleUserStatus(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            userService.toggleUserStatus(id);
            redirectAttributes.addFlashAttribute("successMsg", "User status updated.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    // ─── CATEGORIES ─────────────────────────────────────────────────────

    @GetMapping("/categories")
    public String listCategories(Model model) {
        model.addAttribute("categories", categoryService.findAll());
        return "admin/categories";
    }

    @PostMapping("/categories/create")
    public String createCategory(@RequestParam String name,
                                 @RequestParam(defaultValue = "bi-tag") String icon,
                                 @RequestParam(defaultValue = "primary") String colorClass,
                                 RedirectAttributes redirectAttributes) {
        try {
            categoryService.save(name, icon, colorClass);
            redirectAttributes.addFlashAttribute("successMsg", "Category created: " + name);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/categories";
    }

    @PostMapping("/categories/{id}/delete")
    public String deleteCategory(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            categoryService.delete(id);
            redirectAttributes.addFlashAttribute("successMsg", "Category deleted.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", "Cannot delete category: it may be in use.");
        }
        return "redirect:/admin/categories";
    }
}

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

    @GetMapping("/events/{id}")
    public String viewEvent(@PathVariable Long id, Model model) {
        Event event = eventService.findById(id)
            .orElseThrow(() -> new RuntimeException("Event not found"));
        List<Registration> registrations = registrationService.findByEvent(event);
        model.addAttribute("event", event);
        model.addAttribute("registrations", registrations);
        model.addAttribute("confirmedCount", registrationService.countConfirmed(event));
        model.addAttribute("waitlistedCount", registrationService.countWaitlisted(event));
        return "admin/event-detail";
    }

    @PostMapping("/events/{id}/approve")
    public String approveEvent(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            eventService.approveEvent(id);
            redirectAttributes.addFlashAttribute("successMsg", "✅ Event approved successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/events/pending";
    }

    @PostMapping("/events/{id}/reject")
    public String rejectEvent(@PathVariable Long id,
                              @RequestParam String reason,
                              RedirectAttributes redirectAttributes) {
        try {
            eventService.rejectEvent(id, reason);
            redirectAttributes.addFlashAttribute("successMsg", "Event rejected with reason noted.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/events/pending";
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

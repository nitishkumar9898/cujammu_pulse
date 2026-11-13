package com.college.events.controller;

import com.college.events.dto.EventDto;
import com.college.events.model.*;
import com.college.events.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequestMapping("/organizer")
@RequiredArgsConstructor
public class OrganizerController {

    private final EventService eventService;
    private final EventScheduleService eventScheduleService;
    private final RegistrationService registrationService;
    private final CategoryService categoryService;
    private final CsvExportService csvExportService;
    private final ExcelExportService excelExportService;
    private final EmailService emailService;
    private final UserService userService;
    private final FeedbackService feedbackService;
    private final SpeakerService speakerService;
    private final SponsorService sponsorService;
    private final AuditService auditService;
    private final CertificateService certificateService;

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal User user, Model model) {
        List<Event> myEvents = eventService.findByOrganizer(user);
        long totalEvents   = myEvents.size();
        long pendingEvents = myEvents.stream().filter(e -> e.getStatus() == EventStatus.PENDING).count();
        long approvedEvents = myEvents.stream().filter(e -> e.getStatus() == EventStatus.APPROVED).count();
        long totalRegistrations = myEvents.stream()
            .mapToLong(e -> registrationService.countConfirmed(e)).sum();

        double avgRating = myEvents.stream()
            .mapToDouble(feedbackService::getAverageRating)
            .filter(r -> r > 0)
            .average().orElse(0.0);

        model.addAttribute("user", user);
        model.addAttribute("myEvents", myEvents.stream().limit(5).toList());
        model.addAttribute("totalEvents", totalEvents);
        model.addAttribute("pendingEvents", pendingEvents);
        model.addAttribute("approvedEvents", approvedEvents);
        model.addAttribute("totalRegistrations", totalRegistrations);
        model.addAttribute("avgRating", String.format("%.1f", avgRating));
        return "organizer/dashboard";
    }

    @GetMapping("/events")
    public String listEvents(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("events", eventService.findByOrganizer(user));
        model.addAttribute("user", user);
        return "organizer/events";
    }

    @GetMapping("/events/create")
    public String showCreateForm(Model model) {
        model.addAttribute("dto", new EventDto());
        model.addAttribute("categories", categoryService.findAll());
        return "organizer/create-event";
    }

    @PostMapping("/events/create")
    public String createEvent(@Valid @ModelAttribute("dto") EventDto dto,
                              BindingResult result,
                              @AuthenticationPrincipal User user,
                              RedirectAttributes redirectAttributes,
                              Model model) {
        if (result.hasErrors()) {
            model.addAttribute("categories", categoryService.findAll());
            return "organizer/create-event";
        }
        try {
            Event event = eventService.createEvent(dto, user);
            redirectAttributes.addFlashAttribute("successMsg",
                "✅ Event submitted for admin approval: " + event.getTitle());
            return "redirect:/organizer/events";
        } catch (Exception e) {
            if (e instanceof org.springframework.security.access.AccessDeniedException denied) throw denied;
            model.addAttribute("errorMsg", "Proposal could not be saved. Reload the form, check the fields and try again.");
            model.addAttribute("categories", categoryService.findAll());
            return "organizer/create-event";
        }
    }

    @GetMapping("/events/{id}/edit")
    public String showEditForm(@PathVariable Long id,
                               @AuthenticationPrincipal User user,
                               Model model) {
        Event event = eventService.ownProposal(id, user);

        EventDto dto = new EventDto();
        dto.setExpectedLifecycleVersion(event.getLifecycleVersion());
        dto.setTitle(event.getTitle());
        dto.setDescription(event.getDescription());
        dto.setCategoryId(event.getCategory() != null ? event.getCategory().getId() : null);
        dto.setVenue(event.getVenue());
        dto.setEventDate(event.getEventDate());
        dto.setEventEndsAt(event.getEventEndsAt());
        dto.setMaxSeats(event.getMaxSeats());
        dto.setFees(event.getFees());
        dto.setContactEmail(event.getContactEmail());
        dto.setTargetDepartment(event.getTargetDepartment() != null ? event.getTargetDepartment() : "ALL");
        dto.setGenerateCertificate(event.isGenerateCertificate());

        model.addAttribute("dto", dto);
        model.addAttribute("event", event);
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("speakers", speakerService.getSpeakersForEvent(event));
        model.addAttribute("sponsors", sponsorService.getSponsorsForEvent(event));
        return "organizer/edit-event";
    }

    @PostMapping("/events/{id}/edit")
    public String updateEvent(@PathVariable Long id,
                              @Valid @ModelAttribute("dto") EventDto dto,
                              BindingResult result,
                              @AuthenticationPrincipal User user,
                              RedirectAttributes redirectAttributes,
                              Model model) {
        Event proposal = eventService.ownProposal(id, user);
        model.addAttribute("event", proposal);
        model.addAttribute("speakers", speakerService.getSpeakersForEvent(proposal));
        model.addAttribute("sponsors", sponsorService.getSponsorsForEvent(proposal));
        if (result.hasErrors()) {
            model.addAttribute("categories", categoryService.findAll());
            return "organizer/edit-event";
        }
        try {
            eventService.updateEvent(id, dto, user);
            redirectAttributes.addFlashAttribute("successMsg",
                "Event updated and resubmitted for approval.");
            return "redirect:/organizer/events";
        } catch (Exception e) {
            if (e instanceof org.springframework.security.access.AccessDeniedException denied) throw denied;
            model.addAttribute("errorMsg", "Proposal could not be saved. Reload the form, check the fields and try again.");
            model.addAttribute("categories", categoryService.findAll());
            return "organizer/edit-event";
        }
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
        return "redirect:/organizer/events";
    }

    @PostMapping("/events/{id}/complete")
    public String markComplete(@PathVariable Long id,
                               @AuthenticationPrincipal User user,
                               RedirectAttributes redirectAttributes) {
        try {
            eventService.markCompleted(id, user);
            redirectAttributes.addFlashAttribute("successMsg", "Event marked as completed.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/organizer/events";
    }

    // ─── CANCEL EVENT ──────────────────────────────────────────────────────

    @PostMapping("/events/{id}/cancel")
    public String cancelEvent(@PathVariable Long id,
                              @AuthenticationPrincipal User user,
                              RedirectAttributes redirectAttributes) {
        try {
            eventScheduleService.cancel(id);
            redirectAttributes.addFlashAttribute("successMsg",
                "⚠️ Event cancelled. All registered students have been notified by email.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/organizer/events";
    }

    // ─── POSTPONE EVENT ────────────────────────────────────────────────────

    @GetMapping("/events/{id}/postpone")
    public String showPostponeForm(@PathVariable Long id,
                                   @AuthenticationPrincipal User user,
                                   Model model) {
        Event event = eventService.findById(id)
            .orElseThrow(() -> new RuntimeException("Event not found"));
        if (!event.getOrganizer().getId().equals(user.getId())) {
            return "redirect:/organizer/events";
        }
        model.addAttribute("event", event);
        return "organizer/postpone-event";
    }

    @PostMapping("/events/{id}/postpone")
    public String processPostpone(@PathVariable Long id,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime newDate,
                                  @RequestParam String reason,
                                  @AuthenticationPrincipal User user,
                                  RedirectAttributes redirectAttributes) {
        try {
            eventScheduleService.reschedule(id, newDate, reason);
            redirectAttributes.addFlashAttribute("successMsg",
                "📅 Event postponed successfully. All registered students have been notified.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/organizer/events";
    }

    // ─── REGISTRATIONS & SCANNING ─────────────────────────────────────────

    @GetMapping("/events/{id}/registrations")
    public String viewRegistrations(@PathVariable Long id,
                                    @AuthenticationPrincipal User user,
                                    Model model) {
        Event event = eventService.findById(id)
            .orElseThrow(() -> new RuntimeException("Event not found"));
        if (!event.getOrganizer().getId().equals(user.getId())) {
            return "redirect:/organizer/events";
        }
        List<Registration> registrations = registrationService.findByEvent(event);
        long confirmed = registrationService.countConfirmed(event);
        long waitlisted = registrationService.countWaitlisted(event);

        model.addAttribute("event", event);
        model.addAttribute("registrations", registrations);
        model.addAttribute("confirmedCount", confirmed);
        model.addAttribute("waitlistedCount", waitlisted);
        model.addAttribute("availableSeats", event.getMaxSeats() - confirmed);
        return "organizer/registrations";
    }

    @GetMapping("/events/{id}/scan")
    public String scanPage() {
        return "redirect:/organizer/events";
    }

    @PostMapping("/events/{id}/scan")
    @ResponseBody
    public ResponseEntity<java.util.Map<String, Object>> processAttendanceScan() {
        return ResponseEntity.status(409).body(java.util.Map.of("error", "Legacy QR attendance is disabled until owner-confirmed scan proof is available."));
    }

    @GetMapping("/events/{id}/export-csv")
    public ResponseEntity<byte[]> exportCsv(@PathVariable Long id,
                                             @AuthenticationPrincipal User user) throws IOException {
        Event event = eventService.findById(id)
            .orElseThrow(() -> new RuntimeException("Event not found"));
        if (!event.getOrganizer().getId().equals(user.getId())) {
            return ResponseEntity.status(403).build();
        }

        byte[] csvBytes = csvExportService.exportRegistrations(event);
        String filename = event.getTitle().replaceAll("[^a-zA-Z0-9]", "_") + "_registrations.csv";

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(csvBytes);
    }

    @GetMapping("/events/{id}/export-excel")
    public ResponseEntity<byte[]> exportExcel(@PathVariable Long id,
                                               @AuthenticationPrincipal User user) throws IOException {
        Event event = eventService.findById(id)
            .orElseThrow(() -> new RuntimeException("Event not found"));
        if (!event.getOrganizer().getId().equals(user.getId())) {
            return ResponseEntity.status(403).build();
        }

        byte[] excelBytes = excelExportService.exportRegistrations(event);
        String filename = event.getTitle().replaceAll("[^a-zA-Z0-9]", "_") + "_registrations.xlsx";

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(excelBytes);
    }

    @PostMapping("/events/{id}/announce")
    public String sendAnnouncement(@PathVariable Long id,
                                   @RequestParam String announcementSubject,
                                   @RequestParam String message,
                                   @AuthenticationPrincipal User user,
                                   RedirectAttributes redirectAttributes) {
        Event event = eventService.findById(id)
            .orElseThrow(() -> new RuntimeException("Event not found"));
        if (!event.getOrganizer().getId().equals(user.getId())) {
            return "redirect:/organizer/events";
        }

        redirectAttributes.addFlashAttribute("warningMsg",
            "Custom announcements are currently unavailable. No message was sent.");
        return "redirect:/organizer/events/" + id + "/registrations";
    }

    // ─── PROFILE ──────────────────────────────────────────────────────────

    @GetMapping("/profile")
    public String profile(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("user", user);
        return "organizer/profile";
    }

    @PostMapping("/profile/update")
    public String updateProfile(@AuthenticationPrincipal User user,
                                @RequestParam String name,
                                @RequestParam String department,
                                @RequestParam String phone,
                                RedirectAttributes redirectAttributes) {
        userService.updateProfile(user, name, department, phone);
        redirectAttributes.addFlashAttribute("successMsg", "Profile updated!");
        return "redirect:/organizer/profile";
    }

    @PostMapping("/profile/picture")
    public String uploadProfilePicture(@AuthenticationPrincipal User user,
                                       @RequestParam("file") MultipartFile file,
                                       RedirectAttributes redirectAttributes) {
        try {
            userService.updateProfilePicture(user, file);
            redirectAttributes.addFlashAttribute("successMsg", "Profile picture updated! 📸");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", "Failed to upload: " + e.getMessage());
        }
        return "redirect:/organizer/profile";
    }

    @PostMapping("/profile/change-password")
    public String changePassword(@AuthenticationPrincipal User user,
                                 @RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmNewPassword,
                                 jakarta.servlet.http.HttpSession session,
                                 RedirectAttributes redirectAttributes) {
        if (!userService.checkPassword(user, currentPassword)) {
            redirectAttributes.addFlashAttribute("errorMsg", "Current password is incorrect.");
            return "redirect:/organizer/profile";
        }
        if (!newPassword.equals(confirmNewPassword)) {
            redirectAttributes.addFlashAttribute("errorMsg", "New passwords do not match.");
            return "redirect:/organizer/profile";
        }
        try {
            userService.changePassword(user, newPassword);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
            return "redirect:/organizer/profile";
        }
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        session.invalidate();
        return "redirect:/login?passwordChanged=true";
    }

    // ─── SPEAKERS & SPONSORS ──────────────────────────────────────────────

    @PostMapping("/events/{id}/speakers/add")
    public String addSpeaker(@PathVariable Long id,
                             @RequestParam String name,
                             @RequestParam String bio,
                             @RequestParam(required=false) String linkedInUrl,
                             @RequestParam(required=false) String profileImageUrl,
                             @AuthenticationPrincipal User user,
                             RedirectAttributes redirectAttributes) {
        Event event = eventService.findById(id).orElseThrow();
        if (!event.getOrganizer().getId().equals(user.getId())) return "redirect:/organizer/events";
        
        speakerService.addSpeaker(event, name, bio, linkedInUrl, profileImageUrl);
        redirectAttributes.addFlashAttribute("successMsg", "Speaker added successfully.");
        return "redirect:/organizer/events/" + id + "/edit";
    }

    @PostMapping("/events/{id}/sponsors/add")
    public String addSponsor(@PathVariable Long id,
                             @RequestParam String companyName,
                             @RequestParam String tier,
                             @RequestParam(required=false) String websiteUrl,
                             @RequestParam(required=false) String logoUrl,
                             @AuthenticationPrincipal User user,
                             RedirectAttributes redirectAttributes) {
        Event event = eventService.findById(id).orElseThrow();
        if (!event.getOrganizer().getId().equals(user.getId())) return "redirect:/organizer/events";
        
        sponsorService.addSponsor(event, companyName, logoUrl, websiteUrl, tier);
        redirectAttributes.addFlashAttribute("successMsg", "Sponsor added successfully.");
        return "redirect:/organizer/events/" + id + "/edit";
    }

    // ─── CERTIFICATES ─────────────────────────────────────────────────────

    @GetMapping("/events/{id}/certificates")
    public String viewCertificates(@PathVariable Long id, @AuthenticationPrincipal User user, Model model) {
        Event event = eventService.findById(id).orElseThrow();
        if (!event.getOrganizer().getId().equals(user.getId())) return "redirect:/organizer/events";

        long attendedCount = registrationService.findByEvent(event).stream()
            .filter(r -> r.getStatus() == RegistrationStatus.ATTENDED).count();
        
        java.util.List<Certificate> certificates = certificateService.findByEvent(event);

        java.util.Map<String, java.util.Map<String, String>> templates = new java.util.LinkedHashMap<>();
        templates.put("classic-blue", java.util.Map.of("name", "Classic Blue", "description", "Professional and clean", "emoji", "🔵", "primaryColor", "#1e3a8a", "accentColor", "#3b82f6"));
        templates.put("modern-dark", java.util.Map.of("name", "Modern Dark", "description", "Sleek dark mode design", "emoji", "⚫", "primaryColor", "#1e293b", "accentColor", "#475569"));
        templates.put("elegant-gold", java.util.Map.of("name", "Elegant Gold", "description", "Premium certificate", "emoji", "🟡", "primaryColor", "#854d0e", "accentColor", "#eab308"));
        
        model.addAttribute("event", event);
        model.addAttribute("attendedCount", attendedCount);
        model.addAttribute("certificates", certificates);
        model.addAttribute("templates", templates);
        
        return "organizer/certificates";
    }

    @PostMapping("/events/{id}/certificates/issue")
    public String issueCertificates(@PathVariable Long id, @RequestParam String templateId, @AuthenticationPrincipal User user, RedirectAttributes redirectAttributes) {
        Event event = eventService.findById(id).orElseThrow();
        if (!event.getOrganizer().getId().equals(user.getId())) return "redirect:/organizer/events";

        certificateService.issueCertificatesForEvent(event, templateId, emailService);
        
        redirectAttributes.addFlashAttribute("successMsg", "Certificates issued and emails sent successfully!");
        return "redirect:/organizer/events/" + id + "/certificates";
    }
}

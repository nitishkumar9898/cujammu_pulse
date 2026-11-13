package com.college.events.controller;

import com.college.events.model.*;
import com.college.events.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/student")
@RequiredArgsConstructor
public class StudentController {

    private final EventService eventService;
    private final RegistrationService registrationService;
    private final CategoryService categoryService;
    private final UserService userService;
    private final FeedbackService feedbackService;
    private final SpeakerService speakerService;
    private final SponsorService sponsorService;
    private final PointCertificateIssuanceService certificateIssuance;
    private final com.college.events.service.DynamicFormService dynamicFormService;
    private final com.college.events.service.CoEAcademicService coEAcademicService;
    private final com.college.events.service.SupportHelpdeskService supportHelpdeskService;
    private final FeatureFlagService featureFlagService;

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal User user, Model model) {
        List<Registration> myRegistrations = registrationService.findByUser(user);
        long upcoming = myRegistrations.stream()
            .filter(r -> r.getStatus() == RegistrationStatus.REGISTERED
                      || r.getStatus() == RegistrationStatus.WAITLISTED)
            .filter(r -> r.getEvent().isUpcoming())
            .count();
        long attended = myRegistrations.stream()
            .filter(r -> r.getStatus() == RegistrationStatus.ATTENDED).count();
        long waitlisted = myRegistrations.stream()
            .filter(r -> r.getStatus() == RegistrationStatus.WAITLISTED).count();

        model.addAttribute("user", user);
        model.addAttribute("upcomingCount", upcoming);
        model.addAttribute("attendedCount", attended);
        model.addAttribute("waitlistedCount", waitlisted);
        model.addAttribute("recentRegistrations", myRegistrations.stream().limit(5).toList());
        model.addAttribute("featuredEvents", eventService.findUpcomingApproved().stream().limit(3).toList());
        return "student/dashboard";
    }

    @GetMapping("/events")
    public String browseEvents(@AuthenticationPrincipal User user,
                               @RequestParam(required = false) String keyword,
                               @RequestParam(required = false) Long categoryId,
                               Model model) {
        List<Event> events = eventService.searchEvents(keyword, categoryId);
        List<Registration> myRegistrations = registrationService.findByUser(user);
        List<Long> registeredEventIds = myRegistrations.stream()
            .filter(r -> r.getStatus() != RegistrationStatus.CANCELLED)
            .map(r -> r.getEvent().getId())
            .toList();

        model.addAttribute("events", events);
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("registeredEventIds", registeredEventIds);
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedCategory", categoryId);
        return "student/events";
    }

    @GetMapping("/events/{id}")
    public String viewEvent(@PathVariable Long id,
                            @AuthenticationPrincipal User user,
                            Model model) {
        Event event = eventService.findById(id)
            .orElseThrow(() -> new RuntimeException("Event not found"));

        Optional<Registration> myReg = registrationService.findByUserAndEvent(user, event);
        long confirmedCount = registrationService.countConfirmed(event);
        long waitlistedCount = registrationService.countWaitlisted(event);
        int available = event.getMaxSeats() - (int) confirmedCount;

        model.addAttribute("event", event);
        model.addAttribute("myRegistration", myReg.orElse(null));
        model.addAttribute("confirmedCount", confirmedCount);
        model.addAttribute("waitlistedCount", waitlistedCount);
        model.addAttribute("availableSeats", available);
        model.addAttribute("isFull", available <= 0);
        model.addAttribute("speakers", speakerService.getSpeakersForEvent(event));
        model.addAttribute("sponsors", sponsorService.getSponsorsForEvent(event));
        return "student/event-detail";
    }

    @PostMapping("/events/{id}/register")
    public String registerForEvent(@PathVariable Long id,
                                   @AuthenticationPrincipal User user,
                                   RedirectAttributes redirectAttributes) {
        try {
            Registration reg = registrationService.register(user, id);
            if (reg.getStatus() == RegistrationStatus.REGISTERED) {
                redirectAttributes.addFlashAttribute("successMsg",
                    "Registration confirmed. Secure event-pass display is not enabled in this release.");
            } else {
                redirectAttributes.addFlashAttribute("warningMsg",
                    "⏳ Event is full. You've been added to the waitlist.");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/student/events/" + id;
    }

    @GetMapping("/my-tickets")
    public String myTickets(@AuthenticationPrincipal User user, Model model) {
        List<Registration> all = registrationService.findByUser(user);
        model.addAttribute("registrations", all);
        model.addAttribute("upcomingRegs", all.stream().filter(r -> r.getStatus() == RegistrationStatus.REGISTERED).toList());
        model.addAttribute("attendedRegs", all.stream().filter(r -> r.getStatus() == RegistrationStatus.ATTENDED).toList());
        model.addAttribute("waitlistedRegs", all.stream().filter(r -> r.getStatus() == RegistrationStatus.WAITLISTED).toList());
        model.addAttribute("cancelledRegs", all.stream().filter(r -> r.getStatus() == RegistrationStatus.CANCELLED).toList());
        model.addAttribute("user", user);
        return "student/my-tickets";
    }

    @GetMapping("/ticket/{registrationId}")
    public String viewTicket(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("warningMsg", "Legacy QR tickets are disabled. No event pass was exposed.");
        return "redirect:/student/my-tickets";
    }

    @PostMapping("/registrations/{id}/cancel")
    public String cancelRegistration(@PathVariable Long id,
                                     @AuthenticationPrincipal User user,
                                     RedirectAttributes redirectAttributes) {
        try {
            registrationService.cancelRegistration(id, user);
            redirectAttributes.addFlashAttribute("successMsg", "Registration cancelled successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/student/my-tickets";
    }

    @PostMapping("/events/{id}/feedback")
    public String submitFeedback(@PathVariable Long id,
                                 @RequestParam int rating,
                                 @RequestParam String comment,
                                 @AuthenticationPrincipal User user,
                                 RedirectAttributes redirectAttributes) {
        try {
            feedbackService.submitFeedback(user, id, rating, comment);
            redirectAttributes.addFlashAttribute("successMsg", "Thank you for your feedback! ⭐");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/student/my-tickets";
    }

    @GetMapping("/profile")
    public String profile(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("user", user);
        return "student/profile";
    }

    @PostMapping("/profile/update")
    public String updateProfile(@AuthenticationPrincipal User currentUser,
                                @RequestParam String name,
                                @RequestParam String department,
                                @RequestParam String phone,
                                @RequestParam(required = false) String enrollmentNo,
                                RedirectAttributes redirectAttributes) {
        try {
            User updated = userService.updateProfile(currentUser, name, department, phone);
            if (enrollmentNo != null && !enrollmentNo.isBlank()) {
                updated.setEnrollmentNo(enrollmentNo);
                userService.saveUser(updated);
            }
            redirectAttributes.addFlashAttribute("successMsg", "Profile updated successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/student/profile";
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
        return "redirect:/student/profile";
    }

    @PostMapping("/profile/picture/ajax")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadProfilePictureAjax(
            @AuthenticationPrincipal User user,
            @RequestParam("file") MultipartFile file) {
        try {
            if (file.getSize() > 5 * 1024 * 1024) {
                return ResponseEntity.ok(Map.of("success", false, "message", "Image must be less than 5 MB."));
            }
            User updated = userService.updateProfilePicture(user, file);
            userService.refreshAuthenticatedUser(updated);
            return ResponseEntity.ok(Map.of("success", true, "filename", updated.getProfilePicture()));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "message", "Upload failed: " + e.getMessage()));
        }
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
            return "redirect:/student/profile";
        }
        if (!newPassword.equals(confirmNewPassword)) {
            redirectAttributes.addFlashAttribute("errorMsg", "New passwords do not match.");
            return "redirect:/student/profile";
        }
        try {
            userService.changePassword(user, newPassword);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
            return "redirect:/student/profile";
        }
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        session.invalidate();
        return "redirect:/login?passwordChanged=true";
    }

    @GetMapping("/certificates")
    public String viewCertificates(@AuthenticationPrincipal User user, @RequestParam(defaultValue = "0") int page, Model model) {
        var certificates = certificateIssuance.ownPage(page);
        model.addAttribute("certificates", certificates.getContent());
        model.addAttribute("certificatePage", page);
        model.addAttribute("certificateHasNext", certificates.hasNext());
        model.addAttribute("activePage", "certificates");
        return "student/certificates";
    }

    @GetMapping("/events/{id}/custom-register")
    public String customEventRegister(@PathVariable Long id,
                                      @AuthenticationPrincipal User user,
                                      Model model) {
        Event event = eventService.findById(id).orElseThrow(() -> new IllegalArgumentException("Event not found"));
        var schema = dynamicFormService.getFormSchema(id);
        var existingResp = dynamicFormService.getUserResponse(id, user.getId());

        model.addAttribute("event", event);
        model.addAttribute("user", user);
        model.addAttribute("schema", schema);
        model.addAttribute("existingResponse", existingResp.orElse(null));
        model.addAttribute("activePage", "events");
        return "student/custom-register";
    }

    @GetMapping("/dashboard-360")
    public String comprehensive360Dashboard(@AuthenticationPrincipal User user, Model model) {
        List<Registration> registrations = registrationService.findByUser(user);
        var certificates = featureFlagService.isEnabled("MODULE_CERTIFICATES") && featureFlagService.isEnabled("MODULE_EVENTS")
                ? certificateIssuance.ownCertificates() : List.<Certificate>of();
        var scorecards = coEAcademicService.getMyScoreCards();
        var tickets = featureFlagService.isFeatureEnabled("MODULE_HELPDESK") ? supportHelpdeskService.ownTickets(0).items() : List.of();

        long attended = registrations.stream().filter(r -> r.getStatus() == RegistrationStatus.ATTENDED).count();
        long upcoming = registrations.stream().filter(r -> r.getStatus() == RegistrationStatus.REGISTERED && r.getEvent().isUpcoming()).count();

        model.addAttribute("user", user);
        model.addAttribute("registrations", registrations);
        model.addAttribute("attendedCount", attended);
        model.addAttribute("upcomingCount", upcoming);
        model.addAttribute("certificates", certificates);
        model.addAttribute("scorecards", scorecards);
        model.addAttribute("tickets", tickets);
        model.addAttribute("activePage", "dashboard-360");
        return "student/dashboard-360";
    }
}

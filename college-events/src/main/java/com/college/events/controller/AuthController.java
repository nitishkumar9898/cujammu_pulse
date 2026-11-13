package com.college.events.controller;

import com.college.events.dto.UserRegistrationDto;
import com.college.events.model.Role;
import com.college.events.model.User;
import com.college.events.service.EmailService;
import com.college.events.service.EventService;
import com.college.events.service.OtpService;
import com.college.events.service.SafeResetLink;
import com.college.events.service.UserService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Controller
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final UserService userService;
    private final EventService eventService;
    private final EmailService emailService;
    private final OtpService otpService;
    private final com.college.events.service.PulseFeedService pulseFeedService;
    private final com.college.events.service.LiveSportsScoreService liveSportsScoreService;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${app.security.reset-link-hosts:}")
    private String resetLinkAllowedHosts;

    @Value("${app.email.backend:smtp}")
    private String emailBackend;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("upcomingEvents", eventService.findUpcomingApproved());
        model.addAttribute("totalEvents", eventService.countAll());
        return "home";
    }

    @GetMapping("/feed")
    public String pulseFeed(Model model) {
        model.addAttribute("posts", pulseFeedService.getFeed(0, 30).getContent());
        model.addAttribute("victories", pulseFeedService.getVictoryShowcases());
        return "feed/index";
    }

    @GetMapping("/sports")
    public String liveSports(Model model) {
        model.addAttribute("liveMatches", liveSportsScoreService.getLiveMatches());
        model.addAttribute("allMatches", liveSportsScoreService.getAllMatches());
        return "sports/index";
    }

    @GetMapping("/login")
    public String login(@RequestParam(required = false) String error,
                        @RequestParam(required = false) String logout,
                        @RequestParam(required = false) String expired,
                        @RequestParam(required = false) String passwordChanged,
                        Model model) {
        if (error != null) model.addAttribute("errorMsg", "Invalid email or password.");
        if (logout != null) model.addAttribute("logoutMsg", "You have been successfully logged out.");
        if (expired != null) model.addAttribute("errorMsg", "Your session has expired. Please login again.");
        if (passwordChanged != null) model.addAttribute("successMsg", "Password changed. Please sign in with your new password.");
        return "auth/login";
    }

    private static final Role[] REGISTRATION_ROLES = new Role[]{
        Role.STUDENT, Role.ALUMNI
    };

    @GetMapping("/register")
    public String showRegisterForm(Model model) {
        model.addAttribute("dto", new UserRegistrationDto());
        model.addAttribute("roles", REGISTRATION_ROLES);
        return "auth/register";
    }

    /**
     * Step 1: Validate form data, store in session, send OTP, redirect to verify page.
     */
    @PostMapping("/register")
    public String processRegister(@Valid @ModelAttribute("dto") UserRegistrationDto dto,
                                  BindingResult result,
                                  HttpSession session,
                                  RedirectAttributes redirectAttributes,
                                  Model model) {
        if (!UserService.isPublicRegistrationRole(dto.getRole())) {
            result.rejectValue("role", "error.dto", "This role requires authorized university provisioning.");
        }
        // Strict email validation based on role
        if (dto.getRole() != null && dto.getEmail() != null) {
            String email = dto.getEmail().trim().toLowerCase();
            switch (dto.getRole()) {
                case STUDENT -> {
                    if (!email.matches("^[a-zA-Z0-9]+(\\.[a-zA-Z0-9]+)?@([a-zA-Z0-9]+\\.)?cujammu\\.ac\\.in$")) {
                        result.rejectValue("email", "error.dto",
                            "Student email must match format: rollno.dept@cujammu.ac.in (e.g. 24beccs63.ccs@cujammu.ac.in)");
                    }
                }
                case FACULTY -> {
                    if (!email.matches("^[a-zA-Z]+(\\.[a-zA-Z]+)?@([a-zA-Z0-9]+\\.)?cujammu\\.ac\\.in$")) {
                        result.rejectValue("email", "error.dto",
                            "Faculty email must match format: name.dept@cujammu.ac.in (no digits, e.g. mann.ccs@cujammu.ac.in)");
                    }
                }
                case STAFF -> {
                    if (!email.endsWith("@cujammu.ac.in")) {
                        result.rejectValue("email", "error.dto",
                            "Staff/Authority email must end with @cujammu.ac.in (e.g. sula.adm@cujammu.ac.in)");
                    }
                }
                case ORGANIZER -> {
                    if (!email.endsWith("@cujammu.ac.in")) {
                        result.rejectValue("email", "error.dto",
                            "Organizer email must end with @cujammu.ac.in (e.g. office.math@cujammu.ac.in)");
                    }
                }
                case ALUMNI -> {
                    // Alumni are permitted personal email addresses (Gmail, etc.)
                    if (!email.contains("@") || !email.contains(".")) {
                        result.rejectValue("email", "error.dto",
                            "Please enter a valid personal email address (e.g. name@gmail.com)");
                    }
                }
                default -> {}
            }
        }

        if (result.hasErrors()) {
            model.addAttribute("roles", REGISTRATION_ROLES);
            return "auth/register";
        }
        if (!dto.isPasswordMatching()) {
            result.rejectValue("confirmPassword", "error.dto", "Passwords do not match");
            model.addAttribute("roles", REGISTRATION_ROLES);
            return "auth/register";
        }
        // Check if email already exists
        if (userService.findByEmail(dto.getEmail()).isPresent()) {
            model.addAttribute("errorMsg", "Email already registered: " + dto.getEmail());
            model.addAttribute("roles", REGISTRATION_ROLES);
            return "auth/register";
        }

        // Generate and send OTP
        String otp = otpService.generateOtp(dto.getEmail());
        if (otp == null) {
            model.addAttribute("errorMsg", "Too many OTP requests. Please wait a few minutes and try again.");
            model.addAttribute("roles", REGISTRATION_ROLES);
            return "auth/register";
        }

        // Store pending registration in session
        session.setAttribute("pendingRegistration", dto);
        session.setAttribute("pendingEmail", dto.getEmail().toLowerCase().trim());

        // Send OTP email and wait briefly for the delivery result so the user
        // sees the REAL status instead of a fake "OTP sent" message.
        boolean mailSent = false;
        try {
            mailSent = emailService.sendOtpEmail(dto.getEmail(), otp, dto.getName())
                    .get(20, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("OTP email attempt failed; failure type={}", e.getClass().getSimpleName());
        }

        if ("console".equalsIgnoreCase(emailBackend)) {
            log.info("Console mail backend active; credential delivery unavailable");
            redirectAttributes.addFlashAttribute("warningMsg",
                "Verification email is unavailable in this environment. Please contact support.");
        } else if (mailSent) {
            log.info("OTP sent to {} for registration verification", dto.getEmail());
            redirectAttributes.addFlashAttribute("successMsg",
                "✅ OTP has been sent to your email successfully. Please check inbox/spam.");
        } else {
            log.error("OTP email FAILED for {} (check MAIL_USER/MAIL_PASS SMTP config)", dto.getEmail());
            redirectAttributes.addFlashAttribute("errorMsg",
                "❌ We could not deliver the OTP email (mail server error). " +
                "Please try Resend, or contact support.");
        }
        return "redirect:/register/verify-otp";
    }

    /**
     * Step 2: Show OTP verification form with countdown timer.
     */
    @GetMapping("/register/verify-otp")
    public String showOtpVerification(HttpSession session, Model model) {
        String email = (String) session.getAttribute("pendingEmail");
        if (email == null) {
            return "redirect:/register";
        }
        long remainingSeconds = otpService.getRemainingSeconds(email);
        model.addAttribute("email", email);
        model.addAttribute("remainingSeconds", remainingSeconds);
        return "auth/verify-otp";
    }

    /**
     * Step 3: Verify OTP and create the account.
     */
    @PostMapping("/register/verify-otp")
    public String processOtpVerification(@RequestParam String otp,
                                         HttpSession session,
                                         RedirectAttributes redirectAttributes,
                                         Model model) {
        String email = (String) session.getAttribute("pendingEmail");
        UserRegistrationDto dto = (UserRegistrationDto) session.getAttribute("pendingRegistration");

        if (email == null || dto == null) {
            redirectAttributes.addFlashAttribute("errorMsg", "Session expired. Please register again.");
            return "redirect:/register";
        }

        OtpService.VerificationResult result = otpService.verifyOtp(email, otp);
        if (!result.success()) {
            model.addAttribute("email", email);
            model.addAttribute("errorMsg", result.message());
            model.addAttribute("remainingSeconds", otpService.getRemainingSeconds(email));
            return "auth/verify-otp";
        }

        // OTP verified — create the account
        try {
            userService.registerUser(dto);
            session.removeAttribute("pendingRegistration");
            session.removeAttribute("pendingEmail");
            redirectAttributes.addFlashAttribute("successMsg",
                "🎉 Email verified & account created successfully! Please login.");
            log.info("Registration completed with OTP verification for {}", email);
            return "redirect:/login";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
            return "redirect:/register";
        }
    }

    /**
     * Resend OTP endpoint (AJAX).
     */
    @PostMapping("/register/resend-otp")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> resendOtp(HttpSession session) {
        String email = (String) session.getAttribute("pendingEmail");
        UserRegistrationDto dto = (UserRegistrationDto) session.getAttribute("pendingRegistration");

        if (email == null || dto == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false, "message", "Session expired. Please register again."));
        }

        String otp = otpService.generateOtp(email);
        if (otp == null) {
            return ResponseEntity.ok(Map.of(
                "success", false, "message", "Too many OTP requests. Please wait and try again."));
        }

        boolean mailSent = false;
        try {
            mailSent = emailService.sendOtpEmail(email, otp, dto.getName())
                    .get(20, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Resend OTP delivery attempt failed; failure type={}", e.getClass().getSimpleName());
        }
        long remaining = otpService.getRemainingSeconds(email);

        if (!mailSent) {
            String msg = "console".equalsIgnoreCase(emailBackend)
                ? "Verification email is unavailable in this environment. Please contact support."
                : "We could not deliver the verification email. Please try again or contact support.";
            return ResponseEntity.ok(Map.of(
                "success", false,
                "message", msg,
                "remainingSeconds", remaining));
        }

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "New OTP sent to " + email,
            "remainingSeconds", remaining));
    }

    // ─── FORGOT PASSWORD ─────────────────────────────────────────────────────

    @GetMapping("/forgot-password")
    public String showForgotPassword() {
        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String processForgotPassword(@RequestParam String email,
                                        RedirectAttributes redirectAttributes,
                                        Model model) {
        if (SafeResetLink.isApprovedBaseUrl(baseUrl, resetLinkAllowedHosts)) {
            String token = userService.generatePasswordResetToken(email);
            if (token != null) {
                SafeResetLink.create(baseUrl, token, resetLinkAllowedHosts)
                        .ifPresent(link -> emailService.sendPasswordResetEmail(email, link));
            }
        } else {
            log.warn("Password recovery suppressed because public HTTPS origin is not allowlisted");
        }
        
        // Always return the same message regardless of whether the email exists
        // This prevents User Enumeration attacks
        redirectAttributes.addFlashAttribute("successMsg",
            "✅ If that email exists in our system, a reset link has been sent.");
        return "redirect:/forgot-password";
    }

    @GetMapping("/reset-password")
    public String showResetPassword(@RequestParam(required = false) String token, Model model) {
        if (token == null || token.isBlank()) {
            model.addAttribute("errorMsg", "Invalid or missing reset token.");
            return "auth/reset-password";
        }
        Optional<User> userOpt = userService.validatePasswordResetToken(token);
        if (userOpt.isEmpty()) {
            model.addAttribute("errorMsg", "This reset link has expired or is invalid. Please request a new one.");
            return "auth/reset-password";
        }
        model.addAttribute("token", token);
        model.addAttribute("userEmail", userOpt.get().getEmail());
        return "auth/reset-password";
    }

    @PostMapping("/reset-password")
    public String processResetPassword(@RequestParam String token,
                                       @RequestParam String newPassword,
                                       @RequestParam String confirmPassword,
                                       RedirectAttributes redirectAttributes) {
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("errorMsg", "Passwords do not match.");
            return "redirect:/reset-password?token=" + token;
        }
        try {
            com.college.events.service.PasswordPolicy.validate(newPassword);
        } catch (IllegalArgumentException invalidPassword) {
            redirectAttributes.addFlashAttribute("errorMsg", invalidPassword.getMessage());
            return "redirect:/reset-password?token=" + token;
        }
        boolean success = userService.resetPasswordWithToken(token, newPassword);
        if (success) {
            redirectAttributes.addFlashAttribute("successMsg",
                "🎉 Password reset successfully! Please login with your new password.");
            return "redirect:/login";
        } else {
            redirectAttributes.addFlashAttribute("errorMsg",
                "Reset link has expired or is invalid. Please request a new one.");
            return "redirect:/forgot-password";
        }
    }

    @GetMapping("/access-denied")
    public String accessDenied() {
        return "error/403";
    }
}

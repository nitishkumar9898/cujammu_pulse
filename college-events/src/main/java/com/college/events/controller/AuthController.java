package com.college.events.controller;

import com.college.events.dto.UserRegistrationDto;
import com.college.events.model.Role;
import com.college.events.model.User;
import com.college.events.service.EmailService;
import com.college.events.service.EventService;
import com.college.events.service.OtpService;
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

@Controller
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final UserService userService;
    private final EventService eventService;
    private final EmailService emailService;
    private final OtpService otpService;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("upcomingEvents", eventService.findUpcomingApproved());
        model.addAttribute("totalEvents", eventService.countAll());
        return "home";
    }

    @GetMapping("/login")
    public String login(@RequestParam(required = false) String error,
                        @RequestParam(required = false) String logout,
                        @RequestParam(required = false) String expired,
                        Model model) {
        if (error != null) model.addAttribute("errorMsg", "Invalid email or password.");
        if (logout != null) model.addAttribute("logoutMsg", "You have been successfully logged out.");
        if (expired != null) model.addAttribute("errorMsg", "Your session has expired. Please login again.");
        return "auth/login";
    }

    @GetMapping("/register")
    public String showRegisterForm(Model model) {
        model.addAttribute("dto", new UserRegistrationDto());
        model.addAttribute("roles", new Role[]{Role.STUDENT, Role.ORGANIZER});
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
        if (result.hasErrors()) {
            model.addAttribute("roles", new Role[]{Role.STUDENT, Role.ORGANIZER});
            return "auth/register";
        }
        if (!dto.isPasswordMatching()) {
            result.rejectValue("confirmPassword", "error.dto", "Passwords do not match");
            model.addAttribute("roles", new Role[]{Role.STUDENT, Role.ORGANIZER});
            return "auth/register";
        }
        // Check if email already exists
        if (userService.findByEmail(dto.getEmail()).isPresent()) {
            model.addAttribute("errorMsg", "Email already registered: " + dto.getEmail());
            model.addAttribute("roles", new Role[]{Role.STUDENT, Role.ORGANIZER});
            return "auth/register";
        }

        // Generate and send OTP
        String otp = otpService.generateOtp(dto.getEmail());
        if (otp == null) {
            model.addAttribute("errorMsg", "Too many OTP requests. Please wait a few minutes and try again.");
            model.addAttribute("roles", new Role[]{Role.STUDENT, Role.ORGANIZER});
            return "auth/register";
        }

        // Store pending registration in session
        session.setAttribute("pendingRegistration", dto);
        session.setAttribute("pendingEmail", dto.getEmail().toLowerCase().trim());

        // Send OTP email asynchronously (Fire and Forget) to ensure instant UI response
        emailService.sendOtpEmail(dto.getEmail(), otp, dto.getName());

        log.info("OTP sent to {} for registration verification", dto.getEmail());
        redirectAttributes.addFlashAttribute("successMsg", "✅ OTP has been sent to your email successfully. Please check inbox/spam.");
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

        emailService.sendOtpEmail(email, otp, dto.getName());
        long remaining = otpService.getRemainingSeconds(email);

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
        String token = userService.generatePasswordResetToken(email);

        if (token != null) {
            String resetLink = baseUrl + "/reset-password?token=" + token;
            emailService.sendPasswordResetEmail(email, resetLink, token);
            log.info("Password reset link sent to: {}", email);
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
        if (newPassword.length() < 8) {
            redirectAttributes.addFlashAttribute("errorMsg", "Password must be at least 8 characters.");
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

package com.college.events.controller;

import com.college.events.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthControllerPasswordRecoveryTest {
    @Test void unsafeOrUnallowlistedOriginDoesNotIssueBearerToken() {
        UserService users=mock(UserService.class);
        var controller=controller(users,mock(EmailService.class));
        org.springframework.test.util.ReflectionTestUtils.setField(controller,"baseUrl","http://localhost:8080");
        org.springframework.test.util.ReflectionTestUtils.setField(controller,"resetLinkAllowedHosts","pulse.example.invalid");
        RedirectAttributes redirect=redirect();

        assertEquals("redirect:/forgot-password",controller.processForgotPassword(
                "student@example.invalid",redirect,mock(Model.class)));
        verifyNoInteractions(users);
        verify(redirect).addFlashAttribute("successMsg","✅ If that email exists in our system, a reset link has been sent.");
    }

    @Test void approvedOriginIssuesTokenAndDeliversOnlyAllowlistedResetLink() {
        UserService users=mock(UserService.class);
        EmailService email=mock(EmailService.class);
        var controller=controller(users,email);
        org.springframework.test.util.ReflectionTestUtils.setField(controller,"baseUrl","https://pulse.example.invalid/");
        org.springframework.test.util.ReflectionTestUtils.setField(controller,"resetLinkAllowedHosts","pulse.example.invalid");
        String token="A".repeat(43);
        when(users.generatePasswordResetToken("student@example.invalid")).thenReturn(token);

        assertEquals("redirect:/forgot-password",controller.processForgotPassword(
                "student@example.invalid",redirect(),mock(Model.class)));
        verify(email).sendPasswordResetEmail("student@example.invalid",
                "https://pulse.example.invalid/reset-password?token="+token);
    }

    private static AuthController controller(UserService users,EmailService email) {
        return new AuthController(users,mock(EventService.class),email,mock(OtpService.class),
                mock(PulseFeedService.class),mock(LiveSportsScoreService.class));
    }

    private static RedirectAttributes redirect() {
        RedirectAttributes redirect=mock(RedirectAttributes.class);
        when(redirect.addFlashAttribute(anyString(),any())).thenReturn(redirect);
        return redirect;
    }
}

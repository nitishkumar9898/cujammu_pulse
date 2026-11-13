package com.college.events.service;

import com.college.events.config.SecurityConfig;
import com.college.events.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(properties = "logging.file.name=target/test-logs/resend-csrf.log")
@ContextConfiguration(classes = {SecurityConfig.class, ResendOtpCsrfTest.Endpoint.class})
class ResendOtpCsrfTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserService userService;
    @MockitoBean UserRepository users;
    @MockitoBean PasswordEncoder encoder;

    @Test void crossSiteResendWithoutCsrfIsDenied() throws Exception {
        mvc.perform(post("/register/resend-otp")).andExpect(status().isForbidden());
    }
    @Test void sameSessionResendWithCsrfIsPermitted() throws Exception {
        mvc.perform(post("/register/resend-otp").with(csrf().asHeader())).andExpect(status().isOk());
    }
    @RestController static class Endpoint {
        @PostMapping("/register/resend-otp") public String resend() { return "ok"; }
    }
}

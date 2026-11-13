package com.college.events.config;

import com.college.events.service.UserService;
import com.college.events.repository.UserRepository;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    public SecurityConfig(@Lazy UserService userService, PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, UserRepository users) throws Exception {
        http
            .addFilterBefore(new com.college.events.security.AuthenticationRateLimitFilter(),
                    org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new AccountSessionFilter(users), AuthorizationFilter.class)
            .authenticationProvider(authenticationProvider())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/h2-console/**").denyAll()
                .requestMatchers(HttpMethod.PUT, "/api/v1/cujammu/features/*").hasRole("ADMIN")
                .requestMatchers("/api/v1/cujammu/helpdesk/all").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/cujammu/helpdesk/tickets/*/status").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/app", "/app/**", "/api/v1/session/csrf", "/api/v1/cujammu/departments",
                        "/api/v1/cujammu/facilities", "/api/v1/cujammu/features").permitAll()
                // Public routes
                .requestMatchers("/", "/register", "/register/verify-otp", "/register/resend-otp",
                                 "/login", "/css/**", "/js/**",
                                 "/images/**", "/favicon.ico",
                                 "/attendance/verify/**", "/certificates/verify/**",
                                 "/error", "/api/health", "/api/ready",
                                 "/forgot-password", "/reset-password",
                                 "/feed", "/sports").permitAll()
                // Role-based routes
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/organizer/**").hasAnyRole("ORGANIZER", "FACULTY")
                .requestMatchers("/student/**").hasRole("STUDENT")
                // Authenticated
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .successHandler(customSuccessHandler())
                .failureUrl("/login?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            .exceptionHandling(ex -> ex
                .defaultAuthenticationEntryPointFor((request, response, exception) -> {
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"AUTHENTICATION_REQUIRED\"}");
                }, PathPatternRequestMatcher.pathPattern("/api/**"))
                .accessDeniedHandler((request, response, denied) -> {
                    // Forwarding a rejected POST to a GET-only error page can
                    // turn a real CSRF denial into an incorrect 405 response.
                    response.setStatus(403);
                    response.setHeader("Cache-Control", "no-store");
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"ACCESS_DENIED\"}");
                })
            )
            .sessionManagement(session -> session
                .sessionFixation().migrateSession() // Protection against Session Fixation
                .maximumSessions(1)
                .expiredUrl("/login?expired=true")
            )
            // CSRF and Security Headers
            .headers(headers -> headers
                .frameOptions(frame -> frame.deny())
                .xssProtection(xss -> xss.disable()) // Let CSP handle XSS in modern browsers, or use headers.xssProtection(config -> config.headerValue(XXssConfig.HEADER_VALUE_BLOCK))
                .cacheControl(cache -> cache.disable())
                .addHeaderWriter(new DefaultNoStoreHeaderWriter())
                .addHeaderWriter(new com.college.events.security.BrowserSecurityHeaders())
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000)
                )
            );

        return http.build();
    }

    @Bean
    public AuthenticationSuccessHandler customSuccessHandler() {
        return new AuthenticationSuccessHandler() {
            @Override
            public void onAuthenticationSuccess(HttpServletRequest request,
                                                HttpServletResponse response,
                                                Authentication authentication) throws IOException {
                var session = request.getSession(false);
                if (session != null && Boolean.TRUE.equals(session.getAttribute("universalClientSignIn"))) {
                    session.removeAttribute("universalClientSignIn");
                    response.sendRedirect(request.getContextPath() + "/app/");
                    return;
                }
                UserDetails userDetails = (UserDetails) authentication.getPrincipal();
                String role = userDetails.getAuthorities().iterator().next().getAuthority();

                String redirectUrl = switch (role) {
                    case "ROLE_ADMIN"     -> "/admin/dashboard";
                    case "ROLE_ORGANIZER", "ROLE_FACULTY" -> "/organizer/dashboard";
                    case "ROLE_STUDENT"   -> "/student/dashboard";
                    default               -> "/";
                };
                response.sendRedirect(request.getContextPath() + redirectUrl);
            }
        };
    }
}

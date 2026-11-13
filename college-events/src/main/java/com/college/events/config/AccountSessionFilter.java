package com.college.events.config;

import com.college.events.model.User;
import com.college.events.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

/** Revalidates persisted account authority before a session can reach authorization. */
public final class AccountSessionFilter extends OncePerRequestFilter {
    private final UserRepository users;

    public AccountSessionFilter(UserRepository users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User principal) {
            var current = users.findById(principal.getId());
            boolean valid = current.filter(user -> user.isEnabled()
                    && user.getRole() == principal.getRole()
                    && Objects.equals(user.getEmail(), principal.getEmail())
                    && Objects.equals(user.getPassword(), principal.getPassword())).isPresent();
            if (!valid) {
                SecurityContextHolder.clearContext();
                var session = request.getSession(false);
                if (session != null) session.invalidate();
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Please sign in again.");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}

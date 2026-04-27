package com.college.events.config;

import com.college.events.model.Category;
import com.college.events.model.Role;
import com.college.events.model.User;
import com.college.events.repository.CategoryRepository;
import com.college.events.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class DataInitializer implements CommandLineRunner {

    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.default-admin-email:admin@college.com}")
    private String adminEmail;

    @Value("${app.default-admin-password:Admin@123}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        seedCategories();
        seedAdminUser();
    }

    private void seedCategories() {
        List<Object[]> categories = List.of(
            new Object[]{"Technical",  "bi-cpu",             "primary"},
            new Object[]{"Cultural",   "bi-music-note-beamed","danger"},
            new Object[]{"Sports",     "bi-trophy",          "success"},
            new Object[]{"Academic",   "bi-book",            "info"},
            new Object[]{"Social",     "bi-people",          "warning"},
            new Object[]{"Workshop",   "bi-tools",           "secondary"},
            new Object[]{"Hackathon",  "bi-laptop",          "dark"},
            new Object[]{"Seminar",    "bi-mic",             "primary"}
        );

        for (Object[] cat : categories) {
            String name = (String) cat[0];
            if (!categoryRepository.existsByName(name)) {
                categoryRepository.save(Category.builder()
                    .name(name)
                    .icon((String) cat[1])
                    .colorClass((String) cat[2])
                    .build());
                log.info("Seeded category: {}", name);
            }
        }
    }

    private void seedAdminUser() {
        if (!userRepository.existsByEmail(adminEmail)) {
            userRepository.save(User.builder()
                .name("System Administrator")
                .email(adminEmail)
                .password(passwordEncoder.encode(adminPassword))
                .role(Role.ADMIN)
                .department("Administration")
                .phone("0000000000")
                .enabled(true)
                .build());
            log.info("Default admin created: {}", adminEmail);
        }
    }
}

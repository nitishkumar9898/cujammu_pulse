package com.college.events.service;

import com.college.events.dto.UserRegistrationDto;
import com.college.events.model.Role;
import com.college.events.model.User;
import com.college.events.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
        
        if (!user.isEnabled()) {
            throw new UsernameNotFoundException("User account is disabled. Please contact admin.");
        }
        
        return user;
    }

    @Transactional
    public User registerUser(UserRegistrationDto dto) {
        if (!isPublicRegistrationRole(dto.getRole())) {
            throw new IllegalArgumentException("This role requires authorized university provisioning.");
        }
        String email = dto.getEmail().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered: " + dto.getEmail());
        }
        if (!dto.isPasswordMatching()) {
            throw new IllegalArgumentException("Passwords do not match");
        }
        PasswordPolicy.validate(dto.getPassword());

        User user = User.builder()
            .name(normalizeText(dto.getName()))
            .email(email)
            .password(passwordEncoder.encode(dto.getPassword()))
            .role(dto.getRole())
            .department(normalizeText(dto.getDepartment()))
            .phone(normalizePhone(dto.getPhone()))
            .enrollmentNo(normalizeText(dto.getEnrollmentNo()))
            .enabled(true)
            .build();

        User saved = userRepository.save(user);
        log.info("Registered new user: {} as {}", saved.getEmail(), saved.getRole());
        return saved;
    }

    public static boolean isPublicRegistrationRole(Role role) {
        return role == Role.STUDENT || role == Role.ALUMNI;
    }

    public Optional<User> findByEmail(String email) {
        if (email == null) {
            return Optional.empty();
        }
        return userRepository.findByEmail(email.toLowerCase(Locale.ROOT).trim());
    }

    @Transactional
    public User saveUser(User user) {
        return userRepository.save(user);
    }

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public List<User> findAllExceptAdmins() {
        return userRepository.findByRoleNot(Role.ADMIN);
    }

    public List<User> findByRole(Role role) {
        return userRepository.findByRole(role);
    }

    public List<User> findStudentsByDepartment(String department) {
        return userRepository.findByRoleAndDepartment(Role.STUDENT, department);
    }

    @Transactional
    public void toggleUserStatus(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        user.setEnabled(!user.isEnabled());
        userRepository.save(user);
        log.info("Toggled status for user {}: enabled={}", user.getEmail(), user.isEnabled());
    }

    @Transactional
    public User updateProfile(User user, String name, String department, String phone) {
        return updateProfile(user, name, department, phone, null);
    }

    @Transactional
    public User updateProfile(User user, String name, String department, String phone, String enrollmentNo) {
        User existingUser = userRepository.findById(user.getId())
            .orElseThrow(() -> new RuntimeException("User not found"));
        existingUser.setName(normalizeText(name));
        existingUser.setDepartment(normalizeText(department));
        existingUser.setPhone(normalizePhone(phone));
        if (enrollmentNo != null) {
            existingUser.setEnrollmentNo(normalizeText(enrollmentNo));
        }
        User saved = userRepository.save(existingUser);
        log.info("Updated profile for user: {}", saved.getEmail());
        return saved;
    }

    @Transactional
    public User updateProfilePicture(User user, MultipartFile file) {
        User existingUser = userRepository.findById(user.getId())
            .orElseThrow(() -> new RuntimeException("User not found"));
        if (file != null && !file.isEmpty()) {
            // Delete old picture if exists
            if (existingUser.getProfilePicture() != null) {
                fileStorageService.deleteFile(existingUser.getProfilePicture());
            }
            String filename = fileStorageService.storeProfilePicture(file);
            existingUser.setProfilePicture(filename);
            userRepository.save(existingUser);
            log.info("Updated profile picture for user: {}", existingUser.getEmail());
        }
        return existingUser;
    }

    @Transactional
    public void changePassword(User user, String newPassword) {
        PasswordPolicy.validate(newPassword);
        if (userRepository.replacePasswordAndRevokeReset(user.getId(), user.getPassword(), passwordEncoder.encode(newPassword)) != 1) {
            throw new IllegalArgumentException("Account is unavailable.");
        }
        log.info("Password changed for account id={}", user.getId());
    }

    public boolean checkPassword(User user, String rawPassword) {
        return passwordEncoder.matches(rawPassword, user.getPassword());
    }

    public long countByRole(Role role) {
        return userRepository.countByRole(role);
    }

    public long countAll() {
        return userRepository.count();
    }

    public List<User> getTopStudentsByPoints(int limit) {
        return userRepository.findByRole(Role.STUDENT).stream()
            .sorted((left, right) -> Integer.compare(right.getGamificationPoints(), left.getGamificationPoints()))
            .limit(limit)
            .toList();
    }

    public void refreshAuthenticatedUser(User updatedUser) {
        Authentication currentAuth = SecurityContextHolder.getContext().getAuthentication();
        if (currentAuth == null || updatedUser == null) {
            return;
        }

        UsernamePasswordAuthenticationToken refreshedAuth =
            new UsernamePasswordAuthenticationToken(
                updatedUser,
                currentAuth.getCredentials(),
                updatedUser.getAuthorities()
            );
        refreshedAuth.setDetails(currentAuth.getDetails());
        SecurityContextHolder.getContext().setAuthentication(refreshedAuth);
    }

    // ─── FORGOT PASSWORD ────────────────────────────────────────────────────

    /**
     * Generates a password reset token for the given email.
     * Returns the bearer token for owner delivery only; never log or persist the raw value.
     */
    @Transactional
    public String generatePasswordResetToken(String email) {
        if (email == null) return null;
        Optional<User> userOpt = findByEmail(email).filter(User::isEnabled);
        if (userOpt.isEmpty()) return null;
        User user = userOpt.get();
        String token = PasswordResetTokens.generate();
        if (userRepository.issuePasswordReset(user.getId(), PasswordResetTokens.digest(token),
                LocalDateTime.now(ZoneOffset.UTC).plusMinutes(30)) != 1) return null;
        log.info("Password reset token issued for account id={}", user.getId());
        return token;
    }

    /**
     * Validates a password reset token.
     * Returns the User if token is valid and not expired, else empty.
     */
    public Optional<User> validatePasswordResetToken(String token) {
        String digest = PasswordResetTokens.digest(token);
        if (digest == null) return Optional.empty();
        return userRepository.findByPasswordResetToken(digest)
            .filter(u -> u.isEnabled() && u.getPasswordResetTokenExpiry() != null
                      && u.getPasswordResetTokenExpiry().isAfter(LocalDateTime.now(ZoneOffset.UTC)));
    }

    /**
     * Resets the password using the provided token.
     * Returns true if successful, false if token is invalid/expired.
     */
    @Transactional
    public boolean resetPasswordWithToken(String token, String newPassword) {
        PasswordPolicy.validate(newPassword);
        String digest = PasswordResetTokens.digest(token);
        if (digest == null) return false;
        // Password replacement and credential consumption are one conditional database write.
        // Expiry is checked after encoding so slow hashing cannot extend the acceptance window.
        String encoded = passwordEncoder.encode(newPassword);
        return userRepository.consumePasswordReset(digest, LocalDateTime.now(ZoneOffset.UTC), encoded) == 1;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        // Basic sanitization to prevent Stored XSS
        String normalized = value.trim()
            .replaceAll("<script.*?>.*?</script>", "")
            .replaceAll("<.*?\\s+on.*?>", "") // Remove inline event handlers
            .replaceAll("<[^>]*>", "");       // Strip all other HTML tags
            
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizePhone(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}

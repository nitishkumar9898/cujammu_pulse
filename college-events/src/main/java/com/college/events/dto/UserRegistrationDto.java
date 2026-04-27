package com.college.events.dto;

import com.college.events.model.Role;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class UserRegistrationDto {

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Please enter a valid email address")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).*$",
             message = "Password must contain at least one uppercase, one lowercase, one digit, and one special character")
    private String password;

    @NotBlank(message = "Please confirm your password")
    private String confirmPassword;

    @NotNull(message = "Role is required")
    private Role role;

    @NotBlank(message = "Department is required")
    private String department;

    @Pattern(regexp = "^$|^[+]?[0-9]{10,13}$", message = "Please enter a valid phone number")
    private String phone;

    /** Enrollment / Roll number (required for students, optional for organizers) */
    private String enrollmentNo;

    public boolean isPasswordMatching() {
        return password != null && password.equals(confirmPassword);
    }
}

package com.college.events.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@Data
public class EventDto {

    @NotBlank(message = "Event title is required")
    @Size(min = 3, max = 200, message = "Title must be between 3 and 200 characters")
    private String title;

    @NotBlank(message = "Description is required")
    @Size(min = 20, message = "Description must be at least 20 characters")
    private String description;

    @NotNull(message = "Category is required")
    private Long categoryId;

    @NotBlank(message = "Venue is required")
    private String venue;

    @NotNull(message = "Event date and time is required")
    @Future(message = "Event date must be in the future")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime eventDate;

    @NotNull(message = "Maximum seats is required")
    @Min(value = 1, message = "Must have at least 1 seat")
    @Max(value = 10000, message = "Maximum 10000 seats allowed")
    private Integer maxSeats;

    @Min(value = 0, message = "Fees cannot be negative")
    private Double fees = 0.0;

    @Email(message = "Please enter a valid contact email")
    private String contactEmail;

    private MultipartFile bannerImage;

    /** Target department: "ALL" or specific dept (e.g. "CSE") for smart email */
    private String targetDepartment = "ALL";

    private boolean generateCertificate = true;

    private MultipartFile certificateTemplate;
}

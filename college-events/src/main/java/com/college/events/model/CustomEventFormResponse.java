package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "custom_event_form_responses", uniqueConstraints = {
    @UniqueConstraint(name = "uk_event_user_response", columnNames = {"eventId", "userId"})
}, indexes = {
    @Index(name = "idx_cefr_event", columnList = "eventId")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomEventFormResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long eventId;

    @Column(nullable = false)
    private Long userId;

    // Auto-filled from User profile in database (and editable by participant)
    @Column(nullable = false, length = 100)
    private String participantName;

    @Column(nullable = false, length = 100)
    private String participantEmail;

    @Column(length = 50)
    private String participantRollNo;

    @Column(length = 80)
    private String participantDepartment;

    @Column(length = 30)
    private String participantSemester;

    // JSON map of custom questions and answers
    @Column(columnDefinition = "TEXT")
    private String responsesJson;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime submittedAt;
}

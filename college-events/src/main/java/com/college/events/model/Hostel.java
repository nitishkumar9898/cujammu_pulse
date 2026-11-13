package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "hostels", indexes = {
    @Index(name = "idx_hostel_name", columnList = "name", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Hostel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false, length = 20)
    private String genderType; // BOYS, GIRLS, CO_ED

    private String wardenName;

    private String wardenEmail;

    private String wardenPhone;

    @Column(length = 10)
    @Builder.Default
    private String curfewTime = "21:00"; // e.g. "19:30" or "21:00"

    private Integer capacity;

    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}

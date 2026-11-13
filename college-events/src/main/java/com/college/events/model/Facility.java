package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "facilities", indexes = {
    @Index(name = "idx_facility_name", columnList = "name", unique = true),
    @Index(name = "idx_facility_type", columnList = "facilityType")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Facility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false, length = 30)
    private String facilityType; // AUDITORIUM, SPORTS_GROUND, SEMINAR_HALL, LECTURE_THEATRE, LAB

    private String location;

    private Integer capacity;

    @Builder.Default
    private boolean hasProjector = false;

    @Builder.Default
    private boolean hasSoundSystem = false;

    @Builder.Default
    private boolean hasStageLighting = false;

    @Builder.Default
    private boolean active = true;

    private LocalDateTime maintenanceUntil;

    public boolean isUnderMaintenance() {
        return maintenanceUntil != null && maintenanceUntil.isAfter(java.time.LocalDateTime.now());
    }

    public void setMaintenanceUntil(LocalDateTime maintenanceUntil) { this.maintenanceUntil = maintenanceUntil; }
}

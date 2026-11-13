package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "custom_event_form_fields", indexes = {
    @Index(name = "idx_ceff_event", columnList = "eventId, fieldOrder")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomEventFormField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long eventId;

    @Column(nullable = false, length = 60)
    private String fieldName; // e.g. "tshirt_size"

    @Column(nullable = false, length = 100)
    private String fieldLabel; // e.g. "T-Shirt Size"

    @Column(length = 30, nullable = false)
    @Builder.Default
    private String fieldType = "TEXT"; // TEXT, NUMBER, DROPDOWN, CHECKBOX, TEXTAREA, URL

    @Column(length = 500)
    private String options; // Comma-separated or JSON e.g. "S,M,L,XL,XXL"

    @Builder.Default
    private boolean required = false;

    @Builder.Default
    private int fieldOrder = 1;

    private String placeholder;
}

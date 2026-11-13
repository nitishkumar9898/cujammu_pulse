package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "speakers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Speaker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String bio;

    private String profileImageUrl;
    private String linkedInUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private Event event;

    public String getSafeLinkedInUrl() { return com.college.events.security.WebUrlPolicy.safeHttpUrl(linkedInUrl); }
    public String getSafeProfileImageUrl() { return com.college.events.security.WebUrlPolicy.safeHttpUrl(profileImageUrl); }
}

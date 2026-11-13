package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sponsors")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Sponsor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String companyName;

    private String logoUrl;
    private String websiteUrl;

    @Column(length = 50)
    private String sponsorshipTier; // e.g. PLATINUM, GOLD, SILVER

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private Event event;

    public String getSafeWebsiteUrl() { return com.college.events.security.WebUrlPolicy.safeHttpUrl(websiteUrl); }
    public String getSafeLogoUrl() { return com.college.events.security.WebUrlPolicy.safeHttpUrl(logoUrl); }
}

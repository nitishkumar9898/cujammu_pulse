package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "lost_and_found_items", indexes = {
    @Index(name = "idx_laf_status", columnList = "status"),
    @Index(name = "idx_laf_category", columnList = "category")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LostAndFoundItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version @Column(nullable=false,columnDefinition="bigint default 0") @Builder.Default private long version = 0L;
    @Column(length=36,unique=true) private String requestKey;
    @Column(length=64) private String payloadHash;
    private Long acceptedClaimId;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 50, nullable = false)
    @Builder.Default
    private String category = "OTHER"; // ELECTRONICS, ID_CARDS_DOCS, BAGS_WALLETS, KEYS, CLOTHING, OTHER

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(nullable = false, length = 150)
    private String locationFound;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "reporter_id")
    private User reportedBy;

    private String reportedByName;
    private String reportedByPhone;

    @Column(length = 20, nullable = false)
    @Builder.Default
    private String status = "REPORTED"; // REPORTED, VERIFIED, CLAIMED, HANDED_OVER

    private String claimantName;
    private String claimantRollNo;
    private String claimantContact;

    private String imageUrl;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime reportedAt;

    private LocalDateTime claimedAt;
}

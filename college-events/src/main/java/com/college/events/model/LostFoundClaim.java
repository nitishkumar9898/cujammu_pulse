package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name="lost_found_claims", uniqueConstraints={
        @UniqueConstraint(name="uq_laf_claim_actor",columnNames={"item_id","claimant_id"}),
        @UniqueConstraint(name="uq_laf_claim_key",columnNames={"item_id","requestKey"})})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LostFoundClaim {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="item_id") private LostAndFoundItem item;
    @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="claimant_id") private User claimant;
    @Column(nullable=false,length=36) private String requestKey;
    @Column(nullable=false,length=64) private String payloadHash;
    @Column(nullable=false,length=2000) private String evidence;
    @Column(nullable=false,length=24) @Builder.Default private String status="PENDING";
    @Column(nullable=false) private LocalDateTime submittedAt;
    private LocalDateTime handedOverAt;
    private Long verifiedByUserId;
    @Column(length=500) private String verificationReason;
    @Column(length=36) private String handoverRequestKey;
    @Column(length=64) private String handoverPayloadHash;
}

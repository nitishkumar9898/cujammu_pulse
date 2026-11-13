package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name="proposal_attachment_receipts", uniqueConstraints=@UniqueConstraint(columnNames={"owner_id","request_key"}))
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class ProposalAttachmentReceipt {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="owner_id",nullable=false,updatable=false) private Long ownerId;
    @Column(name="request_key",nullable=false,updatable=false,length=36) private String requestKey;
    @Column(nullable=false,updatable=false) private Long eventId;
    @Column(nullable=false,updatable=false,length=24) private String kind;
    @Column(nullable=false,updatable=false,length=64) private String payloadHash;
    @Column(nullable=false,updatable=false,length=64) private String contentHash;
    @Column(nullable=false,updatable=false) private long resultingRevision;
    @Column(nullable=false,updatable=false) private Instant uploadAcceptedAt;
    @Column(nullable=false,updatable=false) private Instant expiresAt;
}

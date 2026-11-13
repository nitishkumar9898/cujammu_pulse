package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "support_ticket_entries", uniqueConstraints = @UniqueConstraint(name = "uq_ticket_entry_request", columnNames = {"ticket_id", "request_key"}),
       indexes = @Index(name = "idx_ticket_entry_order", columnList = "ticket_id,id"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class SupportTicketEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "ticket_id", nullable = false, updatable = false)
    private SupportTicket ticket;
    @Column(nullable = false, length = 36, updatable = false)
    private String publicId;
    @Column(name = "request_key", nullable = false, length = 36, updatable = false)
    private String requestKey;
    @Column(nullable = false, length = 64, updatable = false)
    private String requestDigest;
    @Column(nullable = false, updatable = false)
    private Long actorId;
    @Column(nullable = false, length = 30, updatable = false)
    private String kind;
    @Column(nullable = false, length = 20, updatable = false)
    private String authorLabel;
    @Column(nullable = false, length = 4000, updatable = false)
    private String body;
    @Column(nullable = false, updatable = false)
    private LocalDateTime occurredAt;
}

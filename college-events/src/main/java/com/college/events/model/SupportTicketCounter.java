package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "support_ticket_counters")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class SupportTicketCounter {
    @Id @Column(length = 6, updatable = false)
    private String monthKey;
    @Column(nullable = false)
    private long lastNumber;
}

package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.time.Instant;

@Entity
@Table(name = "poll_votes", uniqueConstraints = {
    @UniqueConstraint(name = "uk_poll_voter", columnNames = {"poll_id", "voter_id"})
}, indexes = {
    @Index(name = "idx_pv_poll", columnList = "poll_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PollVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "poll_id", nullable = false, updatable = false)
    private Long pollId;

    @Column(nullable = false, length = 100, updatable = false)
    private String selectedOption;

    @Column(name = "voter_id", nullable = false, updatable = false)
    private Long voterId;

    private String voterName;

    private String voterIp;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime votedAt;

    @Column(updatable = false)
    private Instant acceptedAt;
}

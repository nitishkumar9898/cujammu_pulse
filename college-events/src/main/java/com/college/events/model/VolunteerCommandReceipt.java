package com.college.events.model;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="volunteer_command_receipts", uniqueConstraints=@UniqueConstraint(name="uk_volunteer_receipt",columnNames={"event_id","actor_id","request_key"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VolunteerCommandReceipt {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="event_id",nullable=false) private Long eventId;
 @Column(name="actor_id",nullable=false) private Long actorId;
 @Column(name="request_key",nullable=false,length=36) private String requestKey;
 @Column(nullable=false,length=64) private String fingerprint;
 @Column(nullable=false,length=8000) private String responseJson;
}

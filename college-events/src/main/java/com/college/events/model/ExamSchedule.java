package com.college.events.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "exam_schedules", indexes = {
    @Index(name = "idx_es_dept_sem", columnList = "departmentName, semester")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String departmentName;

    @Column(nullable = false)
    private String programName;

    @Column(nullable = false, length = 20)
    private String semester;

    @Column(nullable = false, length = 20)
    private String academicYear;

    private String timetablePdfUrl;

    @Column(nullable = false)
    @Builder.Default
    private String publishedBy = "Controller of Examinations (CoE)";

    private LocalDate startDate;

    private LocalDate endDate;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime publishedAt;
}

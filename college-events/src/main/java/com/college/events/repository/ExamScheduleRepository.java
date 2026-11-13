package com.college.events.repository;

import com.college.events.model.ExamSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExamScheduleRepository extends JpaRepository<ExamSchedule, Long> {
    List<ExamSchedule> findByDepartmentNameOrderByPublishedAtDesc(String departmentName);
    List<ExamSchedule> findAllByOrderByPublishedAtDesc();
}

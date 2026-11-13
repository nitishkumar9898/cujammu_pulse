package com.college.events.repository;

import com.college.events.model.CustomEventFormField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomEventFormFieldRepository extends JpaRepository<CustomEventFormField, Long> {
    List<CustomEventFormField> findByEventIdOrderByFieldOrderAsc(Long eventId);
    void deleteByEventId(Long eventId);
}

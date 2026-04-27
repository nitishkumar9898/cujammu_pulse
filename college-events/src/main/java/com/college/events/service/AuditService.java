package com.college.events.service;

import com.college.events.model.AuditLog;
import com.college.events.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class AuditService {
    
    private final AuditLogRepository auditLogRepository;

    public void logAction(String userEmail, String role, String action, String details) {
        AuditLog log = AuditLog.builder()
            .userEmail(userEmail)
            .role(role)
            .action(action)
            .details(details)
            .build();
        auditLogRepository.save(log);
    }
}

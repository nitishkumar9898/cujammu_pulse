package com.college.events.service;

import com.college.events.model.AuditLog;
import com.college.events.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import com.college.events.dto.FeatureControlAuditDtos;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class AuditService {
    
    private final AuditLogRepository auditLogRepository;
    private static final Pattern FEATURE = Pattern.compile("feature=([A-Za-z0-9_]+)");
    private static final Pattern ENABLED = Pattern.compile("enabled=(true|false)");
    private static final Pattern REASON = Pattern.compile("reason=([^;]+)");

    public void logAction(String userEmail, String role, String action, String details) {
        AuditLog log = AuditLog.builder()
            .userEmail(userEmail)
            .role(role)
            .action(action)
            .details(details)
            .build();
        auditLogRepository.save(log);
    }

    /** Redacted, bounded control-plane history; actor email/details never leave the service. */
    public FeatureControlAuditDtos.PageView featureControlHistory(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid audit page");
        var result = auditLogRepository.findByActionOrderByTimestampDesc("FEATURE_STATE_CHANGED", PageRequest.of(page, size));
        return new FeatureControlAuditDtos.PageView(result.getContent().stream().map(log -> {
            String details = log.getDetails() == null ? "" : log.getDetails();
            String feature = match(FEATURE, details, "UNKNOWN");
            boolean enabled = Boolean.parseBoolean(match(ENABLED, details, "false"));
            String reason = match(REASON, details, "");
            return new FeatureControlAuditDtos.Item(log.getId(), log.getRole(), feature, enabled,
                    reason.length() > 300 ? reason.substring(0, 300) : reason, log.getTimestamp());
        }).toList(), page, result.hasNext());
    }

    private String match(Pattern pattern, String value, String fallback) {
        var matcher = pattern.matcher(value);
        return matcher.find() ? matcher.group(1) : fallback;
    }
}

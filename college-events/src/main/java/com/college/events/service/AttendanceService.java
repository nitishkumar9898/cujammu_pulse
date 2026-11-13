package com.college.events.service;

import com.college.events.model.User;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Compatibility guard for the retired event bearer-token attendance path.
 * Event attendance must use the authorized operations ledger, never a raw token.
 */
@Service
public class AttendanceService {

    public Map<String, Object> markAttendance(String ignoredCredential, Long eventId, User ignoredOperator) {
        return Map.of(
            "success", false,
            "code", "LEGACY_EVENT_ATTENDANCE_DISABLED",
            "message", "Legacy event QR attendance is disabled."
        );
    }
}

package com.college.events.service;

import com.college.events.model.DualAttendance;
import org.springframework.stereotype.Service;

import java.util.List;

/** Compatibility guard for retired identifier-based event attendance methods. */
@Service
public class DualAttendanceService {

    public DualAttendance recordEntryScan(Long eventId, String ignoredIdentifier, String ignoredScanner) {
        throw new IllegalStateException("Legacy identifier-based attendance is disabled.");
    }

    public DualAttendance recordExitScan(Long eventId, String ignoredIdentifier, String ignoredScanner) {
        throw new IllegalStateException("Legacy identifier-based attendance is disabled.");
    }

    public List<DualAttendance> getEventAttendance(Long eventId) {
        throw new IllegalStateException("Legacy attendance reports are disabled.");
    }

    public byte[] exportAttendanceCsv(Long eventId) {
        throw new IllegalStateException("Legacy attendance exports are disabled.");
    }
}

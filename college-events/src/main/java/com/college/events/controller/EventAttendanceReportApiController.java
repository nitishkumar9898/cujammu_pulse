package com.college.events.controller;

import com.college.events.service.EventAttendanceReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Event-manager-scoped reports; QR secrets and private contacts are excluded by the service. */
@RestController
@RequestMapping("/api/v1/cujammu/events/{eventId}/attendance")
@RequiredArgsConstructor
public class EventAttendanceReportApiController {
    private final EventAttendanceReportService reports;

    @GetMapping(value = "/report.csv", produces = "text/csv")
    public ResponseEntity<byte[]> csv(@PathVariable long eventId) throws java.io.IOException {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=event-" + eventId + "-attendance.csv")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(reports.csv(eventId));
    }

    @GetMapping(value = "/report.xlsx", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> xlsx(@PathVariable long eventId) throws java.io.IOException {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=event-" + eventId + "-attendance.xlsx")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(reports.xlsx(eventId));
    }

    @GetMapping(value = "/scan-log.csv", produces = "text/csv")
    public ResponseEntity<byte[]> scanLog(@PathVariable long eventId) throws java.io.IOException {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=event-" + eventId + "-attendance-scan-log.csv")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(reports.scanLogCsv(eventId));
    }
}

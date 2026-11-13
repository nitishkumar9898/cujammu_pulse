package com.college.events.controller;

import com.college.events.dto.EventAttendanceDtos;
import com.college.events.service.EventQrAttendanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Online event attendance routes; no raw entity or QR secret is exposed by read APIs. */
@RestController
@RequestMapping("/api/v1/cujammu/events")
@RequiredArgsConstructor
public class EventQrAttendanceApiController {
    private final EventQrAttendanceService attendance;

    @PostMapping("/{eventId}/registrations/{registrationId}/credential")
    public EventAttendanceDtos.CredentialView issue(@PathVariable long eventId, @PathVariable long registrationId,
                                                    @Valid @RequestBody EventAttendanceDtos.IssueCredential command) {
        return attendance.issueCredential(eventId, registrationId, command);
    }

    @PostMapping("/{eventId}/attendance/scan-intents")
    public EventAttendanceDtos.IntentCreated createIntent(@PathVariable long eventId,
                                                          @Valid @RequestBody EventAttendanceDtos.CreateIntent command) {
        return attendance.createIntent(eventId, command);
    }

    @GetMapping("/attendance/scan-intents/pending")
    public EventAttendanceDtos.PendingIntents pending() { return attendance.pendingForOwner(); }

    @PostMapping("/attendance/scan-intents/{intentKey}/confirm")
    public EventAttendanceDtos.Receipt confirm(@PathVariable String intentKey,
                                               @Valid @RequestBody EventAttendanceDtos.ConfirmIntent command) {
        return attendance.confirm(intentKey, command);
    }
}

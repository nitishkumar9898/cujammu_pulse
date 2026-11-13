package com.college.events.controller;

import com.college.events.dto.EventOperationsDtos.*;
import com.college.events.service.EventOperationsService;
import com.college.events.service.EventScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Explicit DTO-only APIs for the approved-event operations workspace. */
@RestController
@RequestMapping("/api/v1/cujammu/events/{eventId}")
@RequiredArgsConstructor
public class EventOperationsApiController {
    private final EventOperationsService operations;
    private final EventScheduleService schedule;
    @GetMapping("/lifecycle/availability-suggestions") public com.college.events.dto.FacilityAvailabilityDtos.Suggestions suggestions(
            @PathVariable long eventId, @RequestParam(defaultValue = "60") int durationMinutes,
            @RequestParam(defaultValue = "14") int days) {
        return schedule.suggestReschedule(eventId, durationMinutes, days);
    }
    @PostMapping("/lifecycle/cancel") public void cancel(@PathVariable long eventId, @Valid @RequestBody(required = false) Cancel command) {
        schedule.cancel(eventId, command == null ? null : command.requestKey(), command == null ? null : command.expectedRevision());
    }
    @GetMapping("/lifecycle/history") public java.util.List<ScheduleChangeView> history(@PathVariable long eventId) {
        return schedule.history(eventId);
    }
    @PostMapping("/lifecycle/postpone") public void postpone(@PathVariable long eventId, @Valid @RequestBody Postpone command) {
        schedule.reschedule(eventId, command.newDate(), command.reason().trim(), command.requestKey(), command.expectedRevision());
    }
    @GetMapping("/operations") public OperationsView view(@PathVariable long eventId) { return operations.view(eventId); }
    @PutMapping("/operations") public OperationsView configure(@PathVariable long eventId, @Valid @RequestBody Configure command) { return operations.configure(eventId, command); }
    @PutMapping("/volunteer-recruitment") public OperationsView recruitment(@PathVariable long eventId, @Valid @RequestBody Recruitment command) { return operations.recruitment(eventId, command); }
    @PostMapping("/volunteer-applications") public VolunteerView apply(@PathVariable long eventId, @RequestBody(required = false) Apply ignored) { return operations.apply(eventId); }
    @GetMapping("/volunteer-applications") public VolunteerList volunteers(@PathVariable long eventId) { return operations.volunteers(eventId); }
    @PutMapping("/volunteer-applications/{membershipId}/decision") public VolunteerView decide(@PathVariable long eventId,
            @PathVariable long membershipId, @Valid @RequestBody Decision command) { return operations.decide(eventId, membershipId, command); }
    @PutMapping("/scanner-control") public OperationsView scanner(@PathVariable long eventId, @Valid @RequestBody ScannerControl command) { return operations.scannerControl(eventId, command); }
    @PutMapping("/scanner-grants/{userId}") public GrantView scannerGrant(@PathVariable long eventId, @PathVariable long userId,
            @Valid @RequestBody Delegate command) { return operations.delegateScanner(eventId, userId, command); }
    @PutMapping("/manual-grants/{userId}") public GrantView manualGrant(@PathVariable long eventId, @PathVariable long userId,
            @Valid @RequestBody ManualDelegate command) { return operations.delegateManual(eventId, userId, command); }
    @GetMapping("/manual-grants") public GrantList manualGrants(@PathVariable long eventId) { return operations.manualGrants(eventId); }
    @GetMapping("/attendance-export-grants") public GrantList attendanceExportGrants(@PathVariable long eventId) { return operations.attendanceExportGrants(eventId); }
    @PutMapping("/attendance-export-grants/{userId}") public GrantView attendanceExportGrant(@PathVariable long eventId, @PathVariable long userId,
            @Valid @RequestBody ExportDelegate command) { return operations.delegateAttendanceExport(eventId, userId, command); }
    @PutMapping("/scanner-grants/{userId}/revoke") public void revokeScanner(@PathVariable long eventId, @PathVariable long userId,
            @Valid @RequestBody Revoke command) { operations.revoke(eventId, userId, "QR", command.expectedRevision()); }
    @PutMapping("/manual-grants/{userId}/revoke") public void revokeManual(@PathVariable long eventId, @PathVariable long userId,
            @Valid @RequestBody Revoke command) { operations.revoke(eventId, userId, "MANUAL", command.expectedRevision()); }
    @PutMapping("/attendance-export-grants/{userId}/revoke") public void revokeAttendanceExport(@PathVariable long eventId, @PathVariable long userId,
            @Valid @RequestBody Revoke command) { operations.revoke(eventId, userId, "EXPORT", command.expectedRevision()); }
    @PutMapping("/manual-window") public OperationsView manualWindow(@PathVariable long eventId, @Valid @RequestBody ManualWindow command) { return operations.manualWindow(eventId, command); }
    @GetMapping("/my-operator-capabilities") public CapabilityView capabilities(@PathVariable long eventId) { return operations.myCapabilities(eventId); }
    @PostMapping("/attendance/manual-entry") public ManualCheckInReceipt manualEntry(@PathVariable long eventId,
            @Valid @RequestBody ManualCheckIn command) { return operations.manualCheckIn(eventId, command); }
}

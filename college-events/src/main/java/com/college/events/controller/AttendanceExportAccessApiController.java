package com.college.events.controller;

import com.college.events.dto.EventOperationsDtos.EventSummaryPage;
import com.college.events.service.EventOperationsService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only discovery for event-scoped report grants; it exposes no event-management controls. */
@RestController
@RequestMapping("/api/v1/cujammu/events/attendance-export-access")
@RequiredArgsConstructor
@Validated
public class AttendanceExportAccessApiController {
    private final EventOperationsService operations;

    @GetMapping("/mine")
    public EventSummaryPage mine(@RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page) {
        return operations.myAttendanceExportEvents(page);
    }
}

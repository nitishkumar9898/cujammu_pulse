package com.college.events.controller;

import com.college.events.dto.EventOperationsDtos.EventSummaries;
import com.college.events.service.EventOperationsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cujammu/events/operations")
@RequiredArgsConstructor
public class EventOperationsDirectoryApiController {
    private final EventOperationsService operations;
    @GetMapping("/mine") public EventSummaries mine() { return operations.myEvents(); }
    @GetMapping("/my-operator-events") public EventSummaries myOperatorEvents() { return operations.myOperatorEvents(); }
    @GetMapping("/volunteer-opportunities") public EventSummaries volunteerOpportunities() { return operations.volunteerOpportunities(); }
    @GetMapping("/my-volunteer-applications")
    public org.springframework.http.ResponseEntity<com.college.events.dto.EventOperationsDtos.OwnVolunteerApplicationPage> myVolunteerApplications(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int size) {
        return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore().cachePrivate())
                .body(operations.myVolunteerApplications(page, size));
    }
}

package com.college.events.controller;

import com.college.events.model.Event;
import com.college.events.model.Registration;
import com.college.events.security.DomainAccess;
import com.college.events.service.EventService;
import com.college.events.service.RegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/cujammu/events")
@RequiredArgsConstructor
public class EventCatalogApiController {
    private final EventService events;
    private final RegistrationService registrations;
    private final DomainAccess access;
    private final com.college.events.service.CalendarClashService calendarClashes;

    @GetMapping("/catalog")
    public ResponseEntity<?> catalog() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(events.findUpcomingApproved().stream().map(this::eventView).toList());
    }

    @PostMapping("/{eventId}/registrations")
    public ResponseEntity<?> register(@PathVariable Long eventId) {
        Registration registration = registrations.register(access.currentUser(), eventId);
        return ResponseEntity.ok(registrationView(registration));
    }

    @GetMapping("/registrations/mine")
    public ResponseEntity<?> mine() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(registrations.findByUser(access.currentUser()).stream().map(this::registrationView).toList());
    }

    @GetMapping("/calendar/mine")
    public ResponseEntity<com.college.events.dto.CalendarDtos.Page> calendar(
            @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(registrations.myCalendar(access.currentUser(), page));
    }

    @PostMapping("/registrations/{registrationId}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable Long registrationId) {
        registrations.cancelRegistration(registrationId, access.currentUser());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/calendar/clashes/{eventId}")
    public ResponseEntity<com.college.events.dto.CalendarDtos.ClashCheck> clashes(@PathVariable long eventId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(calendarClashes.check(eventId));
    }

    private Map<String, Object> eventView(Event event) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", event.getId()); view.put("title", event.getTitle()); view.put("description", event.getDescription());
        view.put("venue", event.getVenue()); view.put("eventDate", event.getEventDate()); view.put("maxSeats", event.getMaxSeats());
        view.put("status", event.getStatus()); view.put("targetDepartment", event.getTargetDepartment());
        return view;
    }

    private Map<String, Object> registrationView(Registration registration) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", registration.getId()); view.put("eventId", registration.getEvent().getId());
        view.put("status", registration.getStatus()); view.put("registeredAt", registration.getRegisteredAt());
        return view;
    }
}

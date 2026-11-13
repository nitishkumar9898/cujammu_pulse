package com.college.events.service;

import com.college.events.dto.CalendarDtos;
import com.college.events.model.EventStatus;
import com.college.events.repository.EventRepository;
import com.college.events.repository.RegistrationRepository;
import com.college.events.security.DomainAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Private, bounded advisory read. It neither reserves a seat nor authorizes registration. */
@Service
@RequiredArgsConstructor
public class CalendarClashService {
    private final DomainAccess access;
    private final FeatureFlagService features;
    private final EventRepository events;
    private final RegistrationRepository registrations;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CalendarDtos.ClashCheck check(long eventId) {
        var actor = access.currentUser();
        features.requireEnabledForRead("MODULE_EVENTS");
        features.requireEnabledForRead("MODULE_CALENDAR");
        features.requireEnabledForRead("ACTION_CALENDAR_CLASH_CHECK");
        var event = events.findById(eventId)
                .filter(value -> value.getStatus() == EventStatus.APPROVED && value.getEventDate() != null
                        && value.getEventDate().isAfter(LocalDateTime.now(ZoneId.of("Asia/Kolkata"))))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event is not available for a clash check"));
        var end = CalendarDtos.validEnd(event.getEventDate(), event.getEventEndsAt());
        var slice = registrations.findOwnClashCandidates(actor.getId(), eventId, event.getEventDate(),
                end == null ? event.getEventDate() : end, end == null, PageRequest.of(0, 50));
        var items = slice.getContent().stream().map(registration -> {
            var entry = CalendarDtos.entry(registration);
            return new CalendarDtos.Clash(entry, end == null || entry.eventEndsAt() == null ? "UNKNOWN_DURATION" : "OVERLAP");
        }).toList();
        return new CalendarDtos.ClashCheck(eventId, event.getEventDate(), end, event.getLifecycleVersion(),
                Instant.now(), items, slice.hasNext());
    }
}

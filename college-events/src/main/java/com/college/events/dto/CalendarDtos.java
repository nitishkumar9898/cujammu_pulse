package com.college.events.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.time.Instant;
import com.college.events.model.Registration;

public final class CalendarDtos {
    private CalendarDtos() {}

    public record Entry(long registrationId, long eventId, String title, LocalDateTime eventDate,
                        String venue, String status, LocalDateTime eventEndsAt, String eventStatus,
                        long lifecycleVersion) {}
    public record Page(List<Entry> items, int page, boolean hasMore) {}
    public record Clash(Entry entry, String assessment) {}
    public record ClashCheck(long eventId, LocalDateTime startsAt, LocalDateTime endsAt,
                             long lifecycleVersion, Instant checkedAt, List<Clash> items, boolean hasMore) {}

    public static Entry entry(Registration registration) {
        var event = registration.getEvent();
        return new Entry(registration.getId(), event.getId(), event.getTitle(), event.getEventDate(),
                event.getVenue(), registration.getStatus().name(), validEnd(event.getEventDate(), event.getEventEndsAt()),
                event.getStatus().name(), event.getLifecycleVersion());
    }

    /** Historical rows without a valid interval must remain explicitly unknown. */
    public static LocalDateTime validEnd(LocalDateTime start, LocalDateTime end) {
        return start != null && end != null && end.isAfter(start) ? end : null;
    }
}

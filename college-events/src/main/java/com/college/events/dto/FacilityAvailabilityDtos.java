package com.college.events.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Reservation-gap hints only; they are never a hold or a booking confirmation. */
public final class FacilityAvailabilityDtos {
    private FacilityAvailabilityDtos() {}

    public record Suggestions(long facilityId, String facilityName, int durationMinutes,
                              LocalDateTime searchedFrom, LocalDateTime searchedUntil,
                              LocalDateTime computedAt, String status,
                              List<String> checkedConstraints, List<String> unknownConstraints,
                              List<Slot> items) {}

    public record Slot(LocalDateTime startTime, LocalDateTime endTime) {}
}

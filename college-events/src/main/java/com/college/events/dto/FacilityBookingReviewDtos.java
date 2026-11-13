package com.college.events.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public final class FacilityBookingReviewDtos {
    private FacilityBookingReviewDtos() {}

    public record Pending(long id, long version, long facilityId, String facilityName, String title,
                          String requesterName, String requesterDepartment, LocalDateTime startTime,
                          LocalDateTime endTime, int requestedMics, boolean requestedProjector) {}

    public record PendingPage(List<Pending> items, int page, boolean hasNext) {}

    public record Mine(long id, long version, long facilityId, String facilityName, String title, String status,
                       LocalDateTime startTime, LocalDateTime endTime) {}

    public record MinePage(List<Mine> items, int page, boolean hasNext) {}

    public record Decision(@NotNull Long expectedVersion, @NotBlank String decision,
                           @Size(max = 300) String reason) {}

    public record Cancel(@NotNull Long expectedVersion, @NotBlank @Size(max = 300) String reason) {}

    public record Receipt(long id, long facilityId, long version, String status,
                          LocalDateTime startTime, LocalDateTime endTime) {}
}

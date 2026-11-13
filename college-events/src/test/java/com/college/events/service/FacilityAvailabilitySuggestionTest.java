package com.college.events.service;

import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FacilityAvailabilitySuggestionTest {
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final HostelRepository hostels = mock(HostelRepository.class);
    private final HostelGatePassRepository passes = mock(HostelGatePassRepository.class);
    private final FacilityRepository facilities = mock(FacilityRepository.class);
    private final FacilityBookingRepository bookings = mock(FacilityBookingRepository.class);
    private final DomainAccess access = mock(DomainAccess.class);
    private final AuditService audit = mock(AuditService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final CUJammuMasterService service = new CUJammuMasterService(departments, hostels, passes, facilities, bookings, access, audit, notifications);

    @Test
    void suggestionsExcludeKnownOverlapsAndDiscloseUncheckedConstraints() {
        when(access.currentUser()).thenReturn(User.builder().id(8L).build());
        Facility hall = Facility.builder().id(4L).name("Synthetic Hall").active(true).build();
        when(facilities.findById(4L)).thenReturn(Optional.of(hall));
        LocalDateTime start = LocalDateTime.now(ZoneId.of("Asia/Kolkata")).plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0);
        FacilityBooking reserved = FacilityBooking.builder().facility(hall).startTime(start).endTime(start.plusHours(2)).status("CONFIRMED").build();
        when(bookings.findConflictingBookings(eq(4L), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(List.of(reserved));

        var result = service.suggestFacilitySlots(4L, 60, 3);

        assertEquals("ADVISORY_ONLY", result.status());
        assertTrue(result.items().stream().noneMatch(slot -> slot.startTime().isBefore(reserved.getEndTime())
                && slot.endTime().isAfter(reserved.getStartTime())));
        assertTrue(result.unknownConstraints().contains("Opening hours"));
        assertTrue(result.items().size() <= 20);
        verify(bookings).findConflictingBookings(eq(4L), any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    void invalidDurationAndSearchBoundsFailBeforeRepositoryRead() {
        when(access.currentUser()).thenReturn(User.builder().id(8L).build());
        assertThrows(IllegalArgumentException.class, () -> service.suggestFacilitySlots(4L, 10, 3));
        assertThrows(IllegalArgumentException.class, () -> service.suggestFacilitySlots(4L, 60, 31));
        verifyNoInteractions(facilities, bookings);
    }

    @Test
    void disabledVenueFeatureFailsClosedBeforeFacilityReads() {
        FeatureFlagService flags = mock(FeatureFlagService.class);
        when(flags.isFeatureEnabled("MODULE_VENUES")).thenReturn(false);
        CUJammuMasterService guarded = new CUJammuMasterService(departments, hostels, passes, facilities,
                bookings, access, audit, notifications, flags);

        assertThrows(IllegalStateException.class, guarded::getAllActiveFacilities);
        when(access.currentUser()).thenReturn(User.builder().id(8L).build());
        assertThrows(IllegalStateException.class, () -> guarded.suggestFacilitySlots(4L, 60, 3));
        verifyNoInteractions(facilities, bookings);
    }
}

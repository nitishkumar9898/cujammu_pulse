package com.college.events.controller;

import com.college.events.model.Event;
import com.college.events.model.EventStatus;
import com.college.events.model.FacilityBooking;
import com.college.events.repository.FacilityBookingRepository;
import com.college.events.repository.VolunteerMembershipRepository;
import com.college.events.repository.VolunteerTaskRepository;
import com.college.events.security.DomainAccess;
import com.college.events.service.EventOperationsService;
import com.college.events.service.FeatureFlagService;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReadinessApiControllerTest {
    private final FacilityBookingRepository bookings = mock(FacilityBookingRepository.class);
    private final EventOperationsService operations = mock(EventOperationsService.class);
    private final DomainAccess access = mock(DomainAccess.class);
    private final FeatureFlagService flags = mock(FeatureFlagService.class);
    private final VolunteerMembershipRepository memberships = mock(VolunteerMembershipRepository.class);
    private final VolunteerTaskRepository volunteerTasks = mock(VolunteerTaskRepository.class);
    private final ReadinessApiController controller = new ReadinessApiController(bookings, operations, access, flags, memberships, volunteerTasks);

    @Test
    void unconfirmedResourceEvidenceKeepsReadinessFalse() {
        Event event = Event.builder().status(EventStatus.APPROVED).build();
        when(access.requireEventManager(8L)).thenReturn(event);
        when(bookings.findActiveByEventIdOrderByStartTimeAsc(8L)).thenReturn(List.of());
        when(volunteerTasks.countByEventIdAndStatus(8L, "PENDING")).thenReturn(0L);
        when(volunteerTasks.countByEventIdAndStatusIn(8L, List.of("PENDING", "ASSIGNED", "IN_PROGRESS"))).thenReturn(0L);

        var result = controller.view(8L);

        assertFalse(result.ready());
        var resource = result.checks().stream().filter(c -> c.key().equals("RESOURCE_CONFIRMATION")).findFirst().orElseThrow();
        assertFalse(resource.passed());
        assertEquals("No active linked facility booking evidence", resource.detail());
    }

    @Test
    void volunteerRosterCountsAreInformationalAndNeverPassCoverage() {
        Event event = Event.builder().status(EventStatus.APPROVED).build();
        when(access.requireEventManager(11L)).thenReturn(event);
        when(bookings.findActiveByEventIdOrderByStartTimeAsc(11L)).thenReturn(List.of());
        when(memberships.countEligibleAcceptedByEventId(11L)).thenReturn(3L);
        when(volunteerTasks.countByEventIdAndStatus(11L, "PENDING")).thenReturn(0L);
        when(volunteerTasks.countByEventIdAndStatusIn(11L, List.of("PENDING", "ASSIGNED", "IN_PROGRESS"))).thenReturn(0L);

        var result = controller.view(11L);

        var coverage = result.checks().stream().filter(c -> c.key().equals("VOLUNTEER_COVERAGE")).findFirst().orElseThrow();
        assertFalse(coverage.passed());
        assertTrue(coverage.detail().contains("3 accepted verified volunteer(s)"));
        assertTrue(coverage.detail().contains("No approved staffing/referee target"));
        assertFalse(result.ready());
    }

    @Test
    void pendingFacilityRequestDoesNotCountAsConfirmedCapacity() {
        Event event = Event.builder().status(EventStatus.APPROVED).build();
        FacilityBooking pending = FacilityBooking.builder().status("PENDING").build();
        when(access.requireEventManager(9L)).thenReturn(event);
        when(bookings.findActiveByEventIdOrderByStartTimeAsc(9L)).thenReturn(List.of(pending));

        var result = controller.view(9L);

        var resource = result.checks().stream().filter(c -> c.key().equals("RESOURCE_CONFIRMATION")).findFirst().orElseThrow();
        assertFalse(resource.passed());
        assertEquals("At least one facility booking awaits review", resource.detail());
    }

    @Test
    void partialPositiveChecksCannotCertifyFullReadiness() {
        Event event = Event.builder().status(EventStatus.APPROVED).build();
        when(access.requireEventManager(10L)).thenReturn(event);
        when(operations.view(10L)).thenReturn(new com.college.events.dto.EventOperationsDtos.OperationsView(
                0, "DUAL_QR", "ENTRY_ONLY", false, 10, false, "OFF", 0, 0,
                null, null, false, null, null, "UNREVIEWED", 0));
        when(bookings.findActiveByEventIdOrderByStartTimeAsc(10L)).thenReturn(List.of(FacilityBooking.builder().status("CONFIRMED").build()));

        var result = controller.view(10L);

        assertFalse(result.ready());
        var scope = result.checks().stream().filter(c -> c.key().equals("READINESS_SCOPE")).findFirst().orElseThrow();
        assertFalse(scope.passed());
        assertTrue(scope.detail().contains("cannot certify"));
    }
}

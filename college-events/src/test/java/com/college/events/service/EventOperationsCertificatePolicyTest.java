package com.college.events.service;
import com.college.events.dto.EventOperationsDtos;
import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventOperationsCertificatePolicyTest {
    @Test void policyMustBeExplicitCompatibleAndFrozenBeforeRegistration() {
        var events = mock(EventRepository.class); var operations = mock(EventOperationsRepository.class);
        var registrations = mock(RegistrationRepository.class);
        Event event = Event.builder().id(2L).status(EventStatus.APPROVED).build();
        EventOperations policy = EventOperations.builder().event(event).attendanceMode("SINGLE_QR").singleQrPolicy("ENTRY_AND_ATTENDANCE").build();
        when(operations.findForUpdate(2L)).thenReturn(Optional.of(policy));
        // Audit identity is looked up after committing the policy intent.
        DomainAccess access = mock(DomainAccess.class);
        when(access.currentUser()).thenReturn(User.builder().id(1L).email("synthetic@example.invalid").role(Role.ORGANIZER).build());
        var service = new EventOperationsService(events, operations, mock(VolunteerMembershipRepository.class), mock(EventOperatorGrantRepository.class),
                registrations, mock(ManualAttendanceEvidenceRepository.class), mock(UserRepository.class), access, mock(AuditService.class), mock(NotificationService.class), mock(FeatureFlagService.class));
        assertEquals("UNREVIEWED", service.configure(2L, new EventOperationsDtos.Configure(0, "SINGLE_QR", "ENTRY_AND_ATTENDANCE")).certificateEvidencePolicy());
        assertThrows(IllegalArgumentException.class, () -> service.configure(2L, new EventOperationsDtos.Configure(0, "SINGLE_QR", "ENTRY_ONLY", "POINT_ATTENDANCE")));
        var published = service.configure(2L, new EventOperationsDtos.Configure(0, "SINGLE_QR", "ENTRY_AND_ATTENDANCE", "POINT_ATTENDANCE"));
        assertEquals("POINT_ATTENDANCE", published.certificateEvidencePolicy()); assertEquals(1, published.certificatePolicyVersion());
        when(registrations.existsByEvent(event)).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> service.configure(2L, new EventOperationsDtos.Configure(0, "SINGLE_QR", "ENTRY_AND_ATTENDANCE", "UNREVIEWED")));
        assertEquals("POINT_ATTENDANCE", policy.getCertificateEvidencePolicy());
    }
}

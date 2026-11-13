package com.college.events.service;

import com.college.events.dto.HostelPassViews;
import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HostelPassPolicyTest {
    HostelGatePassRepository passes = mock(HostelGatePassRepository.class);
    HostelRepository hostels = mock(HostelRepository.class);
    DomainAccess access = mock(DomainAccess.class);
    CUJammuMasterService service = new CUJammuMasterService(mock(DepartmentRepository.class), hostels, passes,
            mock(FacilityRepository.class), mock(FacilityBookingRepository.class), access,
            mock(AuditService.class), mock(NotificationService.class));

    @Test void foreignWardenCannotApprovePass() {
        User warden = User.builder().id(2L).email("other@example.invalid").role(Role.WARDEN).build();
        when(access.requireRole(any(Role[].class))).thenReturn(warden);
        when(passes.findForDecision(1L)).thenReturn(Optional.of(pass("PENDING")));
        assertThrows(AccessDeniedException.class, () -> service.wardenApprovePass(1L, "assigned@example.invalid", true, null));
        verify(passes, never()).save(any());
    }

    @Test void disabledHostelModuleClosesLegacyReadRouteBeforeRepositoryAccess() {
        FeatureFlagService flags = mock(FeatureFlagService.class);
        when(flags.isFeatureEnabled("MODULE_HOSTEL_GATEPASS")).thenReturn(false);
        CUJammuMasterService guarded = new CUJammuMasterService(mock(DepartmentRepository.class), hostels, passes,
                mock(FacilityRepository.class), mock(FacilityBookingRepository.class), access,
                mock(AuditService.class), mock(NotificationService.class), flags);

        assertThrows(IllegalStateException.class, guarded::ownGatePasses);
        verifyNoInteractions(passes);
    }

    @Test void pendingOrRejectedPassCannotBeScannedForDeparture() {
        staff();
        for (String state : new String[]{"PENDING", "REJECTED", "RETURNED_ON_TIME", "LATE_ENTRY"}) {
            when(passes.findByQrToken("token")).thenReturn(Optional.of(pass(state)));
            assertThrows(IllegalStateException.class, () -> service.recordGateScan("token", true));
        }
        verify(passes, never()).save(any());
    }

    @Test void rejectionRequiresAReasonBeforeThePassIsLoadedOrChanged() {
        User warden = User.builder().id(2L).email("assigned@example.invalid").role(Role.WARDEN).build();
        when(access.requireRole(any(Role[].class))).thenReturn(warden);
        assertThrows(IllegalArgumentException.class, () -> service.wardenApprovePass(1L, warden.getEmail(), false, "  "));
        verifyNoInteractions(passes);
    }

    @Test void returnRequiresDepartureAndDuplicateReturnPreservesTimestamp() {
        staff();
        when(passes.findByQrToken("token")).thenReturn(Optional.of(pass("APPROVED")));
        assertThrows(IllegalStateException.class, () -> service.recordGateScan("token", false));
        var completed = pass("RETURNED_ON_TIME");
        LocalDateTime original = now().minusHours(2); completed.setActualReturnTime(original);
        when(passes.findByQrToken("token")).thenReturn(Optional.of(completed));
        assertEquals(original, service.recordGateScan("token", false).getActualReturnTime());
        verify(passes, never()).save(any());
    }

    @Test void repeatApprovalDoesNotRotateIssuedCredential() {
        User warden = User.builder().id(2L).email("assigned@example.invalid").role(Role.WARDEN).build();
        when(access.requireRole(any(Role[].class))).thenReturn(warden);
        var approved = pass("APPROVED"); approved.setWardenApprovedBy(warden.getEmail()); approved.setQrToken("existing");
        when(passes.findForDecision(1L)).thenReturn(Optional.of(approved));
        assertEquals("existing", service.wardenApprovePass(1L, "forged@example.invalid", true, null).getQrToken());
        verify(passes, never()).save(any());
    }

    @Test void pendingPassHasNoOwnerCredentialAndScanReceiptHasNoSensitiveFields() throws Exception {
        var pending = pass("PENDING"); pending.setQrToken("legacy-token");
        String ownJson = new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .writeValueAsString(HostelPassViews.OwnPass.from(pending));
        assertFalse(ownJson.contains("qrToken"));
        assertFalse(ownJson.contains("legacy-token"));
        String json = new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .writeValueAsString(HostelPassViews.ScanReceipt.from(pending));
        assertFalse(json.contains("reason")); assertFalse(json.contains("qrToken"));
        assertFalse(json.contains("email")); assertFalse(json.contains("password"));
    }

    @Test void studentCannotApplyForAnotherHostel() {
        User student = User.builder().id(1L).role(Role.STUDENT).hostelName("Different hostel").build();
        when(access.requireSelf(1L)).thenReturn(student);
        when(hostels.findById(1L)).thenReturn(Optional.of(pass("PENDING").getHostel()));
        assertThrows(AccessDeniedException.class, () -> service.applyHostelGatePass(student, 1L, "DAY_OUT",
                "Synthetic destination", "Synthetic purpose", now(), now().plusHours(1)));
        verifyNoInteractions(passes);
    }

    private void staff() {
        when(access.requireRole(any(Role[].class))).thenReturn(User.builder().id(2L).role(Role.STAFF).hostelName("Synthetic hostel").build());
    }
    private HostelGatePass pass(String state) {
        return HostelGatePass.builder().id(1L).status(state)
                .user(User.builder().id(1L).name("Synthetic student").enrollmentNo("0001").build())
                .hostel(Hostel.builder().name("Synthetic hostel").wardenEmail("assigned@example.invalid").active(true).build())
                .expectedReturnTime(now().plusHours(1)).build();
    }
    private LocalDateTime now() { return LocalDateTime.now(ZoneId.of("Asia/Kolkata")); }
}

package com.college.events.service;

import com.college.events.dto.HostelPassViews;
import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class HostelGateCredentialServiceTest {
    @Test
    void issuanceStoresOnlyDigestAndReturnsOwnerCredential() {
        HostelGatePassRepository passes = mock(HostelGatePassRepository.class);
        HostelPassCredentialRepository credentials = mock(HostelPassCredentialRepository.class);
        User owner = user(11L, Role.STUDENT, "resident@campus.invalid", "Hostel A");
        Hostel hostel = hostel();
        HostelGatePass pass = HostelGatePass.builder().id(5L).user(owner).hostel(hostel).status("APPROVED")
                .expectedReturnTime(LocalDateTime.now().plusHours(4)).build();
        when(passes.findForDecision(5L)).thenReturn(Optional.of(pass));
        when(credentials.findActiveForUpdate(5L, "EXIT")).thenReturn(Optional.empty());
        when(credentials.saveAndFlush(any(HostelPassCredential.class))).thenAnswer(i -> i.getArgument(0));
        DomainAccess access = mock(DomainAccess.class); when(access.currentUser()).thenReturn(owner);
        HostelGateCredentialService service = new HostelGateCredentialService(passes, credentials, mock(HostelScanIntentRepository.class),
                mock(HostelGateEvidenceRepository.class), access, mock(FeatureFlagService.class), mock(AuditService.class));

        HostelPassViews.CredentialView result = service.issueCurrentCredential(5L);

        assertEquals("EXIT", result.action());
        assertNotNull(result.credential());
        var captured = org.mockito.ArgumentCaptor.forClass(HostelPassCredential.class);
        verify(credentials).saveAndFlush(captured.capture());
        assertEquals(64, captured.getValue().getTokenDigest().length());
        assertNotEquals(result.credential(), captured.getValue().getTokenDigest());
    }

    @Test
    void wrongOwnerOrChallengeCannotConfirmScan() {
        HostelGatePassRepository passes = mock(HostelGatePassRepository.class);
        HostelPassCredentialRepository credentials = mock(HostelPassCredentialRepository.class);
        HostelScanIntentRepository intents = mock(HostelScanIntentRepository.class);
        User owner = user(11L, Role.STUDENT, "resident@campus.invalid", "Hostel A");
        HostelGatePass pass = HostelGatePass.builder().id(5L).user(owner).hostel(hostel()).status("APPROVED").build();
        HostelPassCredential credential = HostelPassCredential.builder().id(7L).pass(pass).action("EXIT")
                .tokenDigest("digest").expiresAt(LocalDateTime.now().plusMinutes(5)).build();
        HostelScanIntent intent = HostelScanIntent.builder().id(8L).intentKey(UUID.randomUUID().toString())
                .requestKey(UUID.randomUUID().toString()).credential(credential).pass(pass).action("EXIT")
                .operatorId(22L).challengeDigest(digest("123456")).expiresAt(LocalDateTime.now().plusMinutes(1)).build();
        when(intents.findForConfirmation(intent.getIntentKey())).thenReturn(Optional.of(intent));
        DomainAccess access = mock(DomainAccess.class); when(access.currentUser()).thenReturn(owner);
        HostelGateCredentialService service = new HostelGateCredentialService(passes, credentials, intents,
                mock(HostelGateEvidenceRepository.class), access, mock(FeatureFlagService.class), mock(AuditService.class));

        assertThrows(IllegalArgumentException.class, () -> service.confirmScan(new HostelPassViews.ConfirmScan(intent.getIntentKey(), "999999")));
        verifyNoInteractions(credentials, passes);
    }

    @Test
    void confirmedExitCreatesEntryCredentialAndEvidence() {
        HostelGatePassRepository passes = mock(HostelGatePassRepository.class);
        HostelPassCredentialRepository credentials = mock(HostelPassCredentialRepository.class);
        HostelScanIntentRepository intents = mock(HostelScanIntentRepository.class);
        HostelGateEvidenceRepository evidence = mock(HostelGateEvidenceRepository.class);
        User owner = user(11L, Role.STUDENT, "resident@campus.invalid", "Hostel A");
        HostelGatePass pass = HostelGatePass.builder().id(5L).user(owner).hostel(hostel()).status("APPROVED")
                .expectedReturnTime(LocalDateTime.now().plusHours(4)).build();
        HostelPassCredential credential = HostelPassCredential.builder().id(7L).pass(pass).action("EXIT")
                .tokenDigest("digest").expiresAt(LocalDateTime.now().plusMinutes(5)).build();
        HostelScanIntent intent = HostelScanIntent.builder().id(8L).intentKey(UUID.randomUUID().toString())
                .requestKey(UUID.randomUUID().toString()).credential(credential).pass(pass).action("EXIT")
                .operatorId(22L).challengeDigest(digest("123456")).expiresAt(LocalDateTime.now().plusMinutes(1)).build();
        when(intents.findForConfirmation(intent.getIntentKey())).thenReturn(Optional.of(intent));
        when(passes.findForDecision(5L)).thenReturn(Optional.of(pass));
        when(credentials.findForUpdate(7L)).thenReturn(Optional.of(credential));
        when(credentials.findActiveForUpdate(5L, "ENTRY")).thenReturn(Optional.empty());
        when(passes.saveAndFlush(any(HostelGatePass.class))).thenAnswer(i -> i.getArgument(0));
        when(intents.saveAndFlush(any(HostelScanIntent.class))).thenAnswer(i -> i.getArgument(0));
        when(credentials.saveAndFlush(any(HostelPassCredential.class))).thenAnswer(i -> i.getArgument(0));
        when(evidence.saveAndFlush(any(HostelGateEvidence.class))).thenAnswer(i -> i.getArgument(0));
        DomainAccess access = mock(DomainAccess.class); when(access.currentUser()).thenReturn(owner);
        HostelGateCredentialService service = new HostelGateCredentialService(passes, credentials, intents, evidence,
                access, mock(FeatureFlagService.class), mock(AuditService.class));

        HostelPassViews.OwnerConfirmedReceipt result = service.confirmScan(new HostelPassViews.ConfirmScan(intent.getIntentKey(), "123456"));

        assertEquals("ACTIVE_OUT", result.status());
        assertNotNull(result.nextCredential());
        assertEquals("ENTRY", result.nextCredential().action());
        verify(evidence).saveAndFlush(any(HostelGateEvidence.class));
    }

    private static User user(Long id, Role role, String email, String hostelName) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id); when(user.getRole()).thenReturn(role);
        when(user.getEmail()).thenReturn(email); when(user.getHostelName()).thenReturn(hostelName);
        return user;
    }

    private static Hostel hostel() { return Hostel.builder().id(3L).name("Hostel A").wardenEmail("warden@campus.invalid").curfewTime("21:00").build(); }
    private static String digest(String value) { try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new AssertionError(e); } }
}

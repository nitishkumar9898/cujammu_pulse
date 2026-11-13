package com.college.events.service;

import com.college.events.dto.AlumniDtos.*;
import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AlumniPrivacyTest {
    private final UserRepository users = mock(UserRepository.class);
    private final AlumniProfileRepository profiles = mock(AlumniProfileRepository.class);
    private final AlumniJobPostingRepository jobs = mock(AlumniJobPostingRepository.class);
    private final FeatureFlagService flags = mock(FeatureFlagService.class);
    private final AuditService audit = mock(AuditService.class);
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final jakarta.validation.ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    private final AlumniNetworkService service = new AlumniNetworkService(profiles, jobs,
            new DomainAccess(users, mock(EventRepository.class)), flags, audit, notifications, validation.getValidator());
    private User owner;

    @BeforeEach void setup() {
        owner = account(1L, Role.ALUMNI);
        when(flags.isFeatureEnabled("MODULE_ALUMNI")).thenReturn(true);
        when(profiles.lockOwner(1L)).thenReturn(Optional.of(owner));
        when(profiles.saveAndFlush(any())).thenAnswer(call -> { AlumniProfile p=call.getArgument(0); if(p.getId()==null)p.setId(10L); return p; });
        when(jobs.saveAndFlush(any())).thenAnswer(call -> { AlumniJobPosting j=call.getArgument(0);j.setId(30L);return j; });
        authenticate(owner);
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); validation.close(); }

    @Test void anonymousDirectoryRejectedBeforeQuery() {
        SecurityContextHolder.clearContext();
        assertThrows(AccessDeniedException.class,()->service.directory(null,false,0));
        verifyNoInteractions(profiles);
    }
    @Test void disabledPersistedAccountCannotReadOwnProfile() {
        owner.setEnabled(false);
        assertThrows(AccessDeniedException.class,service::ownProfile);
        verifyNoInteractions(profiles);
    }
    @Test void disabledModuleRejectsServiceBypass() {
        when(flags.isFeatureEnabled("MODULE_ALUMNI")).thenReturn(false);
        assertEquals(404,assertThrows(ResponseStatusException.class,service::ownProfile).getStatusCode().value());
        verifyNoInteractions(profiles);
    }
    @Test void creatingProfileCannotAwardMembershipOrVerification() {
        Profile saved=service.updateProfile(update(0));
        assertFalse(saved.verifiedAlumni());assertFalse(owner.isAlumni());
        verify(audit).logAction(anyString(),anyString(),eq("ALUMNI_PROFILE_UPDATED"),eq("recordId=10"));
        verify(notifications).save(any(Notification.class));
        verify(users,never()).save(any());
    }
    @Test void legacyVerifiedFlagWithoutReviewerIsUntrusted() {
        AlumniProfile legacy=profile();legacy.setVerifiedById(null);legacy.setVerifiedAt(null);
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(legacy));
        assertFalse(service.ownProfile().verifiedAlumni());
        assertThrows(AccessDeniedException.class,()->service.postJob(jobCommand()));
        verifyNoInteractions(jobs);
    }
    @Test void privateProfileCannotBeReadByDifferentMember() {
        when(profiles.findById(10L)).thenReturn(Optional.of(profile()));
        authenticate(account(2L,Role.STUDENT));
        assertThrows(AccessDeniedException.class,()->service.privateProfile(10L));
    }
    @Test void persistedRoleOverridesForgedSessionAdmin() {
        User persisted=account(2L,Role.STUDENT);authenticate(account(2L,Role.ADMIN));
        when(users.findById(2L)).thenReturn(Optional.of(persisted));
        assertThrows(AccessDeniedException.class,()->service.verifyProfile(10L,new Verification(0,true)));
        verify(profiles,never()).saveAndFlush(any());
    }
    @Test void administratorCannotSelfVerify() {
        owner.setRole(Role.ADMIN);authenticate(owner);
        when(profiles.findById(10L)).thenReturn(Optional.of(profile()));
        assertThrows(AccessDeniedException.class,()->service.verifyProfile(10L,new Verification(0,true)));
        verify(profiles,never()).saveAndFlush(any());
    }
    @Test void independentAdminVerificationPersistsProvenanceAndNotice() {
        AlumniProfile p=profile();p.setVerifiedById(null);p.setVerifiedAt(null);p.setVerifiedAlumni(false);
        when(profiles.findById(10L)).thenReturn(Optional.of(p));when(profiles.findByUserId(1L)).thenReturn(Optional.of(p));
        authenticate(account(2L,Role.ADMIN));
        assertTrue(service.verifyProfile(10L,new Verification(0,true)).verifiedAlumni());
        assertEquals(2L,p.getVerifiedById());assertNotNull(p.getVerifiedAt());assertTrue(owner.isAlumni());
        verify(audit).logAction(anyString(),eq("ADMIN"),eq("ALUMNI_VERIFIED"),eq("recordId=10"));
        verify(notifications).save(any(Notification.class));
    }
    @Test void staleProfileRevisionCannotOverwritePreferences() {
        AlumniProfile p=profile();p.setVersion(4);when(profiles.findByUserId(1L)).thenReturn(Optional.of(p));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.updateProfile(update(3))).getStatusCode().value());
        verify(profiles,never()).saveAndFlush(any());verifyNoInteractions(audit,notifications);
    }
    @Test void directoryUsesAllowlistAndGranularPrivacy() throws Exception {
        AlumniProfile p=profile();p.setCurrentCompany("Hidden employer");p.setLocation("Hidden home");p.setLinkedInUrl("javascript:alert(1)");
        when(profiles.findVisible(isNull(),eq(false),any())).thenReturn(new PageImpl<>(List.of(p)));
        var entry=service.directory(null,false,0).items().getFirst();
        assertNull(entry.currentCompany());assertNull(entry.location());assertNull(entry.linkedInUrl());
        String json=new ObjectMapper().writeValueAsString(entry);
        for(String privateField:List.of("email","phone","enrollment","password","hostel","verifiedById","Hidden")) assertFalse(json.contains(privateField));
    }
    @Test void unsafeLinksRejectedBeforePersisting() {
        ProfileUpdate command=new ProfileUpdate(0,"MSc",2025,null,null,null,"javascript:alert(1)",null,false,false,false,false,false,false,true);
        assertEquals(400,assertThrows(ResponseStatusException.class,()->service.updateProfile(command)).getStatusCode().value());
        verify(profiles,never()).saveAndFlush(any());
    }
    @Test void verifiedDegreeCannotBeChangedThroughSelfEdit() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile()));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.updateProfile(update(0))).getStatusCode().value());
        verify(profiles,never()).saveAndFlush(any());
    }
    @Test void jobCreationRetryConvergesWithoutSecondAuditOrNotice() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile()));
        var command=jobCommand();service.postJob(command);
        var captured=org.mockito.ArgumentCaptor.forClass(AlumniJobPosting.class);verify(jobs).saveAndFlush(captured.capture());
        when(jobs.findByPostedByIdAndRequestKey(1L,command.requestKey())).thenReturn(Optional.of(captured.getValue()));
        assertEquals(30L,service.postJob(command).id());
        verify(jobs,times(1)).saveAndFlush(any());verify(notifications,times(1)).save(any());
    }
    @Test void anotherMemberCannotCloseJob() {
        when(jobs.findById(30L)).thenReturn(Optional.of(AlumniJobPosting.builder().postedBy(account(2L,Role.ALUMNI)).build()));
        assertThrows(AccessDeniedException.class,()->service.changeJobState(30L,new JobState(0,false)));
        verify(jobs,never()).saveAndFlush(any());
    }
    @Test void invalidCommandIsRejectedAtServiceBoundary() {
        assertEquals(400,assertThrows(ResponseStatusException.class,()->service.updateProfile(
            new ProfileUpdate(0,"",1800,null,null,null,null,null,false,false,false,false,false,false,false))).getStatusCode().value());
        verify(profiles,never()).saveAndFlush(any());
    }

    private ProfileUpdate update(long version) { return new ProfileUpdate(version,"MSc",2025,"Example",null,null,null,null,false,false,false,false,false,false,false); }
    private JobUpdate jobCommand() { return new JobUpdate("00000000-0000-0000-0000-000000000001","Example","Engineer","FULL_TIME","Campus","Synthetic listing","https://example.invalid/jobs","ALL"); }
    private AlumniProfile profile() { return AlumniProfile.builder().id(10L).user(owner).department("CSIT").degree("BSc").graduationYear(2024)
        .verifiedAlumni(true).verifiedById(99L).verifiedAt(LocalDateTime.of(2025,1,1,0,0)).build(); }
    private User account(Long id,Role role) { return User.builder().id(id).role(role).enabled(true).name("Synthetic person "+id)
        .email("person"+id+"@example.invalid").password("synthetic-hash").phone("private-phone").enrollmentNo("private-enrollment").build(); }
    private void authenticate(User user) { when(users.findById(user.getId())).thenReturn(Optional.of(user));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,user.getAuthorities())); }
}

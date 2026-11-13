package com.college.events.service;

import com.college.events.model.*;
import com.college.events.repository.EventRepository;
import com.college.events.repository.EventOperationsRepository;
import com.college.events.repository.RegistrationRepository;
import com.college.events.security.DomainAccess;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MediaPublicationScopeTest {
    final DomainAccess access=mock(DomainAccess.class);
    final EventRepository events=mock(EventRepository.class);
    final FeatureFlagService flags=mock(FeatureFlagService.class);
    final EventOperationsRepository operations=mock(EventOperationsRepository.class);
    final RegistrationRepository registrations=mock(RegistrationRepository.class);
    final MediaAccessService policy=new MediaAccessService(access,events,flags,operations,registrations);
    final User viewer=User.builder().id(2L).department("Engineering").enabled(true).role(Role.STUDENT).build();
    final MediaAsset banner=MediaAsset.builder().ownerUserId(1L).resourceType("EVENT_BANNER").resourceId(10L).objectKey("events/banner.png").build();
    Event event(EventStatus status,String department,String key) {
        var e=Event.builder().id(10L).status(status).targetDepartment(department).bannerImage(key).build();
        when(access.currentUser()).thenReturn(viewer); when(events.findById(10L)).thenReturn(Optional.of(e)); return e;
    }
    @Test void unapprovedBannerDoesNotBecomePublicThroughBinding() {
        event(EventStatus.PENDING,"ALL",banner.getObjectKey()); assertFalse(policy.mayRead(banner));
    }
    @Test void obsoleteBannerBindingDoesNotExposeReplacedAsset() {
        event(EventStatus.APPROVED,"ALL","events/replacement.png"); assertFalse(policy.mayRead(banner));
    }
    @Test void selfDeclaredDepartmentCannotAuthorizePrivateAudienceBanner() {
        event(EventStatus.APPROVED,"Engineering",banner.getObjectKey()); assertFalse(policy.mayRead(banner));
    }
    @Test void approvedAllAudienceCanonicalBannerCanBeRead() {
        event(EventStatus.APPROVED,"ALL",banner.getObjectKey()); assertTrue(policy.mayRead(banner));
        verify(flags).requireEnabledForRead("MODULE_EVENTS");
    }
    @Test void onlyTheActiveAssignedAdministratorCanReadBoundPendingProposalMedia() {
        viewer.setRole(Role.ADMIN);
        Event pending=event(EventStatus.PENDING,"ALL",banner.getObjectKey());
        pending.setOrganizer(User.builder().id(1L).role(Role.FACULTY).build());
        pending.setAssignedReviewerId(viewer.getId()); pending.setReviewerAssignedByUserId(3L);
        pending.setReviewerAssignedAt(java.time.LocalDateTime.now()); pending.setLastReviewerAssignmentRevision(2L);
        assertTrue(policy.mayRead(banner));
        pending.setAssignedReviewerId(4L); assertFalse(policy.mayRead(banner));
    }
    @Test void assignedAdministratorNeedsCompleteAssignmentProvenanceAndCannotReadUnboundTemplates() {
        viewer.setRole(Role.ADMIN);
        Event pending=event(EventStatus.PENDING,"ALL",banner.getObjectKey());
        pending.setOrganizer(User.builder().id(1L).role(Role.FACULTY).build());
        pending.setAssignedReviewerId(viewer.getId()); pending.setReviewerAssignedByUserId(3L);
        assertFalse(policy.mayRead(banner));
        pending.setCertificateTemplatePath("events/template.png");
        var unbound=MediaAsset.builder().ownerUserId(1L).resourceType(null).resourceId(null).objectKey("events/template.png").build();
        assertFalse(policy.mayRead(unbound));
    }
    @Test void certificateTemplateRemainsPrivateAfterPublicEventPublication() {
        viewer.setRole(Role.STUDENT);
        Event published=event(EventStatus.APPROVED,"ALL","events/banner.png"); published.setCertificateTemplatePath("events/template.png");
        var template=MediaAsset.builder().ownerUserId(1L).resourceType("EVENT_CERT_TEMPLATE").resourceId(10L).objectKey("events/template.png").build();
        assertFalse(policy.mayRead(template));
    }
    @Test void publisherAdministratorCanReadOnlyAStillUnpublishedBoundProposal() {
        viewer.setRole(Role.ADMIN);
        Event pending=event(EventStatus.APPROVED_PENDING_PUBLICATION,"ALL",banner.getObjectKey());
        pending.setOrganizer(User.builder().id(1L).role(Role.FACULTY).build()); pending.setApprovedAt(java.time.LocalDateTime.now());
        assertTrue(policy.mayRead(banner));
        pending.setStatus(EventStatus.CANCELLED); assertFalse(policy.mayRead(banner));
    }
    @Test void assignedReviewerCannotReadMediaAfterProposalGainsOperationsOrRegistrationHistory() {
        viewer.setRole(Role.ADMIN);
        Event pending=event(EventStatus.PENDING,"ALL",banner.getObjectKey());
        pending.setOrganizer(User.builder().id(1L).role(Role.FACULTY).build());
        pending.setAssignedReviewerId(viewer.getId()); pending.setReviewerAssignedByUserId(3L);
        pending.setReviewerAssignedAt(java.time.LocalDateTime.now()); pending.setLastReviewerAssignmentRevision(2L);
        when(operations.findByEventId(10L)).thenReturn(Optional.of(mock(EventOperations.class)));
        assertFalse(policy.mayRead(banner));
        when(operations.findByEventId(10L)).thenReturn(Optional.empty());
        when(registrations.existsByEvent(pending)).thenReturn(true);
        assertFalse(policy.mayRead(banner));
    }
}

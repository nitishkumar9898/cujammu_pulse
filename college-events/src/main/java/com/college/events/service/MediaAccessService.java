package com.college.events.service;

import com.college.events.model.*;
import com.college.events.repository.EventRepository;
import com.college.events.repository.EventOperationsRepository;
import com.college.events.repository.RegistrationRepository;
import com.college.events.security.DomainAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Objects;

/** Explicit publication binding permits a banner; possession of an object key grants nothing. */
@Service @RequiredArgsConstructor
public class MediaAccessService {
    private final DomainAccess access;
    private final EventRepository events;
    private final FeatureFlagService flags;
    private final EventOperationsRepository operations;
    private final RegistrationRepository registrations;
    public boolean mayRead(MediaAsset asset) {
        User actor=access.currentUser();
        if(asset.getOwnerUserId()==null) return false;
        if("EVENT_BANNER".equals(asset.getResourceType()) || "EVENT_CERT_TEMPLATE".equals(asset.getResourceType())) {
            flags.requireEnabledForRead("MODULE_EVENTS");
            var event=events.findById(asset.getResourceId()==null?-1L:asset.getResourceId()).orElse(null);
            if(event==null) return false;
            String boundKey = "EVENT_BANNER".equals(asset.getResourceType()) ? event.getBannerImage() : event.getCertificateTemplatePath();
            if(!Objects.equals(boundKey,asset.getObjectKey())) return false;
            if(Objects.equals(actor.getId(),asset.getOwnerUserId())) return true;
            boolean assignedReviewer = actor.getRole()==Role.ADMIN && isPurePendingProposal(event)
                    && Objects.equals(actor.getId(),event.getAssignedReviewerId())
                    && event.getOrganizer()!=null && !Objects.equals(actor.getId(),event.getOrganizer().getId())
                    && event.getReviewerAssignedByUserId()!=null && event.getReviewerAssignedAt()!=null
                    && event.getLastReviewerAssignmentRevision()!=null;
            if(assignedReviewer) return true;
            boolean publicationAdministrator = actor.getRole()==Role.ADMIN
                    && isUnpublishedProposal(event)
                    && event.getOrganizer()!=null && !Objects.equals(actor.getId(),event.getOrganizer().getId());
            if(publicationAdministrator) return true;
            if(!"EVENT_BANNER".equals(asset.getResourceType())) return false;
            // Profile department is self-editable and cannot authorize a restricted audience.
            // Department-specific sharing needs a separately verified effective membership adapter.
            return (event.getStatus()==EventStatus.APPROVED||event.getStatus()==EventStatus.COMPLETED)
                    && "ALL".equals(event.getTargetDepartment());
        }
        return Objects.equals(asset.getOwnerUserId(),actor.getId());
    }

    private boolean isPurePendingProposal(Event event) {
        return event.getStatus()==EventStatus.PENDING && event.getApprovedAt()==null && event.getCancelledAt()==null
                && event.getCompletedAt()==null && event.getCompletedByUserId()==null
                && operations.findByEventId(event.getId()).isEmpty() && !registrations.existsByEvent(event);
    }

    private boolean isUnpublishedProposal(Event event) {
        return event.getStatus()==EventStatus.APPROVED_PENDING_PUBLICATION && event.getApprovedAt()!=null
                && event.getCancelledAt()==null && event.getCompletedAt()==null && event.getCompletedByUserId()==null
                && operations.findByEventId(event.getId()).isEmpty() && !registrations.existsByEvent(event);
    }
}

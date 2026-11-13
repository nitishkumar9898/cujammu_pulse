package com.college.events.service;

import com.college.events.dto.EventDto;
import com.college.events.model.*;
import com.college.events.repository.CategoryRepository;
import com.college.events.repository.EventRepository;
import com.college.events.repository.RegistrationRepository;
import com.college.events.repository.UserRepository;
import com.college.events.repository.EventOperationsRepository;
import com.college.events.repository.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class EventService {

    private final EventRepository eventRepository;
    private final CategoryRepository categoryRepository;
    private final FileStorageService fileStorageService;
    private final EmailService emailService;
    private final RegistrationRepository registrationRepository;
    private final UserRepository userRepository;
    private final EventOperationsService eventOperationsService;
    private final NotificationService notificationService;
    private final EventOperationsRepository eventOperationLocks;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.college.events.repository.EventProposalSubmissionReceiptRepository proposalSubmissionReceipts;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.college.events.security.DomainAccess completionAccess;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AuditService completionAudit;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private FeatureFlagService completionFlags;
    /** Optional only for isolated legacy unit constructors; targeted delivery fails closed without it. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private DepartmentRepository recipientDepartments;
    /** Required for audiences above the inline bound; large approval fails closed if unavailable. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private MailAnnouncementCampaignService announcementCampaigns;

    @Transactional
    @CacheEvict(value = "events", allEntries = true)
    public Event createEvent(EventDto dto, User organizer) {
        requireEventFeature("MODULE_EVENTS");
        User actor = proposalActor(organizer);
        validateProposal(dto);
        validateEventInterval(dto.getEventDate(), dto.getEventEndsAt());
        Category category = categoryRepository.findById(dto.getCategoryId())
            .orElseThrow(() -> new IllegalArgumentException("Category not found"));
        String targetDepartment = normalizeTargetDepartment(dto.getTargetDepartment());

        String bannerFilename = null;
        if (dto.getBannerImage() != null && !dto.getBannerImage().isEmpty()) {
            bannerFilename = fileStorageService.storeFile(dto.getBannerImage());
        }

        String certificateTemplateFilename = null;
        if (dto.getCertificateTemplate() != null && !dto.getCertificateTemplate().isEmpty()) {
            certificateTemplateFilename = fileStorageService.storeFile(dto.getCertificateTemplate());
        }

        Event event = Event.builder()
            .title(sanitizeInput(dto.getTitle()))
            .description(sanitizeInput(dto.getDescription()))
            .category(category)
            .organizer(actor)
            .venue(sanitizeInput(dto.getVenue()))
            .eventDate(dto.getEventDate())
            .eventEndsAt(dto.getEventEndsAt())
            .maxSeats(dto.getMaxSeats())
            .fees(dto.getFees() != null ? dto.getFees() : 0.0)
            .contactEmail(dto.getContactEmail())
            .bannerImage(bannerFilename)
            .targetDepartment(targetDepartment)
            .proposalSubmittedAt(LocalDateTime.now())
            .status(EventStatus.PENDING)
            .generateCertificate(dto.isGenerateCertificate())
            .certificateTemplatePath(certificateTemplateFilename)
            .build();

        Event saved = eventRepository.save(event);
        fileStorageService.bindEventBanner(bannerFilename, saved.getId());
        fileStorageService.bindEventCertificateTemplate(certificateTemplateFilename, saved.getId());
        proposalAudit(actor, "EVENT_CREATED", saved);
        log.info("Created event proposal id={}", saved.getId());
        return saved;
    }

    @Transactional
    @CacheEvict(value = "events", allEntries = true)
    public com.college.events.dto.EventProposalReviewDtos.SubmitReceipt submitProposal(
            com.college.events.dto.EventProposalReviewDtos.Submit command) {
        if (proposalSubmissionReceipts == null) throw new IllegalStateException("Proposal submission receipts are unavailable");
        requireEventFeature("MODULE_EVENTS");
        User principal = completionAccess.requireRole(Role.FACULTY, Role.ORGANIZER);
        User actor = userRepository.findForUpdate(principal.getId())
                .filter(User::isEnabled)
                .filter(user -> user.getRole() == Role.FACULTY || user.getRole() == Role.ORGANIZER)
                .orElseThrow(com.college.events.security.DomainAccess::denied);
        EventDto proposal = new EventDto();
        proposal.setTitle(command.title()); proposal.setDescription(command.description());
        proposal.setCategoryId(command.categoryId()); proposal.setVenue(command.venue());
        proposal.setEventDate(command.eventDate()); proposal.setEventEndsAt(command.eventEndsAt());
        proposal.setMaxSeats(command.maxSeats()); proposal.setFees(command.fees() == null ? 0.0 : command.fees());
        proposal.setContactEmail(command.contactEmail()); proposal.setTargetDepartment(command.targetDepartment());
        proposal.setGenerateCertificate(command.generateCertificate() == null || command.generateCertificate());
        validateProposal(proposal);
        validateEventInterval(proposal.getEventDate(), proposal.getEventEndsAt());
        String payloadHash = proposalSubmissionHash(proposal);
        String requestKey = command.requestKey().toString();
        var prior = proposalSubmissionReceipts.findByOrganizerIdAndRequestKey(actor.getId(), requestKey);
        if (prior.isPresent()) {
            var receipt = prior.get();
            if (!MessageDigest.isEqual(receipt.getPayloadHash().getBytes(StandardCharsets.US_ASCII), payloadHash.getBytes(StandardCharsets.US_ASCII)))
                throw new com.college.events.exception.ProposalStateConflictException("Submission request key was already used for different proposal data");
            Event existing = eventRepository.findById(receipt.getEventId()).filter(e -> actor.getId().equals(e.getOrganizer().getId()))
                    .orElseThrow(com.college.events.security.DomainAccess::denied);
            return new com.college.events.dto.EventProposalReviewDtos.SubmitReceipt(existing.getId(), existing.getStatus().name(), existing.getLifecycleVersion(), true);
        }
        Event created = createEvent(proposal, actor);
        proposalSubmissionReceipts.saveAndFlush(com.college.events.model.EventProposalSubmissionReceipt.builder()
                .organizerId(actor.getId()).requestKey(requestKey).payloadHash(payloadHash).eventId(created.getId()).build());
        return new com.college.events.dto.EventProposalReviewDtos.SubmitReceipt(created.getId(), created.getStatus().name(), created.getLifecycleVersion(), false);
    }

    private static String proposalSubmissionHash(EventDto proposal) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String[] fields = {proposal.getTitle(), proposal.getDescription(), proposal.getCategoryId().toString(), proposal.getVenue(),
                    proposal.getEventDate().toString(), proposal.getEventEndsAt() == null ? null : proposal.getEventEndsAt().toString(),
                    proposal.getMaxSeats().toString(), Double.toString(proposal.getFees()), proposal.getContactEmail(),
                    proposal.getTargetDepartment(), Boolean.toString(proposal.isGenerateCertificate())};
            for (String field : fields) {
                if (field == null) { digest.update((byte) 0); continue; }
                byte[] bytes = field.getBytes(StandardCharsets.UTF_8);
                digest.update((byte) 1); digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array()); digest.update(bytes);
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("Proposal fingerprint unavailable", impossible); }
    }

    @Transactional
    @CacheEvict(value = "events", allEntries = true)
    public Event updateEvent(Long eventId, EventDto dto, User organizer) {
        requireEventFeature("MODULE_EVENTS");
        User actor = proposalActor(organizer);
        Event event = eventRepository.findForApproval(eventId)
            .orElseThrow(com.college.events.security.DomainAccess::denied);

        if (!event.getOrganizer().getId().equals(actor.getId())) throw com.college.events.security.DomainAccess.denied();
        requirePureProposal(event);
        if (dto == null || dto.getExpectedLifecycleVersion() == null || dto.getExpectedLifecycleVersion() < 0)
            throw new IllegalArgumentException("Proposal revision is required");
        if (dto.getExpectedLifecycleVersion() != event.getLifecycleVersion())
            throw new com.college.events.exception.ProposalStateConflictException("Proposal changed; reload before editing");
        validateProposal(dto);

        validateEventInterval(dto.getEventDate(), dto.getEventEndsAt());

        Category category = categoryRepository.findById(dto.getCategoryId())
            .orElseThrow(() -> new IllegalArgumentException("Category not found"));
        String targetDepartment = normalizeTargetDepartment(dto.getTargetDepartment());

        if (dto.getBannerImage() != null && !dto.getBannerImage().isEmpty()) {
            // Keep the old immutable-expiry asset until retention purge. A rollback
            // must not leave a committed reference pointing to physically deleted bytes.
            event.setBannerImage(fileStorageService.storeFile(dto.getBannerImage()));
        }

        event.setTitle(sanitizeInput(dto.getTitle()));
        event.setDescription(sanitizeInput(dto.getDescription()));
        event.setCategory(category);
        event.setVenue(sanitizeInput(dto.getVenue()));
        event.setEventDate(dto.getEventDate());
        event.setEventEndsAt(dto.getEventEndsAt());
        event.setMaxSeats(dto.getMaxSeats());
        event.setFees(dto.getFees() != null ? dto.getFees() : 0.0);
        event.setContactEmail(dto.getContactEmail());
        if (dto.getTargetDepartment() != null) {
            event.setTargetDepartment(targetDepartment);
        }
        
        event.setGenerateCertificate(dto.isGenerateCertificate());
        String replacementCertificateTemplate = null;
        if (dto.getCertificateTemplate() != null && !dto.getCertificateTemplate().isEmpty()) {
            replacementCertificateTemplate = fileStorageService.storeFile(dto.getCertificateTemplate());
            event.setCertificateTemplatePath(replacementCertificateTemplate);
        }

        event.setStatus(EventStatus.PENDING); // Re-submit for approval
        event.setLastProposalDecisionRevision(null);
        event.setRejectionReason(null);
        event.setAssignedReviewerId(null);
        event.setReviewerAssignedByUserId(null);
        event.setReviewerAssignedAt(null);
        event.setLastReviewerAssignmentRevision(null);
        event.setProposalSubmittedAt(LocalDateTime.now());

        Event saved = eventRepository.saveAndFlush(event);
        if (dto.getBannerImage() != null && !dto.getBannerImage().isEmpty())
            fileStorageService.bindEventBanner(saved.getBannerImage(), saved.getId());
        fileStorageService.bindEventCertificateTemplate(replacementCertificateTemplate, saved.getId());
        proposalAudit(actor, "EVENT_UPDATED", saved);
        return saved;
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    @CacheEvict(value = "events", allEntries = true)
    public void approveEvent(Long eventId, long expectedRevision) {
        requireEventFeature("MODULE_EVENTS");
        User actor = proposalAdministrator();
        Event event = eventRepository.findForApproval(eventId)
            .orElseThrow(com.college.events.security.DomainAccess::denied);
        if (event.getOrganizer().getId().equals(actor.getId())) throw com.college.events.security.DomainAccess.denied();
        requireAssignedReviewer(event, actor);
        // Approval records the assigned review decision only. Publication is a separate command.
        if(event.getStatus()==EventStatus.APPROVED_PENDING_PUBLICATION && java.util.Objects.equals(event.getLastProposalDecisionRevision(), expectedRevision))return;
        if(event.getStatus()!=EventStatus.PENDING)
            throw new IllegalStateException("Only a pending event can be approved");
        requirePureProposal(event);
        if (event.getLifecycleVersion() != expectedRevision) throw new IllegalStateException("Proposal changed; reload the review");
        event.setStatus(EventStatus.APPROVED_PENDING_PUBLICATION);
        event.setApprovedAt(LocalDateTime.now());
        event.setRejectionReason(null);
        event.setLastProposalDecisionRevision(expectedRevision);
        eventRepository.saveAndFlush(event);
        proposalAudit(actor, "EVENT_APPROVED_PENDING_PUBLICATION", event);
        log.info("Event proposal approved and awaiting publication id={}", event.getId());
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    @CacheEvict(value = "events", allEntries = true)
    public void publishApprovedEvent(Long eventId, long expectedRevision) {
        requireEventFeature("MODULE_EVENTS");
        User actor = proposalAdministrator();
        Event event = eventRepository.findForApproval(eventId)
            .orElseThrow(com.college.events.security.DomainAccess::denied);
        if (event.getOrganizer().getId().equals(actor.getId())) throw com.college.events.security.DomainAccess.denied();
        if (event.getStatus() == EventStatus.APPROVED
                && java.util.Objects.equals(event.getLastPublicationDecisionRevision(), expectedRevision)) return;
        if (event.getStatus() != EventStatus.APPROVED_PENDING_PUBLICATION)
            throw new com.college.events.exception.ProposalStateConflictException("Only an approved unpublished proposal can be published");
        requireUnpublishedProposal(event);
        if (event.getLifecycleVersion() != expectedRevision)
            throw new com.college.events.exception.ProposalStateConflictException("Proposal changed; reload before publication");
        long announcementAudience = countNewEventAnnouncementAudience(event);
        if (announcementAudience > MailAnnouncementCampaignService.MAX_CAMPAIGN_AUDIENCE)
            throw new IllegalStateException("Publication is paused because its announcement audience exceeds the bounded campaign safety limit.");
        event.setStatus(EventStatus.APPROVED);
        event.setPublishedAt(LocalDateTime.now());
        event.setPublishedByUserId(actor.getId());
        event.setLastPublicationDecisionRevision(expectedRevision);
        eventRepository.saveAndFlush(event);
        eventOperationsService.ensureWorkspace(event);
        emailService.sendEventApprovalNotification(event);

        // The public listing, workspace and audience notice become effective together at publication.
        if (announcementAudience > EmailService.MAX_SYNCHRONOUS_AUDIENCE) {
            if (announcementCampaigns == null)
                throw new IllegalStateException("Resumable announcement campaign controls are unavailable");
            Long verifiedDepartmentId = "ALL".equalsIgnoreCase(event.getTargetDepartment()) ? null :
                    resolveTargetDepartment(event.getTargetDepartment()).getId();
            announcementCampaigns.createSnapshot(event, announcementAudience, verifiedDepartmentId);
            log.info("Resumable event announcement campaign created for {} recipients", announcementAudience);
        } else {
            sendNewEventAnnouncementEmails(event);
        }

        proposalAudit(actor, "EVENT_PUBLISHED", event, expectedRevision);
        log.info("Approved event published id={}", event.getId());
    }

    private void sendNewEventAnnouncementEmails(Event event) {
        List<User> targetStudents;
        if ("ALL".equalsIgnoreCase(event.getTargetDepartment())) {
            targetStudents = userRepository.findByRole(Role.STUDENT);
        } else {
            Department department = resolveTargetDepartment(event.getTargetDepartment());
            targetStudents = userRepository.findByRoleAndVerifiedDepartmentId(Role.STUDENT, department.getId());
        }
        // Recheck the bounded snapshot: under READ_COMMITTED, the audience can grow after the count.
        if (targetStudents.size() > EmailService.MAX_SYNCHRONOUS_AUDIENCE) {
            throw new IllegalStateException("Event approval is paused because its announcement audience changed beyond the current delivery limit. Retry after narrowing the audience or enabling resumable campaign delivery.");
        }
        if (!"ALL".equalsIgnoreCase(event.getTargetDepartment()) && targetStudents.isEmpty()) {
            throw new IllegalStateException("Event approval is paused because no office-verified student audience is available for the selected department.");
        }
        emailService.sendNewEventAnnouncement(event, targetStudents);
        log.info("New event announcement queued for {} students for event '{}'",
            targetStudents.size(), event.getTitle());
    }

    private long countNewEventAnnouncementAudience(Event event) {
        if ("ALL".equalsIgnoreCase(event.getTargetDepartment())) {
            return userRepository.countByRole(Role.STUDENT);
        }
        Department department = resolveTargetDepartment(event.getTargetDepartment());
        long audience = userRepository.countByRoleAndVerifiedDepartmentId(Role.STUDENT, department.getId());
        if (audience == 0) {
            throw new IllegalStateException("Event approval is paused because no office-verified student audience is available for the selected department.");
        }
        return audience;
    }

    private String normalizeTargetDepartment(String requested) {
        if (requested == null || "ALL".equalsIgnoreCase(requested.trim())) return "ALL";
        if (requested.isBlank()) throw new IllegalArgumentException("Target department cannot be blank");
        return resolveTargetDepartment(requested).getCode();
    }

    private Department resolveTargetDepartment(String requested) {
        if (recipientDepartments == null) {
            throw new IllegalStateException("Verified department audience controls are unavailable");
        }
        String selector = requested == null ? "" : requested.trim();
        Department department = recipientDepartments.findByCode(selector.toUpperCase(java.util.Locale.ROOT))
                .or(() -> recipientDepartments.findByNameIgnoreCase(selector))
                .filter(Department::isActive)
                .orElseThrow(() -> new IllegalArgumentException("Select an active university department"));
        return department;
    }

    @Transactional
    @CacheEvict(value = "events", allEntries = true)
    public void rejectEvent(Long eventId, String reason, long expectedRevision) {
        requireEventFeature("MODULE_EVENTS");
        User actor = proposalAdministrator();
        if (reason == null || reason.trim().isEmpty() || reason.trim().length() > 500
                || reason.chars().anyMatch(c -> Character.isISOControl(c) && c != '\r' && c != '\n' && c != '\t'))
            throw new IllegalArgumentException("A bounded review reason is required");
        String normalizedReason = reason.trim().replaceAll("[\\r\\n\\t]+", " ");
        Event event = eventRepository.findForApproval(eventId)
            .orElseThrow(com.college.events.security.DomainAccess::denied);
        if (event.getOrganizer().getId().equals(actor.getId())) throw com.college.events.security.DomainAccess.denied();
        requireAssignedReviewer(event, actor);
        if (event.getStatus() == EventStatus.REJECTED && java.util.Objects.equals(event.getLastProposalDecisionRevision(), expectedRevision)
                && normalizedReason.equals(event.getRejectionReason())) return;
        requirePureProposal(event);
        if (event.getStatus() != EventStatus.PENDING || event.getLifecycleVersion() != expectedRevision)
            throw new IllegalStateException("Proposal changed; reload the review");
        event.setStatus(EventStatus.REJECTED);
        event.setRejectionReason(normalizedReason);
        event.setLastProposalDecisionRevision(expectedRevision);
        eventRepository.saveAndFlush(event);
        emailService.sendEventRejectionNotification(event);
        proposalAudit(actor, "EVENT_REJECTED", event);
        log.info("Event rejected id={}", event.getId());
    }

    @Transactional
    @CacheEvict(value = "events", allEntries = true)
    public void markCompleted(Long eventId, User organizer) {
        if(completionAccess==null||completionAudit==null||completionFlags==null)
            throw new IllegalStateException("Completion controls are unavailable");
        requireEventFeature("MODULE_EVENTS");
        User actor=completionAccess.currentUser();
        if(organizer==null||!actor.getId().equals(organizer.getId())) throw com.college.events.security.DomainAccess.denied();
        EventOperations operation=eventOperationLocks.findForUpdate(eventId)
                .orElseThrow(()->new IllegalStateException("Event lifecycle controls are unavailable"));
        Event event=eventRepository.findForApproval(eventId).orElseThrow(com.college.events.security.DomainAccess::denied);
        if(!completionAccess.isEventOwner(actor,event)&&actor.getRole()!=Role.ADMIN) throw com.college.events.security.DomainAccess.denied();
        if(event.getStatus()==EventStatus.COMPLETED&&event.getCompletedAt()!=null) return;
        if(event.getStatus()!=EventStatus.APPROVED||event.getApprovedAt()==null||event.getCancelledAt()!=null
                ||event.getEventDate()==null||event.getEventDate().isAfter(LocalDateTime.now()))
            throw new IllegalStateException("Only an approved started event can be explicitly completed");
        operation.setScannerMasterEnabled(false); operation.setScannerScope("OFF"); operation.setManualWindowOpen(false);
        operation.setScannerEpoch(operation.getScannerEpoch()+1); operation.setManualEpoch(operation.getManualEpoch()+1);
        operation.setScannerClosesAt(LocalDateTime.now()); operation.setManualClosesAt(LocalDateTime.now());
        eventOperationLocks.saveAndFlush(operation);
        event.setStatus(EventStatus.COMPLETED);
        event.setCompletedAt(LocalDateTime.now()); event.setCompletedByUserId(actor.getId());
        eventRepository.saveAndFlush(event);
        completionAudit.logAction(actor.getEmail(),actor.getRole().name(),"EVENT_COMPLETED","eventId="+eventId);
        for(var registration:registrationRepository.findNotifiableByEvent(event,java.util.EnumSet.of(RegistrationStatus.REGISTERED,RegistrationStatus.ATTENDED)))
            notificationService.createNotification(registration.getUser(),"An event you registered for has been completed.","/app/");
    }

    @Transactional
    @CacheEvict(value = "events", allEntries = true)
    public void cancelEvent(Long eventId, User requester) {
        requireEventFeature("MODULE_EVENTS");
        requireEventFeature("ACTION_EVENT_CANCEL");
        eventOperationLocks.findForUpdate(eventId)
            .orElseThrow(() -> new IllegalStateException("Event operations are unavailable"));
        Event event = eventRepository.findForApproval(eventId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        boolean isAdmin = requester.getRole() == Role.ADMIN;
        boolean isOwner = event.getOrganizer().getId().equals(requester.getId());
        if (!isAdmin && !isOwner) throw new AccessDeniedException("Event lifecycle access denied");
        if (event.getStatus() == EventStatus.CANCELLED) return;
        if (event.getStatus() == EventStatus.COMPLETED) {
            throw new IllegalStateException("Completed events require a reviewed correction workflow");
        }

        event.setStatus(EventStatus.CANCELLED);
        event.setCancelledAt(LocalDateTime.now());
        eventRepository.saveAndFlush(event);

        // Notify all registered students
        List<Registration> registrations = registrationRepository.findNotifiableByEvent(event,
                java.util.EnumSet.of(RegistrationStatus.REGISTERED, RegistrationStatus.WAITLISTED));
        // Render immutable snapshots while entities still belong to this transaction.
        // EmailService performs actual asynchronous dispatch only after commit.
        emailService.sendEventCancellationNotification(event, registrations);
        notifyAffectedParticipants(event, registrations,
            "The event " + event.getTitle() + " has been cancelled. Sign in to view the latest event information.");
        if (!isOwner) {
            notificationService.createNotification(event.getOrganizer(),
                "The event " + event.getTitle() + " was cancelled by a university administrator.",
                "/organizer/events/" + event.getId());
        }
        log.info("Event '{}' cancelled by {}. {} students notified.",
            event.getTitle(), requester.getEmail(), registrations.size());
    }

    @Transactional
    @CacheEvict(value = "events", allEntries = true)
    public void postponeEvent(Long eventId, LocalDateTime newDate, String reason, User requester) {
        requireEventFeature("MODULE_EVENTS");
        requireEventFeature("ACTION_EVENT_POSTPONE");
        eventOperationLocks.findForUpdate(eventId)
            .orElseThrow(() -> new IllegalStateException("Event operations are unavailable"));
        Event event = eventRepository.findForApproval(eventId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        boolean isAdmin = requester.getRole() == Role.ADMIN;
        boolean isOwner = event.getOrganizer().getId().equals(requester.getId());
        if (!isAdmin && !isOwner) throw new AccessDeniedException("Event lifecycle access denied");
        if (event.getStatus() == EventStatus.CANCELLED || event.getStatus() == EventStatus.COMPLETED) {
            throw new IllegalStateException("This event can no longer be postponed");
        }
        if (newDate == null || !newDate.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("The replacement date must be in the future");
        }
        if (event.getEventDate() == null || (event.getEventEndsAt() != null
                && !event.getEventEndsAt().isAfter(event.getEventDate()))) {
            throw new IllegalStateException("The stored event interval requires correction before postponement");
        }
        if (event.getEventDate().equals(newDate) && java.util.Objects.equals(event.getPostponeReason(), reason)) return;

        LocalDateTime replacementEnd = null;
        if (event.getEventEndsAt() != null) {
            try {
                replacementEnd = newDate.plus(java.time.Duration.between(event.getEventDate(), event.getEventEndsAt()));
            } catch (java.time.DateTimeException | ArithmeticException failure) {
                throw new IllegalArgumentException("The replacement event interval is outside the supported date range");
            }
        }
        event.setEventDate(newDate);
        event.setEventEndsAt(replacementEnd);
        event.setPostponeReason(reason);
        eventRepository.saveAndFlush(event);

        // Notify all registered students
        List<Registration> registrations = registrationRepository.findNotifiableByEvent(event,
                java.util.EnumSet.of(RegistrationStatus.REGISTERED, RegistrationStatus.WAITLISTED));
        emailService.sendEventPostponementNotification(event, registrations);
        notifyAffectedParticipants(event, registrations,
            "The schedule for " + event.getTitle() + " has changed. Sign in to view the updated event information.");
        if (!isOwner) {
            notificationService.createNotification(event.getOrganizer(),
                "The schedule for " + event.getTitle() + " was changed by a university administrator.",
                "/organizer/events/" + event.getId());
        }
        log.info("Event '{}' postponed to {}. {} students notified.",
            event.getTitle(), newDate, registrations.size());
    }

    private void notifyAffectedParticipants(Event event, List<Registration> registrations, String message) {
        registrations.stream()
            .map(Registration::getUser)
            .filter(java.util.Objects::nonNull)
            .forEach(user -> notificationService.createNotification(user, message, "/events/" + event.getId()));
    }

    private static void validateEventInterval(LocalDateTime start, LocalDateTime end) {
        if (start == null) throw new IllegalArgumentException("Event start time is required");
        if (end != null && !end.isAfter(start)) {
            throw new IllegalArgumentException("Event end time must be after its start time");
        }
    }

    @Transactional
    @CacheEvict(value = "events", allEntries = true)
    public void deleteEvent(Long eventId, User requester) {
        requireEventFeature("MODULE_EVENTS");
        // A hard delete may erase lifecycle evidence or violate media holds. No
        // reviewed archival/deletion workflow exists; preserve existing records.
        throw new IllegalStateException("Event deletion is unavailable. Use the reviewed cancellation workflow for active events.");
    }

    @Transactional(readOnly = true)
    public Event ownProposal(Long eventId, User supplied) {
        requireEventFeature("MODULE_EVENTS");
        User actor = proposalActor(supplied);
        Event event = eventRepository.findWithDetailsById(eventId).orElseThrow(com.college.events.security.DomainAccess::denied);
        if (!event.getOrganizer().getId().equals(actor.getId())) throw com.college.events.security.DomainAccess.denied();
        requirePureProposal(event);
        return event;
    }

    /** Returns an internal asset key only to the controller; never serialize it in a DTO. */
    @Transactional(readOnly = true)
    public String ownProposalMediaKey(long eventId, String mediaKind, User supplied) {
        return proposalMediaKey(ownProposal(eventId, supplied), mediaKind);
    }

    /** A reviewer may see only their assigned pending proposal; any administrator may inspect an unpublished approved item. */
    @Transactional(readOnly = true)
    public String administratorProposalMediaKey(long eventId, String mediaKind) {
        requireEventFeature("MODULE_EVENTS");
        User actor = proposalAdministrator();
        Event event = eventRepository.findWithDetailsById(eventId).orElseThrow(com.college.events.security.DomainAccess::denied);
        boolean assignedReview = event.getStatus() == EventStatus.PENDING && isPureProposal(event)
                && assignmentComplete(event) && !java.util.Objects.equals(event.getOrganizer().getId(), actor.getId())
                && java.util.Objects.equals(event.getAssignedReviewerId(), actor.getId());
        boolean publicationReview = event.getStatus() == EventStatus.APPROVED_PENDING_PUBLICATION
                && isUnpublishedProposal(event) && !java.util.Objects.equals(event.getOrganizer().getId(), actor.getId());
        if (!assignedReview && !publicationReview) throw com.college.events.security.DomainAccess.denied();
        return proposalMediaKey(event, mediaKind);
    }

    private static String proposalMediaKey(Event event, String mediaKind) {
        String key = switch (mediaKind == null ? "" : mediaKind) {
            case "banner" -> event.getBannerImage();
            case "certificate-template" -> event.getCertificateTemplatePath();
            default -> null;
        };
        if (key == null || key.isBlank()) throw com.college.events.security.DomainAccess.denied();
        return key;
    }

    @Transactional
    public com.college.events.dto.EventProposalReviewDtos.EditableProposal ownEditableProposal(long eventId, User supplied) {
        Event event = ownProposal(eventId, supplied);
        if (event.getCategory() == null || event.getCategory().getId() == null)
            throw new com.college.events.exception.ProposalStateConflictException("Proposal category is unavailable; contact the event office");
        return new com.college.events.dto.EventProposalReviewDtos.EditableProposal(event.getId(), event.getTitle(), event.getDescription(),
                event.getCategory().getId(), event.getVenue(), event.getEventDate(), event.getEventEndsAt(), event.getMaxSeats(),
                event.getFees() == null ? 0.0 : event.getFees(), event.getContactEmail(),
                event.getTargetDepartment() == null ? "ALL" : event.getTargetDepartment(), event.isGenerateCertificate(),
                event.getLifecycleVersion(), event.getBannerImage() != null, event.getCertificateTemplatePath() != null);
    }

    @Transactional
    @CacheEvict(value = "events", allEntries = true)
    public com.college.events.dto.EventProposalReviewDtos.EditableProposal updateOwnProposal(long eventId,
            com.college.events.dto.EventProposalReviewDtos.Update command, User supplied) {
        if (command == null) throw new IllegalArgumentException("Proposal update is required");
        EventDto dto = new EventDto();
        dto.setExpectedLifecycleVersion(command.expectedRevision()); dto.setTitle(command.title()); dto.setDescription(command.description());
        dto.setCategoryId(command.categoryId()); dto.setVenue(command.venue()); dto.setEventDate(command.eventDate());
        dto.setEventEndsAt(command.eventEndsAt()); dto.setMaxSeats(command.maxSeats()); dto.setFees(command.fees());
        dto.setContactEmail(command.contactEmail()); dto.setTargetDepartment(command.targetDepartment());
        dto.setGenerateCertificate(Boolean.TRUE.equals(command.generateCertificate()));
        Event updated = updateEvent(eventId, dto, supplied);
        return new com.college.events.dto.EventProposalReviewDtos.EditableProposal(updated.getId(), updated.getTitle(), updated.getDescription(),
                updated.getCategory().getId(), updated.getVenue(), updated.getEventDate(), updated.getEventEndsAt(), updated.getMaxSeats(),
                updated.getFees() == null ? 0.0 : updated.getFees(), updated.getContactEmail(),
                updated.getTargetDepartment() == null ? "ALL" : updated.getTargetDepartment(), updated.isGenerateCertificate(),
                updated.getLifecycleVersion(), updated.getBannerImage() != null, updated.getCertificateTemplatePath() != null);
    }

    @Transactional
    public com.college.events.dto.EventProposalReviewDtos.Queue proposalReviewQueue(int page) {
        requireEventFeature("MODULE_EVENTS");
        User actor = proposalAdministrator();
        validateReviewPage(page);
        var slice = eventRepository.findAllByStatus(EventStatus.PENDING,
                org.springframework.data.domain.PageRequest.of(page, 20, org.springframework.data.domain.Sort.by("id").descending()));
        return new com.college.events.dto.EventProposalReviewDtos.Queue(slice.stream().map(event -> proposalView(event, actor)).toList(), page, slice.hasNext());
    }

    @Transactional(readOnly = true)
    public com.college.events.dto.EventProposalReviewDtos.OwnQueue ownProposalQueue(int page) {
        requireEventFeature("MODULE_EVENTS");
        User actor = completionAccess.requireRole(Role.FACULTY, Role.ORGANIZER);
        validateReviewPage(page);
        var slice = eventRepository.findAllByOrganizerIdOrderByIdDesc(actor.getId(),
                org.springframework.data.domain.PageRequest.of(page, 20));
        return new com.college.events.dto.EventProposalReviewDtos.OwnQueue(slice.stream().map(event ->
                new com.college.events.dto.EventProposalReviewDtos.OwnProposal(event.getId(), event.getTitle(), event.getVenue(),
                        event.getEventDate(), event.getStatus().name(), event.getLifecycleVersion(), event.getRejectionReason(),
                        event.getProposalSubmittedAt())).toList(), page, slice.hasNext());
    }

    @Transactional(readOnly = true)
    public List<com.college.events.dto.EventProposalReviewDtos.ProposalCategory> proposalCategories() {
        requireEventFeature("MODULE_EVENTS");
        completionAccess.requireRole(Role.FACULTY, Role.ORGANIZER);
        return categoryRepository.findAll().stream().filter(category -> category.getId() != null && category.getName() != null)
                .sorted(java.util.Comparator.comparing(Category::getName, String.CASE_INSENSITIVE_ORDER).thenComparing(Category::getId))
                .limit(100).map(category -> new com.college.events.dto.EventProposalReviewDtos.ProposalCategory(category.getId(), category.getName())).toList();
    }

    @Transactional(readOnly = true)
    public com.college.events.dto.EventProposalReviewDtos.Queue proposalPublicationQueue(int page) {
        requireEventFeature("MODULE_EVENTS");
        User actor = proposalAdministrator();
        validateReviewPage(page);
        var slice = eventRepository.findAllByStatus(EventStatus.APPROVED_PENDING_PUBLICATION,
                org.springframework.data.domain.PageRequest.of(page, 20, org.springframework.data.domain.Sort.by("id").descending()));
        return new com.college.events.dto.EventProposalReviewDtos.Queue(slice.stream().map(event -> proposalView(event, actor)).toList(), page, slice.hasNext());
    }

    @Transactional
    public com.college.events.dto.EventProposalReviewDtos.Reviewers proposalReviewers(int page) {
        requireEventFeature("MODULE_EVENTS");
        proposalAdministrator(); validateReviewPage(page);
        var slice = userRepository.findAllByRoleAndEnabledTrue(Role.ADMIN,
                org.springframework.data.domain.PageRequest.of(page, 20, org.springframework.data.domain.Sort.by("id")));
        return new com.college.events.dto.EventProposalReviewDtos.Reviewers(slice.stream()
                .map(user -> new com.college.events.dto.EventProposalReviewDtos.Reviewer(user.getId(), user.getName())).toList(), page, slice.hasNext());
    }

    @Transactional
    public com.college.events.dto.EventProposalReviewDtos.Proposal assignReviewer(long eventId, long reviewerId, long expectedRevision) {
        requireEventFeature("MODULE_EVENTS");
        User actor = proposalAdministrator();
        if (reviewerId <= 0 || expectedRevision < 0) throw new IllegalArgumentException("Invalid review assignment");
        Event event = eventRepository.findForApproval(eventId).orElseThrow(com.college.events.security.DomainAccess::denied);
        if (event.getOrganizer().getId().equals(actor.getId())) throw com.college.events.security.DomainAccess.denied();
        requirePureProposal(event);
        if (event.getStatus() != EventStatus.PENDING)
            throw new com.college.events.exception.ProposalStateConflictException("Only pending proposals can be assigned");
        // No user lock here: outbox delivery locks user before feature/event. Adding the inverse order would deadlock.
        User reviewer = userRepository.findById(reviewerId).filter(user -> user.isEnabled() && user.getRole() == Role.ADMIN)
                .orElseThrow(com.college.events.security.DomainAccess::denied);
        if (reviewer.getId().equals(event.getOrganizer().getId())) throw com.college.events.security.DomainAccess.denied();
        boolean sameReviewer = java.util.Objects.equals(event.getAssignedReviewerId(), reviewerId);
        if (assignmentComplete(event) && sameReviewer && java.util.Objects.equals(event.getReviewerAssignedByUserId(), actor.getId())
                && java.util.Objects.equals(event.getLastReviewerAssignmentRevision(), expectedRevision)) return proposalView(event, actor);
        if (event.getLifecycleVersion() != expectedRevision)
            throw new com.college.events.exception.ProposalStateConflictException("Proposal changed; reload before assignment");
        if (assignmentComplete(event) && sameReviewer) return proposalView(event, actor);
        Long previousReviewerId = event.getAssignedReviewerId();
        event.setAssignedReviewerId(reviewer.getId()); event.setReviewerAssignedByUserId(actor.getId());
        event.setReviewerAssignedAt(LocalDateTime.now()); event.setLastReviewerAssignmentRevision(expectedRevision);
        eventRepository.saveAndFlush(event);
        completionAudit.logAction(actor.getEmail(), actor.getRole().name(), "EVENT_REVIEWER_ASSIGNED",
                "eventId=" + event.getId() + ";previousReviewerId=" + previousReviewerId + ";reviewerId=" + reviewer.getId()
                        + ";inputRevision=" + expectedRevision + ";revision=" + event.getLifecycleVersion());
        notificationService.createNotification(reviewer, "An event proposal has been assigned to you for review. Open Administration > Proposal reviews.", "/app/");
        return proposalView(event, actor);
    }

    private com.college.events.dto.EventProposalReviewDtos.Proposal proposalView(Event event, User actor) {
        User assigned = event.getAssignedReviewerId() == null ? null : userRepository.findById(event.getAssignedReviewerId()).orElse(null);
        boolean active = assigned != null && assigned.isEnabled() && assigned.getRole() == Role.ADMIN;
        boolean eligible = event.getStatus() == EventStatus.PENDING && isPureProposal(event) && !event.getOrganizer().getId().equals(actor.getId());
        boolean publishable = event.getStatus() == EventStatus.APPROVED_PENDING_PUBLICATION && isUnpublishedProposal(event)
                && !event.getOrganizer().getId().equals(actor.getId());
        return new com.college.events.dto.EventProposalReviewDtos.Proposal(event.getId(), event.getTitle(), event.getDescription(), event.getVenue(),
                event.getEventDate(), event.getEventEndsAt(), event.getOrganizer().getName(), event.getStatus().name(), event.getLifecycleVersion(),
                event.getAssignedReviewerId(), assigned == null ? null : assigned.getName(), active, eligible,
                eligible && active && assignmentComplete(event) && actor.getId().equals(event.getAssignedReviewerId()), publishable,
                event.getMaxSeats(), event.getFees() == null ? 0 : event.getFees(),
                event.getTargetDepartment() == null ? "" : event.getTargetDepartment(), event.getCategory() == null ? null : event.getCategory().getName(),
                event.isGenerateCertificate(), event.getBannerImage() != null, event.getCertificateTemplatePath() != null);
    }
    private static void validateReviewPage(int page) {
        if (page < 0 || page > 10000) throw new IllegalArgumentException("Invalid review page");
    }
    @Transactional
    public boolean canReviewProposal(long eventId) {
        User actor = proposalAdministrator();
        if (completionFlags == null || !completionFlags.isFeatureEnabled("MODULE_EVENTS")) return false;
        Event event = eventRepository.findById(eventId).orElseThrow(com.college.events.security.DomainAccess::denied);
        return event.getStatus() == EventStatus.PENDING && isPureProposal(event) && assignmentComplete(event)
                && !event.getOrganizer().getId().equals(actor.getId()) && actor.getId().equals(event.getAssignedReviewerId());
    }
    private static boolean assignmentComplete(Event event) {
        return event.getAssignedReviewerId() != null && event.getReviewerAssignedByUserId() != null
                && event.getReviewerAssignedAt() != null && event.getLastReviewerAssignmentRevision() != null;
    }
    private static void requireAssignedReviewer(Event event, User actor) {
        if (!assignmentComplete(event) || !actor.getId().equals(event.getAssignedReviewerId()))
            throw com.college.events.security.DomainAccess.denied();
    }

    private User proposalActor(User supplied) {
        if (completionAccess == null || completionAudit == null) throw new IllegalStateException("Proposal authorization/audit unavailable");
        User actor = completionAccess.requireRole(Role.FACULTY, Role.ORGANIZER);
        if (supplied == null || !java.util.Objects.equals(actor.getId(), supplied.getId())) throw com.college.events.security.DomainAccess.denied();
        return actor;
    }
    private User proposalAdministrator() {
        if (completionAccess == null || completionAudit == null) throw new IllegalStateException("Proposal authorization/audit unavailable");
        return completionAccess.requireRole(Role.ADMIN);
    }
    private void requirePureProposal(Event event) {
        if (!isPureProposal(event))
            throw new com.college.events.exception.ProposalStateConflictException("This event requires the reviewed lifecycle workflow, not proposal editing");
    }
    private boolean isPureProposal(Event event) {
        return (event.getStatus() == EventStatus.PENDING || event.getStatus() == EventStatus.REJECTED)
                && event.getApprovedAt() == null && event.getCancelledAt() == null && event.getCompletedAt() == null
                && event.getCompletedByUserId() == null && eventOperationLocks.findByEventId(event.getId()).isEmpty()
                && !registrationRepository.existsByEvent(event);
    }
    private void requireUnpublishedProposal(Event event) {
        if (!isUnpublishedProposal(event))
            throw new com.college.events.exception.ProposalStateConflictException("This event is not eligible for proposal publication");
    }
    private boolean isUnpublishedProposal(Event event) {
        return event.getStatus() == EventStatus.APPROVED_PENDING_PUBLICATION && event.getApprovedAt() != null
                && event.getCancelledAt() == null && event.getCompletedAt() == null
                && event.getCompletedByUserId() == null && eventOperationLocks.findByEventId(event.getId()).isEmpty()
                && !registrationRepository.existsByEvent(event);
    }
    private void proposalAudit(User actor, String action, Event event) {
        completionAudit.logAction(actor.getEmail(), actor.getRole().name(), action, "eventId=" + event.getId());
    }
    private void proposalAudit(User actor, String action, Event event, long inputRevision) {
        completionAudit.logAction(actor.getEmail(), actor.getRole().name(), action,
                "eventId=" + event.getId() + ";inputRevision=" + inputRevision + ";resultRevision=" + event.getLifecycleVersion()
                        + ";status=" + event.getStatus().name());
    }
    private void validateProposal(EventDto dto) {
        if (dto == null || dto.getTitle() == null || dto.getTitle().trim().length() < 3 || dto.getTitle().length() > 200
                || dto.getDescription() == null || dto.getDescription().trim().length() < 20 || dto.getDescription().length() > 10000
                || dto.getVenue() == null || dto.getVenue().isBlank() || dto.getVenue().length() > 200
                || dto.getCategoryId() == null || dto.getEventDate() == null || !dto.getEventDate().isAfter(LocalDateTime.now())
                || dto.getMaxSeats() == null || dto.getMaxSeats() < 1 || dto.getMaxSeats() > 10000
                || (dto.getFees() != null && (!Double.isFinite(dto.getFees()) || dto.getFees() < 0)))
            throw new IllegalArgumentException("Invalid event proposal");
        if (sanitizeInput(dto.getTitle()).trim().length() < 3
                || sanitizeInput(dto.getDescription()).trim().length() < 20
                || sanitizeInput(dto.getVenue()).isBlank())
            throw new IllegalArgumentException("Proposal text must contain readable content");
        if (dto.getContactEmail() != null && !dto.getContactEmail().isEmpty()) {
            String contact = dto.getContactEmail();
            if (contact.length() > 254 || contact.chars().anyMatch(Character::isISOControl))
                throw new IllegalArgumentException("Invalid contact email");
            try {
                var address = new jakarta.mail.internet.InternetAddress(contact, true);
                address.validate();
                if (address.getPersonal() != null || !contact.equals(address.getAddress()))
                    throw new IllegalArgumentException("Invalid contact email");
            } catch (jakarta.mail.internet.AddressException invalid) {
                throw new IllegalArgumentException("Invalid contact email");
            }
        }
    }

    public Optional<Event> findById(Long id) {
        return eventRepository.findWithDetailsById(id);
    }

    @Cacheable(value = "events", key = "'upcoming'")
    public List<Event> findUpcomingApproved() {
        return eventRepository.findUpcomingApprovedEvents(LocalDateTime.now());
    }

    @Cacheable(value = "events", key = "'search_' + (#keyword != null ? #keyword : '') + '_' + (#categoryId != null ? #categoryId : '')")
    public List<Event> searchEvents(String keyword, Long categoryId) {
        LocalDateTime now = LocalDateTime.now();
        if (keyword != null && !keyword.isBlank() && categoryId != null) {
            return eventRepository.searchApprovedEventsByCategory(keyword, categoryId, now);
        } else if (keyword != null && !keyword.isBlank()) {
            return eventRepository.searchApprovedEvents(keyword, now);
        } else if (categoryId != null) {
            return eventRepository.findByStatusAndCategoryId(categoryId, now);
        }
        return eventRepository.findUpcomingApprovedEvents(now);
    }

    public List<Event> findByStatus(EventStatus status) {
        return eventRepository.findByStatus(status);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Slice<Event> findApprovedUnpublished(int page) {
        requireEventFeature("MODULE_EVENTS");
        validateReviewPage(page);
        return eventRepository.findAllByStatus(EventStatus.APPROVED_PENDING_PUBLICATION,
                org.springframework.data.domain.PageRequest.of(page, 20, org.springframework.data.domain.Sort.by("id").descending()));
    }

    public List<Event> findByOrganizer(User organizer) {
        return eventRepository.findByOrganizer(organizer);
    }

    public List<Event> findAll() {
        return eventRepository.findAll();
    }

    public long countByStatus(EventStatus status) {
        return eventRepository.countByStatus(status);
    }

    public long countAll() {
        return eventRepository.count();
    }

    /** A start timestamp is not an end timestamp. Closure needs an authorized recorded decision. */
    @Transactional
    public void autoCompleteExpiredEvents() {
        log.debug("Automatic event closure requires approved session end times; awaiting explicit manager completion");
    }

    private String sanitizeInput(String input) {
        if (input == null) return null;
        return input.trim()
            .replaceAll("<script.*?>.*?</script>", "")
            .replaceAll("<.*?\\s+on.*?>", "")
            .replaceAll("<[^>]*>", "");
    }

    /** Acquire the versioned control before any domain row lock so disable and mutation serialize. */
    private void requireEventFeature(String key) {
        if (completionFlags == null) throw new IllegalStateException("Event runtime controls are unavailable");
        completionFlags.requireEnabledForUpdate(key);
    }
}

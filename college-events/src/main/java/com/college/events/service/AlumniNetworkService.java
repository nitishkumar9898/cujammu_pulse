package com.college.events.service;

import com.college.events.dto.AlumniDtos.*;
import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

/** Alumni membership comes from independent verification, never from profile edits or dates. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlumniNetworkService {
    private final AlumniProfileRepository profiles;
    private final AlumniJobPostingRepository jobs;
    private final DomainAccess access;
    private final FeatureFlagService flags;
    private final AuditService audit;
    private final NotificationRepository notifications;
    private final Validator validator;

    public PageResult<DirectoryEntry> directory(String department, boolean mentors, int page) {
        current();
        return result(profiles.findVisible(department(department), mentors, paging(page)).map(this::directoryEntry));
    }

    public Profile ownProfile() {
        User actor = current();
        return profiles.findByUserId(actor.getId()).map(this::profile).orElse(null);
    }

    public Profile privateProfile(Long id) {
        User actor = current();
        AlumniProfile value = profiles.findById(id).orElseThrow(AlumniNetworkService::notFound);
        if (!Objects.equals(actor.getId(), value.getUser().getId()) && actor.getRole() != Role.ADMIN)
            throw DomainAccess.denied();
        return profile(value);
    }

    public PageResult<Profile> pendingReview(int page) {
        current();
        access.requireRole(Role.ADMIN);
        return result(profiles.findPendingReview(paging(page)).map(this::profile));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Profile updateProfile(ProfileUpdate command) {
        valid(command);
        User actor = lock(current());
        String linkedin = checkedLink(command.linkedin(), true);
        AlumniProfile value = profiles.findByUserId(actor.getId()).orElseGet(() -> AlumniProfile.builder()
                .user(actor).department(actor.getDepartment() == null ? "UNASSIGNED" : actor.getDepartment()).build());
        version(value.getVersion(), command.expectedVersion());
        boolean changesVerifiedFacts = !Objects.equals(value.getDegree(), command.degree().trim())
                || value.getGraduationYear() != command.graduationYear();
        if (verified(value) && changesVerifiedFacts)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Verified degree/year require university review before correction");
        value.setDegree(command.degree().trim());
        value.setGraduationYear(command.graduationYear());
        value.setCurrentCompany(trim(command.company()));
        value.setCurrentDesignation(trim(command.designation()));
        value.setLocation(trim(command.location()));
        value.setLinkedInUrl(linkedin);
        value.setBio(trim(command.bio()));
        value.setWillingToMentor(command.mentor());
        value.setWillingToRefer(command.refer());
        value.setDirectoryVisible(command.directoryVisible());
        value.setShowCareer(command.showCareer());
        value.setShowLocation(command.showLocation());
        value.setShowBio(command.showBio());
        value.setShowLinkedIn(command.showLinkedIn());
        profiles.saveAndFlush(value);
        record(actor, "ALUMNI_PROFILE_UPDATED", value.getId(), actor,
                "Your alumni profile and visibility preferences were saved. Directory listing requires verification and your opt-in.");
        return profile(value);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Profile verifyProfile(Long id, Verification command) {
        valid(command);
        current();
        User actor = access.requireRole(Role.ADMIN);
        AlumniProfile initial = profiles.findById(id).orElseThrow(AlumniNetworkService::notFound);
        if (Objects.equals(initial.getUser().getId(), actor.getId())) throw DomainAccess.denied();
        User owner = lock(initial.getUser());
        AlumniProfile value = profiles.findByUserId(owner.getId()).orElseThrow(AlumniNetworkService::notFound);
        version(value.getVersion(), command.expectedVersion());
        value.setVerifiedAlumni(command.verified());
        value.setVerifiedById(command.verified() ? actor.getId() : null);
        value.setVerifiedAt(command.verified() ? LocalDateTime.now(ZoneOffset.UTC) : null);
        owner.setAlumni(command.verified());
        profiles.saveAndFlush(value);
        record(actor, command.verified() ? "ALUMNI_VERIFIED" : "ALUMNI_VERIFICATION_REVOKED", id, owner,
                "Your alumni verification status changed. Open your profile to review it.");
        return profile(value);
    }

    public PageResult<Job> activeJobs(String department, int page) {
        current();
        return result(jobs.findVisible(department(department), paging(page)).map(this::job));
    }

    public PageResult<Job> ownJobs(int page) {
        User actor = current();
        return result(jobs.findByPostedByIdOrderByIdDesc(actor.getId(), paging(page)).map(this::job));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Job postJob(JobUpdate command) {
        valid(command);
        User actor = lock(current());
        requireVerified(actor);
        String link = checkedLink(command.linkOrEmail(), false);
        var existing = jobs.findByPostedByIdAndRequestKey(actor.getId(), command.requestKey());
        if (existing.isPresent()) {
            AlumniJobPosting old = existing.get();
            if (!Objects.equals(old.getCompanyName(), command.companyName().trim())
                    || !Objects.equals(old.getJobTitle(), command.jobTitle().trim())
                    || !Objects.equals(old.getJobType(), command.jobType())
                    || !Objects.equals(old.getLocation(), trim(command.location()))
                    || !Objects.equals(old.getDescription(), command.description().trim())
                    || !Objects.equals(old.getApplicationLinkOrEmail(), link)
                    || !Objects.equals(old.getTargetDepartment(), command.targetDepartment().trim()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Request key already used for different content");
            return job(old);
        }
        AlumniJobPosting value = AlumniJobPosting.builder().postedBy(actor).requestKey(command.requestKey())
                .companyName(command.companyName().trim()).jobTitle(command.jobTitle().trim())
                .jobType(command.jobType()).location(trim(command.location())).description(command.description().trim())
                .applicationLinkOrEmail(link).targetDepartment(command.targetDepartment().trim()).active(true).build();
        jobs.saveAndFlush(value);
        record(actor, "ALUMNI_JOB_POSTED", value.getId(), actor, "Your alumni job listing was published.");
        return job(value);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Job changeJobState(Long id, JobState command) {
        valid(command);
        User actor = current();
        AlumniJobPosting initial = jobs.findById(id).orElseThrow(AlumniNetworkService::notFound);
        if (!Objects.equals(initial.getPostedBy().getId(), actor.getId()) && actor.getRole() != Role.ADMIN)
            throw DomainAccess.denied();
        User owner = lock(initial.getPostedBy());
        AlumniJobPosting value = jobs.findById(id).orElseThrow(AlumniNetworkService::notFound);
        version(value.getVersion(), command.expectedVersion());
        if (command.active()) requireVerified(owner);
        value.setActive(command.active());
        jobs.saveAndFlush(value);
        record(actor, "ALUMNI_JOB_STATE_CHANGED", id, owner, "Your alumni job listing status changed.");
        return job(value);
    }

    private User current() {
        User actor = access.currentUser();
        if (!flags.isFeatureEnabled("MODULE_ALUMNI")) throw notFound();
        return actor;
    }

    private User lock(User owner) {
        return profiles.lockOwner(owner.getId()).filter(User::isEnabled).orElseThrow(DomainAccess::denied);
    }

    private void requireVerified(User actor) {
        if (profiles.findByUserId(actor.getId()).filter(this::verified).isEmpty()) throw DomainAccess.denied();
    }

    private boolean verified(AlumniProfile p) {
        return p.isVerifiedAlumni() && p.getVerifiedById() != null && p.getVerifiedAt() != null
                && !Objects.equals(p.getVerifiedById(), p.getUser().getId());
    }

    private void record(User actor, String action, Long id, User recipient, String message) {
        audit.logAction(actor.getEmail(), actor.getRole().name(), action, "recordId=" + id);
        // Protected inbox persisted in the SAME transaction. No provider/SSE call before commit.
        notifications.save(Notification.builder().user(recipient).message(message).actionUrl("/alumni")
                .isRead(false).build());
    }

    private void valid(Object command) {
        if (command == null || !validator.validate(command).isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid alumni command");
    }

    private void version(long current, long expected) {
        if (current != expected) throw new ResponseStatusException(HttpStatus.CONFLICT, "Data changed; reload before saving");
    }

    private String department(String value) {
        if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) return null;
        if (value.length() > 80) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid department");
        return value.trim();
    }

    private PageRequest paging(int page) {
        if (page < 0 || page > 10000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page");
        return PageRequest.of(page, 24);
    }

    private <T> PageResult<T> result(Page<T> page) {
        return new PageResult<>(page.getContent(), page.getNumber(), page.hasNext());
    }

    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private String checkedLink(String value, boolean linkedinOnly) {
        String normalized = trim(value);
        if (normalized == null && linkedinOnly) return null;
        try {
            URI uri = URI.create(normalized);
            if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null) {
                if (!linkedinOnly || uri.getHost().equalsIgnoreCase("linkedin.com")
                        || uri.getHost().equalsIgnoreCase("www.linkedin.com")) return uri.toASCIIString();
            }
            if (!linkedinOnly && normalized.matches("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}"))
                return "mailto:" + normalized;
        } catch (IllegalArgumentException | NullPointerException ignored) { }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a valid HTTPS link or job contact email");
    }

    private String safeStoredLink(String value, boolean linkedinOnly) {
        if (value != null && value.startsWith("mailto:") && !linkedinOnly) value = value.substring(7);
        try { return checkedLink(value, linkedinOnly); } catch (ResponseStatusException ignored) { return null; }
    }

    private DirectoryEntry directoryEntry(AlumniProfile p) {
        return new DirectoryEntry(p.getId(), p.getUser().getName(), p.getDepartment(), p.getGraduationYear(), p.getDegree(),
                yes(p.getShowCareer()) ? p.getCurrentCompany() : null,
                yes(p.getShowCareer()) ? p.getCurrentDesignation() : null,
                yes(p.getShowLocation()) ? p.getLocation() : null,
                yes(p.getShowLinkedIn()) ? safeStoredLink(p.getLinkedInUrl(), true) : null,
                yes(p.getShowBio()) ? p.getBio() : null, p.isWillingToMentor(), p.isWillingToRefer());
    }

    private Profile profile(AlumniProfile p) {
        return new Profile(p.getId(), p.getVersion(), p.getUser().getName(), p.getDepartment(), p.getGraduationYear(),
                p.getDegree(), p.getCurrentCompany(), p.getCurrentDesignation(), p.getLocation(),
                safeStoredLink(p.getLinkedInUrl(), true), p.getBio(), p.isWillingToMentor(), p.isWillingToRefer(),
                verified(p), yes(p.getDirectoryVisible()), yes(p.getShowCareer()), yes(p.getShowLocation()),
                yes(p.getShowBio()), yes(p.getShowLinkedIn()));
    }

    private Job job(AlumniJobPosting j) {
        return new Job(j.getId(), j.getVersion(), j.getCompanyName(), j.getJobTitle(), j.getJobType(), j.getLocation(),
                j.getDescription(), safeStoredLink(j.getApplicationLinkOrEmail(), false), j.getTargetDepartment(),
                j.isActive(), j.getPostedAt());
    }

    private boolean yes(Boolean value) { return Boolean.TRUE.equals(value); }
    private static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND); }
}

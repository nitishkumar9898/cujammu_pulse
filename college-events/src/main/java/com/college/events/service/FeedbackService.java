package com.college.events.service;

import com.college.events.dto.FeedbackDtos;
import com.college.events.dto.CalendarDtos;
import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Confidential feedback: no public aggregate until its disclosure policy is approved. */
@Service
@RequiredArgsConstructor
public class FeedbackService {
    private final FeedbackRepository feedbackRepository;
    private final EventRepository eventRepository;
    private final FeatureFlagService featureFlags;
    private final DomainAccess access;
    private final RegistrationRepository registrations;
    private final AuditService audit;

    @Transactional
    public Feedback submitFeedback(User suppliedUser, Long eventId, int rating, String comment) {
        featureFlags.requireEnabledForUpdate("MODULE_INSIGHTS");
        User actor = access.requireSelf(suppliedUser.getId());
        if (rating < 1 || rating > 5) throw new IllegalArgumentException("Rating must be between 1 and 5");
        String normalized = comment == null || comment.isBlank() ? null : comment.trim();
        if (normalized != null && normalized.length() > 2000) throw new IllegalArgumentException("Comment is too long");
        // Same canonical event write lock used by lifecycle changes. It also serializes absent feedback rows.
        Event event = eventRepository.findForApproval(eventId).orElseThrow(DomainAccess::denied);
        if (actor.getVerifiedEnrollmentNo() == null || actor.getVerifiedEnrollmentNo().isBlank()
                || !registrations.existsByUserIdAndEventIdAndStatusIn(actor.getId(), eventId,
                    List.of(RegistrationStatus.REGISTERED, RegistrationStatus.ATTENDED))) throw DomainAccess.denied();
        if (event.getStatus() != EventStatus.COMPLETED || event.getCompletedAt() == null
                || event.getCompletedByUserId() == null || event.getApprovedAt() == null)
            throw new IllegalStateException("Feedback opens after reviewed event completion");
        Optional<Feedback> previous = feedbackRepository.findByUserAndEvent(actor, event);
        if (previous.isPresent()) {
            Feedback existing = previous.get();
            if (existing.getRating() != rating || !Objects.equals(existing.getComment(), normalized))
                throw new IllegalStateException("Feedback was already submitted; changing it requires a reviewed correction");
            return existing;
        }
        Feedback saved = feedbackRepository.saveAndFlush(Feedback.builder().event(event).user(actor)
                .rating(rating).comment(normalized).build());
        audit.logAction(actor.getUsername(), actor.getRole().name(), "EVENT_FEEDBACK_SUBMITTED",
                "eventId=" + eventId + ";feedbackId=" + saved.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public CalendarDtos.Page registeredEvents(int page) {
        featureFlags.requireEnabledForRead("MODULE_INSIGHTS");
        if (page < 0 || page > 10000) throw new IllegalArgumentException("Invalid event page");
        User actor = access.currentUser();
        var slice = registrations.findCalendarPageByUserId(actor.getId(), PageRequest.of(page, 50));
        return new CalendarDtos.Page(slice.getContent().stream().map(CalendarDtos::entry).toList(), page, slice.hasNext());
    }

    @Transactional(readOnly = true)
    public FeedbackDtos.Summary summary(long eventId) {
        featureFlags.requireEnabledForRead("MODULE_INSIGHTS");
        Event event = access.requireEventManager(eventId);
        Double mean = feedbackRepository.averageForEvent(event);
        return new FeedbackDtos.Summary(eventId, mean == null ? 0 : mean, feedbackRepository.countByEvent(event),
                feedbackRepository.findByEventOrderByCreatedAtDesc(event, PageRequest.of(0, 50)).stream().map(this::view).toList());
    }

    @Transactional(readOnly = true)
    public Optional<FeedbackDtos.View> own(long eventId) {
        featureFlags.requireEnabledForRead("MODULE_INSIGHTS");
        User actor = access.currentUser();
        Event event = eventRepository.findById(eventId).orElseThrow(DomainAccess::denied);
        return feedbackRepository.findByUserAndEvent(actor, event).map(this::view);
    }

    @Transactional(readOnly = true)
    public List<Feedback> getFeedbackForEvent(Event supplied) {
        featureFlags.requireEnabledForRead("MODULE_INSIGHTS");
        Event event = access.requireEventManager(supplied.getId());
        return feedbackRepository.findByEventOrderByCreatedAtDesc(event, PageRequest.of(0, 50));
    }

    @Transactional(readOnly = true)
    public double getAverageRating(Event supplied) {
        featureFlags.requireEnabledForRead("MODULE_INSIGHTS");
        Event event = access.requireEventManager(supplied.getId());
        Double result = feedbackRepository.averageForEvent(event);
        return result == null ? 0 : result;
    }

    @Transactional(readOnly = true)
    public boolean hasUserSubmittedFeedback(User supplied, Event event) {
        featureFlags.requireEnabledForRead("MODULE_INSIGHTS");
        return feedbackRepository.existsByUserAndEvent(access.requireSelf(supplied.getId()), event);
    }

    private FeedbackDtos.View view(Feedback f) {
        return new FeedbackDtos.View(f.getId(), f.getEvent().getId(), f.getRating(), f.getComment(), f.getCreatedAt());
    }
}

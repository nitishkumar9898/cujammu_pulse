package com.college.events.service;

import com.college.events.model.*;
import com.college.events.repository.EventRepository;
import com.college.events.repository.EventOperationsRepository;
import com.college.events.repository.RegistrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import com.college.events.dto.CalendarDtos;
import org.springframework.data.domain.PageRequest;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class RegistrationService {

    private final RegistrationRepository registrationRepository;
    private final EventRepository eventRepository;

    private final EmailService emailService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final EventOperationsRepository eventOperations;
    private final com.college.events.repository.TeamMemberRepository teamMembers;
    @Autowired private FeatureFlagService featureFlags;

    @Transactional(readOnly = true)
    public CalendarDtos.Page myCalendar(User actor, int page) {
        if (page < 0 || page > 10000) throw new IllegalArgumentException("Invalid calendar page");
        if (featureFlags != null) {
            featureFlags.requireEnabledForRead("MODULE_EVENTS");
            featureFlags.requireEnabledForRead("MODULE_CALENDAR");
        }
        var slice = registrationRepository.findCalendarPageByUserId(actor.getId(), PageRequest.of(page, 50));
        return new CalendarDtos.Page(slice.getContent().stream().map(CalendarDtos::entry).toList(), page, slice.hasNext());
    }

    @Transactional
    public Registration register(User user, Long eventId) {
        if (featureFlags != null) featureFlags.requireEnabledForUpdate("MODULE_EVENTS");
        // Serialize registration against approved attendance-policy mutation on the same event control row.
        eventOperations.findForUpdate(eventId)
            .orElseThrow(() -> new IllegalStateException("Event operations are unavailable"));
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new RuntimeException("Event not found"));

        if (event.getStatus() != EventStatus.APPROVED) {
            throw new RuntimeException("Event is not available for registration");
        }
        if (event.isPast()) {
            throw new RuntimeException("Cannot register for a past event");
        }
        if (event.getRegistrationDeadline() != null && !event.getRegistrationDeadline().isAfter(LocalDateTime.now())) {
            throw new IllegalStateException("Registration for this event has closed");
        }
        if (registrationRepository.existsByUserAndEvent(user, event)) {
            throw new RuntimeException("You are already registered for this event");
        }

        long confirmedCount = registrationRepository.countConfirmedByEvent(event);
        RegistrationStatus status = confirmedCount < event.getMaxSeats()
            ? RegistrationStatus.REGISTERED
            : RegistrationStatus.WAITLISTED;

        Registration registration = Registration.builder()
            .user(user)
            .event(event)
            .status(status)
            .build();

        Registration saved = registrationRepository.save(registration);

        if (status == RegistrationStatus.REGISTERED) {
            user.setGamificationPoints(user.getGamificationPoints() + 10);
            emailService.sendRegistrationConfirmation(saved);
            notificationService.createNotification(
                user,
                "You're registered for " + event.getTitle() + ". Secure event-pass display is not enabled yet.",
                "/student/my-tickets"
            );
        } else {
            emailService.sendWaitlistConfirmation(saved);
            notificationService.createNotification(
                user,
                "You've been added to the waitlist for " + event.getTitle() + ".",
                "/student/events/" + event.getId()
            );
        }
        notificationService.createNotification(
            event.getOrganizer(),
            user.getName() + " " + (status == RegistrationStatus.REGISTERED ? "registered" : "joined the waitlist")
                + " for " + event.getTitle() + ".",
            "/organizer/events/" + event.getId() + "/registrations"
        );
        auditService.logAction(
            user.getEmail(),
            user.getRole().name(),
            "EVENT_REGISTRATION",
            "Event ID: " + event.getId() + ", Registration ID: " + saved.getId() + ", Status: " + saved.getStatus()
        );

        log.info("User {} registered for event '{}' with status {}", user.getEmail(), event.getTitle(), status);
        return saved;
    }

    @Transactional
    public void cancelRegistration(Long registrationId, User user) {
        long eventId = registrationRepository.findOwnedEventId(registrationId, user.getId())
            .orElseThrow(com.college.events.security.DomainAccess::denied);
        eventOperations.findForUpdate(eventId)
            .orElseThrow(() -> new IllegalStateException("Event operations are unavailable"));
        // Serialize cancellation with outbox claim-time validation for this registration.
        Registration registration = registrationRepository.findForUpdate(registrationId)
            .orElseThrow(() -> new RuntimeException("Registration not found"));

        if (!registration.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized");
        }
        if (registration.getStatus() == RegistrationStatus.CANCELLED) {
            throw new RuntimeException("Already cancelled");
        }
        if (registration.getStatus() == RegistrationStatus.ATTENDED) {
            throw new RuntimeException("Cannot cancel an attended registration");
        }
        if (teamMembers.existsByUserIdAndEventId(user.getId(), eventId)) {
            throw new IllegalStateException("Team membership must be resolved by the organizer before cancellation");
        }

        boolean wasRegistered = registration.getStatus() == RegistrationStatus.REGISTERED;
        registration.setStatus(RegistrationStatus.CANCELLED);
        registration.setCancelledAt(LocalDateTime.now());
        registrationRepository.save(registration);

        emailService.sendCancellationConfirmation(registration);
        notificationService.createNotification(
            user,
            "Your registration for " + registration.getEvent().getTitle() + " has been cancelled.",
            "/student/my-tickets"
        );
        notificationService.createNotification(
            registration.getEvent().getOrganizer(),
            user.getName() + " cancelled their registration for " + registration.getEvent().getTitle() + ".",
            "/organizer/events/" + registration.getEvent().getId() + "/registrations"
        );
        auditService.logAction(
            user.getEmail(),
            user.getRole().name(),
            "REGISTRATION_CANCELLED",
            "Registration ID: " + registrationId + ", Event ID: " + registration.getEvent().getId()
        );

        // Promote first waitlisted if there was a confirmed seat freed
        if (wasRegistered) {
            promoteFromWaitlist(registration.getEvent());
        }

        log.info("User {} cancelled registration {} for event '{}'",
            user.getEmail(), registrationId, registration.getEvent().getTitle());
    }

    private void promoteFromWaitlist(Event event) {
        // Cancellation/reschedule lifecycle uses the same event control row. Never create a
        // new admission after the event has been cancelled or its start time has passed.
        if (event.getStatus() != EventStatus.APPROVED || event.isPast()
                || (event.getRegistrationDeadline() != null
                    && !event.getRegistrationDeadline().isAfter(LocalDateTime.now()))) return;
        Optional<Registration> nextWaitlisted = registrationRepository
            // Registration IDs are allocated only after acquiring this event's guard row,
            // so they preserve serialized queue order even if wall-clock timestamps tie or drift.
            .findFirstByEventAndStatusAndUser_EnabledTrueOrderByIdAsc(event, RegistrationStatus.WAITLISTED);

        nextWaitlisted.ifPresent(reg -> {
            reg.setStatus(RegistrationStatus.REGISTERED);
            registrationRepository.save(reg);
            emailService.sendWaitlistPromotion(reg);
            notificationService.createNotification(
                reg.getUser(),
                "A seat opened up for " + event.getTitle() + ". You're now confirmed.",
                "/student/my-tickets"
            );
            notificationService.createNotification(
                event.getOrganizer(),
                reg.getUser().getName() + " has been promoted from the waitlist for " + event.getTitle() + ".",
                "/organizer/events/" + event.getId() + "/registrations"
            );
            log.info("Promoted {} from waitlist for event '{}'",
                reg.getUser().getEmail(), event.getTitle());
        });
    }

    public Optional<Registration> findById(Long id) {
        return registrationRepository.findWithEventById(id);
    }

    public Optional<Registration> findByUserAndEvent(User user, Event event) {
        return registrationRepository.findByUserAndEvent(user, event);
    }

    public List<Registration> findByEvent(Event event) {
        return registrationRepository.findByEventOrderByRegisteredAtAsc(event);
    }

    public List<Registration> findByUser(User user) {
        return registrationRepository.findByUserOrderByRegisteredAtDesc(user);
    }

    public long countConfirmed(Event event) {
        return registrationRepository.countConfirmedByEvent(event);
    }

    public long countWaitlisted(Event event) {
        return registrationRepository.countWaitlistedByEvent(event);
    }

    public long countAll() {
        return registrationRepository.count();
    }

    public long countByStatus(RegistrationStatus status) {
        return registrationRepository.countByStatus(status);
    }
}

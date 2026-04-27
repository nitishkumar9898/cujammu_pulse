package com.college.events.service;

import com.college.events.model.*;
import com.college.events.repository.EventRepository;
import com.college.events.repository.RegistrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    @Transactional
    public Registration register(User user, Long eventId) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new RuntimeException("Event not found"));

        if (event.getStatus() != EventStatus.APPROVED) {
            throw new RuntimeException("Event is not available for registration");
        }
        if (event.isPast()) {
            throw new RuntimeException("Cannot register for a past event");
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
            .qrToken(UUID.randomUUID().toString())
            .build();

        Registration saved = registrationRepository.save(registration);

        if (status == RegistrationStatus.REGISTERED) {
            user.setGamificationPoints(user.getGamificationPoints() + 10);
            emailService.sendRegistrationConfirmation(saved);
            notificationService.createNotification(
                user,
                "You're registered for " + event.getTitle() + ". Your QR ticket is ready.",
                "/student/ticket/" + saved.getId()
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
        Registration registration = registrationRepository.findById(registrationId)
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
        Optional<Registration> nextWaitlisted = registrationRepository
            .findFirstByEventAndStatusOrderByRegisteredAtAsc(event, RegistrationStatus.WAITLISTED);

        nextWaitlisted.ifPresent(reg -> {
            reg.setStatus(RegistrationStatus.REGISTERED);
            registrationRepository.save(reg);
            emailService.sendWaitlistPromotion(reg);
            notificationService.createNotification(
                reg.getUser(),
                "A seat opened up for " + event.getTitle() + ". You're now confirmed.",
                "/student/ticket/" + reg.getId()
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

    public Optional<Registration> findByQrToken(String token) {
        return registrationRepository.findByQrToken(token);
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

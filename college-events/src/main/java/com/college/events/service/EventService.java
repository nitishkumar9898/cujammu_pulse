package com.college.events.service;

import com.college.events.dto.EventDto;
import com.college.events.model.*;
import com.college.events.repository.CategoryRepository;
import com.college.events.repository.EventRepository;
import com.college.events.repository.RegistrationRepository;
import com.college.events.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

    @Transactional
    @CacheEvict(value = "events", allEntries = true)
    public Event createEvent(EventDto dto, User organizer) {
        Category category = categoryRepository.findById(dto.getCategoryId())
            .orElseThrow(() -> new RuntimeException("Category not found"));

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
            .organizer(organizer)
            .venue(sanitizeInput(dto.getVenue()))
            .eventDate(dto.getEventDate())
            .maxSeats(dto.getMaxSeats())
            .fees(dto.getFees() != null ? dto.getFees() : 0.0)
            .contactEmail(dto.getContactEmail())
            .bannerImage(bannerFilename)
            .targetDepartment(dto.getTargetDepartment() != null ? dto.getTargetDepartment() : "ALL")
            .status(EventStatus.PENDING)
            .generateCertificate(dto.isGenerateCertificate())
            .certificateTemplatePath(certificateTemplateFilename)
            .build();

        Event saved = eventRepository.save(event);
        log.info("Created event '{}' by {}", saved.getTitle(), organizer.getEmail());
        return saved;
    }

    @Transactional
    @CacheEvict(value = "events", allEntries = true)
    public Event updateEvent(Long eventId, EventDto dto, User organizer) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new RuntimeException("Event not found"));

        if (!event.getOrganizer().getId().equals(organizer.getId())) {
            throw new RuntimeException("Unauthorized: You can only edit your own events");
        }

        Category category = categoryRepository.findById(dto.getCategoryId())
            .orElseThrow(() -> new RuntimeException("Category not found"));

        if (dto.getBannerImage() != null && !dto.getBannerImage().isEmpty()) {
            if (event.getBannerImage() != null) {
                fileStorageService.deleteFile(event.getBannerImage());
            }
            event.setBannerImage(fileStorageService.storeFile(dto.getBannerImage()));
        }

        event.setTitle(sanitizeInput(dto.getTitle()));
        event.setDescription(sanitizeInput(dto.getDescription()));
        event.setCategory(category);
        event.setVenue(sanitizeInput(dto.getVenue()));
        event.setEventDate(dto.getEventDate());
        event.setMaxSeats(dto.getMaxSeats());
        event.setFees(dto.getFees() != null ? dto.getFees() : 0.0);
        event.setContactEmail(dto.getContactEmail());
        if (dto.getTargetDepartment() != null) {
            event.setTargetDepartment(dto.getTargetDepartment());
        }
        
        event.setGenerateCertificate(dto.isGenerateCertificate());
        if (dto.getCertificateTemplate() != null && !dto.getCertificateTemplate().isEmpty()) {
            if (event.getCertificateTemplatePath() != null) {
                fileStorageService.deleteFile(event.getCertificateTemplatePath());
            }
            event.setCertificateTemplatePath(fileStorageService.storeFile(dto.getCertificateTemplate()));
        }

        event.setStatus(EventStatus.PENDING); // Re-submit for approval

        return eventRepository.save(event);
    }

    @Transactional
    @CacheEvict(value = "events", allEntries = true)
    public void approveEvent(Long eventId) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new RuntimeException("Event not found"));
        event.setStatus(EventStatus.APPROVED);
        event.setApprovedAt(LocalDateTime.now());
        event.setRejectionReason(null);
        eventRepository.save(event);
        emailService.sendEventApprovalNotification(event);

        // Send new-event announcement email to target students
        sendNewEventAnnouncementEmails(event);

        log.info("Event approved: {}", event.getTitle());
    }

    private void sendNewEventAnnouncementEmails(Event event) {
        try {
            List<User> targetStudents;
            if ("ALL".equalsIgnoreCase(event.getTargetDepartment())) {
                targetStudents = userRepository.findByRole(Role.STUDENT);
            } else {
                targetStudents = userRepository.findByRoleAndDepartment(Role.STUDENT, event.getTargetDepartment());
            }
            emailService.sendNewEventAnnouncement(event, targetStudents);
            log.info("New event announcement queued for {} students for event '{}'",
                targetStudents.size(), event.getTitle());
        } catch (Exception e) {
            log.error("Failed to send new event announcement emails", e);
        }
    }

    @Transactional
    @CacheEvict(value = "events", allEntries = true)
    public void rejectEvent(Long eventId, String reason) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new RuntimeException("Event not found"));
        event.setStatus(EventStatus.REJECTED);
        event.setRejectionReason(reason);
        eventRepository.save(event);
        emailService.sendEventRejectionNotification(event);
        log.info("Event rejected: {} | Reason: {}", event.getTitle(), reason);
    }

    @Transactional
    @CacheEvict(value = "events", allEntries = true)
    public void markCompleted(Long eventId, User organizer) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new RuntimeException("Event not found"));
        if (!event.getOrganizer().getId().equals(organizer.getId())) {
            throw new RuntimeException("Unauthorized");
        }
        event.setStatus(EventStatus.COMPLETED);
        eventRepository.save(event);
    }

    @Transactional
    @CacheEvict(value = "events", allEntries = true)
    public void cancelEvent(Long eventId, User requester) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new RuntimeException("Event not found"));

        boolean isAdmin = requester.getRole() == Role.ADMIN;
        boolean isOwner = event.getOrganizer().getId().equals(requester.getId());
        if (!isAdmin && !isOwner) throw new RuntimeException("Unauthorized");

        event.setStatus(EventStatus.CANCELLED);
        event.setCancelledAt(LocalDateTime.now());
        eventRepository.save(event);

        // Notify all registered students
        List<Registration> registrations = registrationRepository
            .findByEventOrderByRegisteredAtAsc(event)
            .stream()
            .filter(r -> r.getStatus() == RegistrationStatus.REGISTERED
                      || r.getStatus() == RegistrationStatus.WAITLISTED)
            .toList();
        emailService.sendEventCancellationNotification(event, registrations);
        log.info("Event '{}' cancelled by {}. {} students notified.",
            event.getTitle(), requester.getEmail(), registrations.size());
    }

    @Transactional
    @CacheEvict(value = "events", allEntries = true)
    public void postponeEvent(Long eventId, LocalDateTime newDate, String reason, User requester) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new RuntimeException("Event not found"));

        boolean isAdmin = requester.getRole() == Role.ADMIN;
        boolean isOwner = event.getOrganizer().getId().equals(requester.getId());
        if (!isAdmin && !isOwner) throw new RuntimeException("Unauthorized");

        event.setEventDate(newDate);
        event.setPostponeReason(reason);
        eventRepository.save(event);

        // Notify all registered students
        List<Registration> registrations = registrationRepository
            .findByEventOrderByRegisteredAtAsc(event)
            .stream()
            .filter(r -> r.getStatus() == RegistrationStatus.REGISTERED
                      || r.getStatus() == RegistrationStatus.WAITLISTED)
            .toList();
        emailService.sendEventPostponementNotification(event, registrations);
        log.info("Event '{}' postponed to {}. {} students notified.",
            event.getTitle(), newDate, registrations.size());
    }

    @Transactional
    @CacheEvict(value = "events", allEntries = true)
    public void deleteEvent(Long eventId, User requester) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new RuntimeException("Event not found"));
        boolean isAdmin = requester.getRole() == Role.ADMIN;
        boolean isOwner = event.getOrganizer().getId().equals(requester.getId());
        if (!isAdmin && !isOwner) {
            throw new RuntimeException("Unauthorized");
        }
        if (event.getBannerImage() != null) {
            fileStorageService.deleteFile(event.getBannerImage());
        }
        eventRepository.delete(event);
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

    /** Auto-hide/complete events that are past their event date */
    @Transactional
    public void autoCompleteExpiredEvents() {
        LocalDateTime now = LocalDateTime.now();
        List<Event> expired = eventRepository.findByStatus(EventStatus.APPROVED)
            .stream()
            .filter(e -> e.getEventDate() != null && e.getEventDate().isBefore(now))
            .toList();

        for (Event event : expired) {
            event.setStatus(EventStatus.COMPLETED);
            eventRepository.save(event);
            log.info("Auto-completed expired event: {}", event.getTitle());
        }
    }

    private String sanitizeInput(String input) {
        if (input == null) return null;
        return input.trim()
            .replaceAll("<script.*?>.*?</script>", "")
            .replaceAll("<.*?\\s+on.*?>", "")
            .replaceAll("<[^>]*>", "");
    }
}

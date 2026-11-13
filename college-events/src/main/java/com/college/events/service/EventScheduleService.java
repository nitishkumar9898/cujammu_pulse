package com.college.events.service;

import com.college.events.dto.FacilityAvailabilityDtos;
import com.college.events.model.Event;
import com.college.events.model.EventStatus;
import com.college.events.model.EventScheduleChange;
import com.college.events.model.EventLifecycleCommandReceipt;
import com.college.events.model.User;
import com.college.events.repository.EventOperationsRepository;
import com.college.events.repository.FacilityBookingRepository;
import com.college.events.repository.FacilityRepository;
import com.college.events.repository.EventScheduleChangeRepository;
import com.college.events.repository.EventLifecycleCommandReceiptRepository;
import com.college.events.security.DomainAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

/** Coordinates event lifecycle with the associated campus facility reservation. */
@Service
@RequiredArgsConstructor
public class EventScheduleService {
    private final DomainAccess access;
    private final FacilityRepository facilities;
    private final FacilityBookingRepository bookings;
    private final EventOperationsRepository operations;
    private final EventService eventService;
    private final CUJammuMasterService campusFacilities;
    private final AuditService audit;
    /** Optional for source-compatible isolated unit tests; injected in production. */
    @Autowired(required = false) private EventScheduleChangeRepository scheduleChanges;
    @Autowired(required = false) private EventLifecycleCommandReceiptRepository lifecycleReceipts;
    /** Optional for source-compatible isolated tests; production injects the runtime control plane. */
    @Autowired(required = false) private FeatureFlagService featureFlags;

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<com.college.events.dto.EventOperationsDtos.ScheduleChangeView> history(long eventId) {
        access.requireEventManager(eventId);
        if (scheduleChanges == null) return List.of();
        return scheduleChanges.findByEventIdOrderByChangeNumberAsc(eventId).stream()
                .map(change -> new com.college.events.dto.EventOperationsDtos.ScheduleChangeView(
                        change.getId(), change.getChangeNumber(), change.getChangeType(), change.getPreviousDate(),
                        change.getReplacementDate(), change.getPublicReason(), change.getActorRole(), change.getRecordedAt()))
                .toList();
    }

    @org.springframework.transaction.annotation.Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void cancel(long eventId) {
        cancel(eventId, UUID.randomUUID().toString(), null);
    }

    @org.springframework.transaction.annotation.Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void cancel(long eventId, String requestKey) {
        cancel(eventId, requestKey, null);
    }

    @org.springframework.transaction.annotation.Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void cancel(long eventId, String requestKey, Long expectedRevision) {
        requireEventAction("ACTION_EVENT_CANCEL");
        String key = normalizedKey(requestKey);
        operations.findForUpdate(eventId).orElseThrow(() -> new IllegalStateException("Event lifecycle controls are unavailable"));
        Event event = access.requireEventManager(eventId);
        String payloadHash = hash("CANCEL|expected=" + expectedRevision);
        if (replayed(eventId, key, "CANCEL", payloadHash)) return;
        assertExpectedRevision(eventId, expectedRevision);
        var actor = access.currentUser();
        if (event.getStatus() == EventStatus.COMPLETED) throw new IllegalStateException("Completed events require a reviewed correction workflow");
        if (event.getStatus() == EventStatus.CANCELLED) return;
        var previousDate = event.getEventDate();
        var linked = bookings.findActiveByEventIdOrderByStartTimeAsc(eventId);
        if (linked.size() > 1) throw new IllegalStateException("Multiple active venue records require reconciliation before cancellation");
        if (linked.size() == 1) {
            var original = linked.get(0);
            var facility = facilities.findForBooking(original.getFacility().getId())
                    .orElseThrow(() -> new IllegalStateException("The linked facility record is unavailable"));
            var reservation = bookings.findForUpdate(original.getId())
                    .orElseThrow(() -> new IllegalStateException("The linked venue reservation changed; reload and retry"));
            if (!reservation.getEvent().getId().equals(eventId) || !reservation.getFacility().getId().equals(facility.getId())) {
                throw new IllegalStateException("The linked venue reservation changed; reload and retry");
            }
            if (List.of("PENDING", "CONFIRMED").contains(reservation.getStatus())) {
                reservation.setStatus("CANCELLED");
                reservation.setReviewedAt(java.time.LocalDateTime.now());
                reservation.setReviewedBy(actor);
                reservation.setReviewReason("Event cancelled by authorized event manager");
                bookings.saveAndFlush(reservation);
            }
        }
        eventService.cancelEvent(eventId, actor);
        recordChange(event, "CANCELLED", previousDate, null, "Event cancelled by authorized event manager", actor);
        audit.logAction(actor.getEmail(), actor.getRole().name(), "EVENT_CANCELLED_WITH_RESOURCE_RELEASE",
                "eventId=" + eventId);
        recordReceipt(eventId, key, "CANCEL", payloadHash, "CANCELLED");
    }

    @org.springframework.transaction.annotation.Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void reschedule(long eventId, java.time.LocalDateTime newDate, String reason) {
        reschedule(eventId, newDate, reason, UUID.randomUUID().toString(), null);
    }

    @org.springframework.transaction.annotation.Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void reschedule(long eventId, java.time.LocalDateTime newDate, String reason, String requestKey) {
        reschedule(eventId, newDate, reason, requestKey, null);
    }

    @org.springframework.transaction.annotation.Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void reschedule(long eventId, java.time.LocalDateTime newDate, String reason, String requestKey, Long expectedRevision) {
        requireEventAction("ACTION_EVENT_POSTPONE");
        operations.findForUpdate(eventId).orElseThrow(() -> new IllegalStateException("Event lifecycle controls are unavailable"));
        Event event = access.requireEventManager(eventId);
        var actor = access.currentUser();
        if (event.getStatus() != EventStatus.APPROVED) throw new IllegalStateException("Only approved events can be rescheduled");
        if (newDate == null || !newDate.isAfter(java.time.LocalDateTime.now())) throw new IllegalArgumentException("Replacement date must be in the future");
        if (reason == null || reason.isBlank() || reason.trim().length() > 300) throw new IllegalArgumentException("A bounded reschedule reason is required");
        reason = reason.trim();
        String key = normalizedKey(requestKey);
        String payloadHash = hash("RESCHEDULE|" + newDate + "|" + reason + "|expected=" + expectedRevision);
        if (replayed(eventId, key, "RESCHEDULE", payloadHash)) return;
        assertExpectedRevision(eventId, expectedRevision);

        var linked = bookings.findActiveByEventIdOrderByStartTimeAsc(eventId);
        if (linked.size() != 1) throw new IllegalStateException("Rescheduling requires exactly one linked venue reservation; contact the venue office to reconcile this event");
        var original = linked.get(0);
        var previousDate = event.getEventDate();
        if (previousDate != null && previousDate.equals(newDate) && java.util.Objects.equals(event.getPostponeReason(), reason)) return;
        if (event.getVenue() == null || !original.getFacility().getName().equals(event.getVenue().trim())) {
            throw new IllegalStateException("The event venue and linked reservation do not match; contact the venue office");
        }
        var facility = facilities.findForBooking(original.getFacility().getId())
                .filter(com.college.events.model.Facility::isActive)
                .orElseThrow(() -> new IllegalStateException("The linked campus facility is unavailable"));
        var reservation = bookings.findForUpdate(original.getId())
                .orElseThrow(() -> new IllegalStateException("The linked venue reservation changed; reload and retry"));
        if (!reservation.getEvent().getId().equals(eventId) || !reservation.getFacility().getId().equals(facility.getId())
                || !List.of("PENDING", "CONFIRMED").contains(reservation.getStatus())) {
            throw new IllegalStateException("The linked venue reservation is no longer reschedulable");
        }
        java.time.Duration duration = java.time.Duration.between(reservation.getStartTime(), reservation.getEndTime());
        if (duration.isZero() || duration.isNegative() || duration.compareTo(java.time.Duration.ofDays(7)) > 0) {
            throw new IllegalStateException("The linked venue reservation has an invalid duration");
        }
        var newEnd = newDate.plus(duration);
        var conflicts = bookings.findConflictingBookingsExcept(facility.getId(), newDate, newEnd, reservation.getId());
        if (!conflicts.isEmpty()) throw new IllegalStateException("The suggested time is no longer available; request fresh suggestions");

        reservation.setStartTime(newDate);
        reservation.setEndTime(newEnd);
        bookings.saveAndFlush(reservation);
        eventService.postponeEvent(eventId, newDate, reason, actor);
        recordChange(event, "RESCHEDULED", previousDate, newDate, reason, actor);
        audit.logAction(actor.getEmail(), actor.getRole().name(), "EVENT_RESCHEDULED_WITH_RESOURCE_MOVE",
                "eventId=" + eventId + ";reservationId=" + reservation.getId() + ";start=" + newDate + ";end=" + newEnd);
        recordReceipt(eventId, key, "RESCHEDULE", payloadHash, "RESCHEDULED");
    }

    public FacilityAvailabilityDtos.Suggestions suggestReschedule(long eventId, int durationMinutes, int days) {
        Event event = access.requireEventManager(eventId);
        if (event.getStatus() != EventStatus.APPROVED) throw new IllegalStateException("Only approved events can be rescheduled");
        if (event.getVenue() == null || event.getVenue().isBlank()) {
            throw new IllegalArgumentException("This event has no registered campus facility mapping");
        }
        var facility = facilities.findByName(event.getVenue().trim())
                .filter(com.college.events.model.Facility::isActive)
                .orElseThrow(() -> new IllegalArgumentException("This event venue does not match an active campus facility"));
        return campusFacilities.suggestFacilitySlots(facility.getId(), durationMinutes, days);
    }

    private void recordChange(Event event, String type, java.time.LocalDateTime previousDate,
                              java.time.LocalDateTime replacementDate, String reason, User actor) {
        if (scheduleChanges == null) return;
        long next = scheduleChanges.findTopByEventIdOrderByChangeNumberDesc(event.getId())
                .map(value -> value.getChangeNumber() + 1).orElse(1L);
        scheduleChanges.save(EventScheduleChange.builder().event(event).changeNumber(next).changeType(type)
                .previousDate(previousDate).replacementDate(replacementDate).publicReason(reason.trim())
                .actorId(actor.getId()).actorEmail(actor.getEmail()).actorRole(actor.getRole().name()).build());
    }

    private boolean replayed(long eventId, String requestKey, String commandType, String payloadHash) {
        if (lifecycleReceipts == null) return false;
        var existing = lifecycleReceipts.findByEventIdAndRequestKey(eventId, requestKey).orElse(null);
        if (existing == null) return false;
        if (!existing.getCommandType().equals(commandType) || !existing.getPayloadHash().equals(payloadHash))
            throw new IllegalArgumentException("Request identifier was already used with different lifecycle details");
        return true;
    }

    private void assertExpectedRevision(long eventId, Long expectedRevision) {
        if (expectedRevision == null || scheduleChanges == null) return;
        long current = scheduleChanges.findTopByEventIdOrderByChangeNumberDesc(eventId)
                .map(EventScheduleChange::getChangeNumber).orElse(0L);
        if (current != expectedRevision) {
            throw new IllegalStateException("The event schedule changed; reload the lifecycle workspace and retry");
        }
    }

    private void recordReceipt(long eventId, String requestKey, String commandType, String payloadHash, String result) {
        if (lifecycleReceipts == null) return;
        lifecycleReceipts.saveAndFlush(EventLifecycleCommandReceipt.builder().eventId(eventId).requestKey(requestKey)
                .commandType(commandType).payloadHash(payloadHash).resultStatus(result).build());
    }

    private static String normalizedKey(String value) {
        if (value == null || value.isBlank()) return UUID.randomUUID().toString();
        try { return UUID.fromString(value).toString(); }
        catch (RuntimeException invalid) { throw new IllegalArgumentException("A valid request identifier is required"); }
    }

    private void requireAction(String key) {
        if (featureFlags == null) throw new IllegalStateException("Event runtime controls are unavailable");
        featureFlags.requireEnabledForUpdate(key);
    }

    /** Acquire runtime-control locks before lifecycle/resource locks, matching EventService's global order. */
    private void requireEventAction(String actionKey) {
        requireAction("MODULE_EVENTS");
        requireAction(actionKey);
    }

    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException("Hash unavailable", ex); }
    }
}

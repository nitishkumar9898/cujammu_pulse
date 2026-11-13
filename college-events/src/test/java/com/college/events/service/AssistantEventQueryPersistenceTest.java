package com.college.events.service;

import com.college.events.model.*;
import com.college.events.repository.EventRepository;
import com.college.events.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real legacy JPA/H2 paging proof; this is not Mongo replica-set evidence. */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never",
        "logging.file.name=target/test-logs/assistant-query.log"}, showSql = false)
class AssistantEventQueryPersistenceTest {
    @Autowired EventRepository events;
    @Autowired UserRepository users;

    @Test void repositoryAppliesLimitsApprovalFilterAndStableDateIdOrder() {
        LocalDateTime now = LocalDateTime.of(2030, 1, 1, 12, 0);
        User owner = users.saveAndFlush(User.builder().name("Synthetic Organizer")
                .email(UUID.randomUUID()+"@example.invalid").password("synthetic-hash").role(Role.ORGANIZER).enabled(true).build());
        List<Long> futureIds = new ArrayList<>();
        List<Long> pastIds = new ArrayList<>();
        for (int i = 0; i < 16; i++) futureIds.add(save(owner, now.plusDays(1), EventStatus.APPROVED).getId());
        for (int i = 0; i < 8; i++) pastIds.add(save(owner, now.minusDays(1), EventStatus.APPROVED).getId());
        save(owner, now.plusMinutes(1), EventStatus.PENDING);
        save(owner, now.minusMinutes(1), EventStatus.PENDING);
        events.flush();

        var upcoming = events.findUpcomingApprovedEvents(now, PageRequest.of(0, 10));
        var past = events.findPastApprovedEvents(now, PageRequest.of(0, 5));
        assertEquals(futureIds.subList(0, 10), upcoming.stream().map(Event::getId).toList());
        Collections.reverse(pastIds);
        assertEquals(pastIds.subList(0, 5), past.stream().map(Event::getId).toList());
        assertTrue(upcoming.stream().allMatch(e -> e.getStatus() == EventStatus.APPROVED));
        assertTrue(past.stream().allMatch(e -> e.getStatus() == EventStatus.APPROVED));
        assertEquals(16, events.countByStatusAndEventDateAfter(EventStatus.APPROVED, now));
    }

    private Event save(User owner, LocalDateTime date, EventStatus status) {
        return events.save(Event.builder().title("Synthetic query fixture").description("Synthetic only")
                .organizer(owner).venue("Synthetic hall").maxSeats(10).eventDate(date).status(status).build());
    }

    @Test void todayAndFreePredicatesAreAppliedBeforePageLimit() {
        LocalDateTime start = LocalDateTime.of(2031, 1, 1, 0, 0);
        User owner = users.saveAndFlush(User.builder().name("Synthetic Organizer")
                .email(UUID.randomUUID()+"@example.invalid").password("synthetic-hash").role(Role.ORGANIZER).enabled(true).build());
        for (int i = 0; i < 12; i++) {
            Event paid = save(owner, start.plusHours(1).plusMinutes(i), EventStatus.APPROVED);
            paid.setFees(100.0); events.save(paid);
        }
        Event free = save(owner, start.plusHours(2), EventStatus.APPROVED);
        Event midnight = save(owner, start, EventStatus.APPROVED);
        save(owner, start.plusDays(1), EventStatus.APPROVED);
        save(owner, start.minusSeconds(1), EventStatus.APPROVED);
        events.flush();
        assertTrue(events.findUpcomingFreeApprovedEvents(start.plusMinutes(1), PageRequest.of(0, 10))
                .stream().anyMatch(e -> e.getId().equals(free.getId())));
        var today = events.findApprovedEventsInDay(start, start.plusDays(1), PageRequest.of(0, 20));
        assertEquals(14, today.size());
        assertEquals(midnight.getId(), today.getFirst().getId());
        assertTrue(today.stream().allMatch(e -> !e.getEventDate().isBefore(start) && e.getEventDate().isBefore(start.plusDays(1))));
    }
}

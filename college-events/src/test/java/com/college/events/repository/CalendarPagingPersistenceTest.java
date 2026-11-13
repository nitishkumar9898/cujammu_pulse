package com.college.events.repository;

import com.college.events.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never"}, showSql=false)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class CalendarPagingPersistenceTest {
    @Autowired UserRepository users;
    @Autowired EventRepository events;
    @Autowired RegistrationRepository registrations;
    @Autowired PlatformTransactionManager transactions;

    @Test void calendarPagesAreOwnerScopedBoundedAndStableAcrossEqualEventTimes() {
        Long[] ownerId = new Long[1];
        Long[] outsiderId = new Long[1];
        new TransactionTemplate(transactions).execute(status -> {
            User owner = users.saveAndFlush(user());
            User outsider = users.saveAndFlush(user());
            User organizer = users.saveAndFlush(user(Role.ORGANIZER));
            ownerId[0] = owner.getId(); outsiderId[0] = outsider.getId();
            for (int index = 0; index < 51; index++) {
                Event event = events.saveAndFlush(Event.builder().title("Synthetic calendar event " + index)
                        .description("Synthetic").venue("Synthetic hall")
                        .eventDate(LocalDateTime.of(2030, 4, 2, 9, 30)).maxSeats(100)
                        .organizer(organizer).status(EventStatus.APPROVED).build());
                registrations.saveAndFlush(Registration.builder().event(event).user(owner)
                        .status(RegistrationStatus.REGISTERED).build());
            }
            return null;
        });

        var first = registrations.findCalendarPageByUserId(ownerId[0], PageRequest.of(0, 50));
        assertEquals(50, first.getContent().size());
        assertTrue(first.hasNext());
        var second = registrations.findCalendarPageByUserId(ownerId[0], PageRequest.of(1, 50));
        assertEquals(1, second.getContent().size());
        assertFalse(second.hasNext());
        assertTrue(registrations.findCalendarPageByUserId(outsiderId[0], PageRequest.of(0, 50)).isEmpty());
        assertTrue(first.getContent().get(0).getId() < first.getContent().get(49).getId());
        assertTrue(first.getContent().get(49).getId() < second.getContent().get(0).getId());
    }

    private User user() { return user(Role.STUDENT); }
    private User user(Role role) { return User.builder().name("Synthetic calendar user")
            .email(UUID.randomUUID() + "@example.invalid").password("synthetic-hash").enabled(true).role(role).build(); }
}

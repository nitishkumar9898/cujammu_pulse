package com.college.events;

import com.college.events.model.*;
import com.college.events.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:calendar-clash-http;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never", "app.default-admin-password=",
        "app.upload.dir=target/test-uploads/calendar-clash-http", "logging.file.name=target/test-logs/calendar-clash-http.log",
        "app.media.cleanup-cron=-", "app.identity.enabled=false", "app.email.backend=console", "spring.cache.type=none"})
@AutoConfigureMockMvc
@DirtiesContext
class CalendarClashHttpTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired EventRepository events;
    @Autowired RegistrationRepository registrations;
    @Autowired FeatureFlagRepository flags;
    @Autowired ObjectMapper json;
    User owner, outsider;
    Event target;
    final LocalDateTime start = LocalDateTime.of(2035, 4, 2, 10, 0);

    @BeforeEach void setup() {
        owner = account(); outsider = account();
        target = event("Target event", start, start.plusHours(2), EventStatus.APPROVED);
        for (var key : new String[]{"MODULE_EVENTS", "MODULE_CALENDAR", "ACTION_CALENDAR_CLASH_CHECK"}) toggle(key, true);
    }
    @Test void privateBoundedQueryUsesHalfOpenIntervalsAndDoesNotMutateRegistration() throws Exception {
        enroll(owner, event("Own overlap", start.plusMinutes(30), start.plusHours(3), EventStatus.APPROVED), RegistrationStatus.REGISTERED);
        enroll(outsider, event("Private outsider title", start, start.plusHours(1), EventStatus.APPROVED), RegistrationStatus.REGISTERED);
        enroll(owner, event("Ends at start", start.minusHours(1), start, EventStatus.APPROVED), RegistrationStatus.REGISTERED);
        enroll(owner, event("Starts at end", start.plusHours(2), start.plusHours(3), EventStatus.APPROVED), RegistrationStatus.REGISTERED);
        enroll(owner, event("Cancelled event", start, start.plusHours(1), EventStatus.CANCELLED), RegistrationStatus.REGISTERED);
        enroll(owner, event("Waitlisted event", start, start.plusHours(1), EventStatus.APPROVED), RegistrationStatus.WAITLISTED);
        enroll(owner, event("Withdrawn event", start, start.plusHours(1), EventStatus.APPROVED), RegistrationStatus.CANCELLED);
        long count = registrations.count();
        var body = mvc.perform(get(url()).with(user(owner))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].entry.title").value("Own overlap"))
                .andExpect(jsonPath("$.items[0].assessment").value("OVERLAP"))
                .andExpect(jsonPath("$.items[0].entry.user").doesNotExist()).andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("Private outsider title")); assertEquals(count, registrations.count());
        assertFalse(registrations.existsByUserAndEvent(owner, target));
        mvc.perform(get(url()+"?userId="+owner.getId()).with(user(outsider)))
                .andExpect(jsonPath("$.items[0].entry.title").value("Private outsider title"));
    }
    @Test void unknownDurationsRemainCandidatesAndPaginationNeverClaimsCompleteness() throws Exception {
        enroll(owner, event("Old unknown end", start.minusDays(1), null, EventStatus.APPROVED), RegistrationStatus.REGISTERED);
        enroll(owner, event("Malformed historical end", start.minusHours(1), start.minusHours(2), EventStatus.APPROVED), RegistrationStatus.REGISTERED);
        for (int i = 0; i < 49; i++) enroll(owner, event("Unknown historical "+i, start.minusDays(2), null, EventStatus.APPROVED), RegistrationStatus.REGISTERED);
        enroll(owner, event("Definite overlap survives unknown history", start, start.plusHours(1), EventStatus.APPROVED), RegistrationStatus.REGISTERED);
        mvc.perform(get(url()).with(user(owner))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(50)).andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.items[0].assessment").value("OVERLAP"))
                .andExpect(jsonPath("$.items[0].entry.title").value("Definite overlap survives unknown history"))
                .andExpect(jsonPath("$.items[1].assessment").value("UNKNOWN_DURATION"));
    }
    @Test void unknownTargetDurationDoesNotExcludeFutureCommitments() throws Exception {
        target.setEventEndsAt(null); target = events.saveAndFlush(target);
        enroll(owner, event("Later confirmed event", start.plusDays(2), start.plusDays(2).plusHours(1), EventStatus.APPROVED), RegistrationStatus.REGISTERED);
        mvc.perform(get(url()).with(user(owner))).andExpect(status().isOk())
                .andExpect(jsonPath("$.endsAt").isEmpty())
                .andExpect(jsonPath("$.items[0].assessment").value("UNKNOWN_DURATION"));
    }
    @Test void authenticationAndEachRuntimeDependencyDenyPrivateReads() throws Exception {
        mvc.perform(get(url())).andExpect(status().isUnauthorized());
        for (var key : new String[]{"ACTION_CALENDAR_CLASH_CHECK", "MODULE_CALENDAR", "MODULE_EVENTS"}) {
            toggle(key, false);
            mvc.perform(get(url()).with(user(owner))).andExpect(status().isNotFound());
            toggle(key, true);
        }
        target.setStatus(EventStatus.PENDING); events.saveAndFlush(target);
        mvc.perform(get(url()).with(user(owner))).andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.error").value("REQUEST_UNAVAILABLE"));
    }
    @Test void ownAgendaCarriesCurrentLifecycleAndRevisionAfterCancellation() throws Exception {
        enroll(owner, target, RegistrationStatus.REGISTERED);
        target.setStatus(EventStatus.CANCELLED); target = events.saveAndFlush(target);
        mvc.perform(get("/api/v1/cujammu/events/calendar/mine").with(user(owner))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].eventStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.items[0].lifecycleVersion").value(target.getLifecycleVersion()))
                .andExpect(jsonPath("$.items[0].status").value("REGISTERED"));
    }
    private String url() { return "/api/v1/cujammu/events/calendar/clashes/"+target.getId(); }
    private void toggle(String key, boolean enabled) { var flag=flags.findByFeatureKey(key).orElseThrow();flag.setEnabled(enabled);flags.saveAndFlush(flag); }
    private User account() { return users.saveAndFlush(User.builder().name("Synthetic").email(UUID.randomUUID()+"@example.invalid").password("synthetic-hash").enabled(true).role(Role.STUDENT).build()); }
    private Event event(String title, LocalDateTime from, LocalDateTime to, EventStatus status) { return events.saveAndFlush(Event.builder().organizer(owner).title(title).description("Synthetic private schedule").venue("Synthetic hall").eventDate(from).eventEndsAt(to).status(status).maxSeats(100).build()); }
    private void enroll(User person, Event event, RegistrationStatus status) { registrations.saveAndFlush(Registration.builder().user(person).event(event).status(status).build()); }
}

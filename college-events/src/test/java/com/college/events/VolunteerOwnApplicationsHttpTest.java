package com.college.events;

import com.college.events.model.*;
import com.college.events.repository.*;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:volunteer-own;DB_CLOSE_DELAY=-1","spring.jpa.hibernate.ddl-auto=create-drop","spring.sql.init.mode=never","app.default-admin-password=","app.upload.dir=target/test-uploads/volunteer-own","app.media.cleanup-cron=-","app.identity.enabled=false","app.email.backend=console","spring.cache.type=none"})
@AutoConfigureMockMvc
class VolunteerOwnApplicationsHttpTest {
    private static final String URL="/api/v1/cujammu/events/operations/my-volunteer-applications";
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired EventRepository events;
    @Autowired CategoryRepository categories;
    @Autowired VolunteerMembershipRepository memberships;
    @Autowired FeatureFlagRepository flags;
    private User student,other,organizer;
    @BeforeEach void setup() {
        student=account(Role.STUDENT); other=account(Role.STUDENT); organizer=account(Role.ORGANIZER);
        var module=flags.findByFeatureKey("MODULE_EVENTS").orElseGet(()->FeatureFlag.builder().featureKey("MODULE_EVENTS").displayName("Events").build());
        module.setEnabled(true); flags.saveAndFlush(module);
    }
    private User account(Role role) {
        return users.saveAndFlush(User.builder().name("Synthetic account").email(UUID.randomUUID()+"@example.invalid")
                .password("synthetic").role(role).enabled(true).build());
    }
    private VolunteerMembership application(User applicant, EventStatus eventStatus, String status) {
        var category=categories.saveAndFlush(Category.builder().name("Synthetic "+UUID.randomUUID()).build());
        var event=events.saveAndFlush(Event.builder().title("Synthetic volunteer event").description("Synthetic programme description.")
                .organizer(organizer).category(category).venue("Synthetic room").eventDate(LocalDateTime.now().plusDays(2))
                .maxSeats(20).fees(0.0).status(eventStatus).build());
        return memberships.saveAndFlush(VolunteerMembership.builder().event(event).user(applicant).status(status)
                .verifiedIdentifier("00123").build());
    }
    @Test void returnsOnlyOwnHistoryWithoutPrivateRosterFields() throws Exception {
        var own=application(student,EventStatus.APPROVED,"APPLIED");
        application(other,EventStatus.APPROVED,"ACCEPTED");
        mvc.perform(get(URL).param("userId",other.getId().toString()).with(user(student)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].id").value(own.getId()))
                .andExpect(jsonPath("$.items[0].status").value("APPLIED"))
                .andExpect(jsonPath("$.items[0].identifier").doesNotExist()).andExpect(jsonPath("$.items[0].userId").doesNotExist())
                .andExpect(jsonPath("$.items[0].email").doesNotExist()).andExpect(jsonPath("$.hasNext").value(false));
    }
    @Test void terminalOutcomesRemainVisibleAndUnpublishedProposalsStayHidden() throws Exception {
        application(student,EventStatus.COMPLETED,"ACCEPTED");
        application(student,EventStatus.CANCELLED,"REMOVED");
        application(student,EventStatus.APPROVED,"REJECTED");
        application(student,EventStatus.PENDING,"APPLIED");
        application(student,EventStatus.APPROVED_PENDING_PUBLICATION,"APPLIED");
        application(student,EventStatus.REJECTED,"APPLIED");
        mvc.perform(get(URL).with(user(student))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3));
    }
    @Test void paginatesStablyAndReturnsEmptyOwnPage() throws Exception {
        application(student,EventStatus.APPROVED,"APPLIED");
        var newest=application(student,EventStatus.APPROVED,"ACCEPTED");
        mvc.perform(get(URL).param("size","1").with(user(student))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(newest.getId())).andExpect(jsonPath("$.hasNext").value(true));
        mvc.perform(get(URL).param("size","1").param("page","1").with(user(student))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.hasNext").value(false));
        mvc.perform(get(URL).with(user(other))).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get(URL).param("size","51").with(user(student))).andExpect(status().isBadRequest());
        mvc.perform(get(URL).param("page","-1").with(user(student))).andExpect(status().isBadRequest());
    }
    @Test void rejectsDisabledAccountChangedRoleAndAnonymousRequests() throws Exception {
        var stale=student;
        student.setEnabled(false); student=users.saveAndFlush(student);
        mvc.perform(get(URL).with(user(stale))).andExpect(status().isUnauthorized());
        student.setEnabled(true); student.setRole(Role.ORGANIZER); student=users.saveAndFlush(student);
        mvc.perform(get(URL).with(user(stale))).andExpect(status().isUnauthorized());
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }
    @Test void disabledModuleBlocksOwnHistory() throws Exception {
        application(student,EventStatus.APPROVED,"ACCEPTED");
        var flag=flags.findByFeatureKey("MODULE_EVENTS").orElseThrow(); boolean enabled=flag.isEnabled();
        flag.setEnabled(false); flags.saveAndFlush(flag);
        try { mvc.perform(get(URL).with(user(student))).andExpect(status().isNotFound()); }
        finally { var restored=flags.findByFeatureKey("MODULE_EVENTS").orElseThrow(); restored.setEnabled(enabled); flags.saveAndFlush(restored); }
    }
}

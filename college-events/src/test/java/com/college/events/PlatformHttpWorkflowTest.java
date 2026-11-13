package com.college.events;

import com.college.events.model.Role;
import com.college.events.model.User;
import com.college.events.repository.UserRepository;
import com.college.events.repository.LibraryGateLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real Spring security, controllers, services and repositories; synthetic isolated database only. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:platform-http-workflow;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never",
        "app.default-admin-password=", "app.upload.dir=target/test-uploads/platform-http-workflow",
        "logging.file.name=target/test-logs/platform-http-workflow.log", "app.media.cleanup-cron=-",
        "app.identity.enabled=false", "app.email.backend=console", "spring.cache.type=none"
})
@AutoConfigureMockMvc
@DirtiesContext
class PlatformHttpWorkflowTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired LibraryGateLogRepository libraryLogs;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    @Autowired com.college.events.repository.FeatureFlagRepository flags;
    @Autowired com.college.events.repository.ChatGroupRepository chatGroups;
    @Autowired com.college.events.repository.ChatMembershipRepository chatMembers;

    @Test void chatRequestValidationRetryBindingAndQuotaUseRealHttpBoundary() throws Exception {
        String email = "chat-retry-owner@example.invalid";
        create(email, Role.STUDENT);
        User sender = users.findByEmail(email).orElseThrow();
        var group = chatGroups.saveAndFlush(com.college.events.model.ChatGroup.builder()
                .name("Synthetic retry group").groupType("PERMANENT_DEPARTMENT")
                .relatedDepartment("Synthetic").nightSilenceEnabled(false).build());
        var member = new com.college.events.model.ChatMembership();
        member.setGroupId(group.getId()); member.setUser(sender); member.setActive(true);
        chatMembers.saveAndFlush(member);
        MockHttpSession session = login(email);
        String url = "/api/v1/cujammu/chat/groups/" + group.getId() + "/messages";
        mvc.perform(post(url).session(session).with(csrf()).contentType("application/json")
                .content("{\"content\":\"Missing request key\"}")).andExpect(status().isBadRequest());
        mvc.perform(post(url).session(session).with(csrf()).contentType("application/json")
                .content("{\"content\":\"Malformed request key\",\"requestKey\":\"not-a-uuid\"}"))
                .andExpect(status().isBadRequest());
        String body = "{\"content\":\"Synthetic retry\",\"requestKey\":\"fbf0a3b5-0a7b-4768-bf39-3662b259f755\"}";
        mvc.perform(post(url).session(session).contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        var first = mvc.perform(post(url).session(session).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.requestKey").doesNotExist()).andReturn();
        long id = json.readTree(first.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(post(url).session(session).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        mvc.perform(post(url).session(session).with(csrf()).contentType("application/json")
                .content(body.replace("Synthetic retry", "Different payload"))).andExpect(status().isConflict());
        for (int i = 1; i < 20; i++) {
            String next = json.writeValueAsString(java.util.Map.of("content", "Synthetic message " + i,
                    "requestKey", java.util.UUID.randomUUID().toString()));
            mvc.perform(post(url).session(session).with(csrf()).contentType("application/json").content(next))
                    .andExpect(status().isOk());
        }
        String extra = json.writeValueAsString(java.util.Map.of("content", "Over quota",
                "requestKey", java.util.UUID.randomUUID().toString()));
        mvc.perform(post(url).session(session).with(csrf()).contentType("application/json").content(extra))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "60"));
        mvc.perform(post(url).session(session).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        mvc.perform(get(url).session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(20));
    }

    @Test void explicitDepartmentChatGrantUsesRealCsrfAndRemainsRevocableWhileDisabled() throws Exception {
        String studentEmail = "chat-subject@example.invalid", adminEmail = "chat-provisioner@example.invalid";
        create(studentEmail, Role.STUDENT); create(adminEmail, Role.ADMIN);
        long subjectId = users.findByEmail(studentEmail).orElseThrow().getId();
        var group = chatGroups.saveAndFlush(com.college.events.model.ChatGroup.builder().name("Synthetic private department")
                .groupType("PERMANENT_DEPARTMENT").relatedDepartment("Synthetic registry department")
                .nightSilenceEnabled(false).build());
        String manage = "/api/v1/cujammu/chat/admin/groups/" + group.getId() + "/members/" + subjectId;
        String messages = "/api/v1/cujammu/chat/groups/" + group.getId() + "/messages";
        MockHttpSession student = login(studentEmail), admin = login(adminEmail);
        String grant = """
                {"expectedRevision":null,"active":true,"sourceReference":"REGISTRY-TEST-001","reason":"Synthetic verified source review"}
                """;
        mvc.perform(get("/api/v1/cujammu/chat/admin/department-groups")).andExpect(status().isUnauthorized());
        mvc.perform(put(manage).session(student).with(csrf()).contentType("application/json").content(grant))
                .andExpect(status().isForbidden());
        mvc.perform(put(manage).session(admin).contentType("application/json").content(grant)).andExpect(status().isForbidden());
        mvc.perform(get(messages).session(student)).andExpect(status().isForbidden());
        var response = mvc.perform(put(manage).session(admin).with(csrf()).contentType("application/json").content(grant))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.active").value(true)).andExpect(jsonPath("$.sourceReference").doesNotExist()).andReturn();
        long revision = json.readTree(response.getResponse().getContentAsString()).get("revision").asLong();
        mvc.perform(get(messages).session(admin)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/cujammu/chat/groups/page").session(student)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(group.getId()));
        mvc.perform(post(messages).session(student).with(csrf()).contentType("application/json").content("{\"requestKey\":\"5b923e86-9a89-477b-b26b-88c177878994\",\"content\":\"Synthetic private message\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/cujammu/features/MODULE_COMMUNITY_CHAT").session(admin).with(csrf())
                .contentType("application/json").content(featureChange("MODULE_COMMUNITY_CHAT", false))).andExpect(status().isOk());
        try {
            mvc.perform(get(messages).session(student)).andExpect(status().isNotFound());
            mvc.perform(get("/api/v1/cujammu/chat/admin/groups/"+group.getId()+"/members").session(admin))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].active").value(true));
            mvc.perform(put(manage).session(admin).with(csrf()).contentType("application/json")
                    .content("{\"expectedRevision\":"+revision+",\"active\":false,\"reason\":\"Synthetic revoked access\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        } finally {
            mvc.perform(put("/api/v1/cujammu/features/MODULE_COMMUNITY_CHAT").session(admin).with(csrf())
                    .contentType("application/json").content(featureChange("MODULE_COMMUNITY_CHAT", true))).andExpect(status().isOk());
        }
        mvc.perform(get(messages).session(student)).andExpect(status().isForbidden());
        mvc.perform(post(messages).session(student).with(csrf()).contentType("application/json").content("{\"requestKey\":\"784bf486-fc49-4af9-b18e-026d27f9b204\",\"content\":\"Must not persist\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/cujammu/chat/groups/page").session(student)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test void legacyLibraryRoutesFailClosedWithoutSignedChallengesOrScopedReports() throws Exception {
        String studentEmail = "library-legacy-student@example.invalid";
        String adminEmail = "library-legacy-admin@example.invalid";
        create(studentEmail, Role.STUDENT);
        create(adminEmail, Role.ADMIN);
        for (Role role : Role.values()) {
            if (role != Role.STUDENT && role != Role.ADMIN)
                create("library-legacy-" + role.name().toLowerCase(java.util.Locale.ROOT) + "@example.invalid", role);
        }
        MockHttpSession admin = login(adminEmail);
        mvc.perform(post("/admin/features/toggle").session(admin).with(csrf())
                        .param("key", "MODULE_LIBRARY_TURNSTILE").param("enabled", "true")
                        .param("expectedRevision", String.valueOf(flags.findByFeatureKey("MODULE_LIBRARY_TURNSTILE").orElseThrow().getRevision()))
                        .param("reason", "Synthetic library workflow verification"))
                .andExpect(status().is3xxRedirection());
        MockHttpSession student = login(studentEmail);
        long before = libraryLogs.count();
        try {
            mvc.perform(post("/api/v1/cujammu/library/scan/entry").session(student).with(csrf())
                            .contentType("application/json").content("{\"passType\":\"STUDY\"}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LIBRARY_VERIFICATION_UNAVAILABLE"));
            mvc.perform(post("/api/v1/cujammu/library/scan/exit").session(student).with(csrf())
                            .contentType("application/json").content("{\"passType\":\"STUDY\"}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LIBRARY_VERIFICATION_UNAVAILABLE"));
            mvc.perform(get("/api/v1/cujammu/library/active").session(student))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LIBRARY_VERIFICATION_UNAVAILABLE"));
            mvc.perform(get("/api/v1/cujammu/library/export-csv").session(student))
                    .andExpect(status().isBadRequest());
            mvc.perform(get("/api/v1/cujammu/library/export-csv").session(student).param("date", "2026-10-09"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LIBRARY_VERIFICATION_UNAVAILABLE"))
                    .andExpect(header().string("Cache-Control", "no-store"));
            mvc.perform(get("/api/v1/cujammu/library/export-xlsx").session(student).param("date", "2026-10-09"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LIBRARY_VERIFICATION_UNAVAILABLE"))
                    .andExpect(header().string("Cache-Control", "no-store"));
            for (Role role : Role.values()) {
                String email = role == Role.STUDENT ? studentEmail : role == Role.ADMIN ? adminEmail
                        : "library-legacy-" + role.name().toLowerCase(java.util.Locale.ROOT) + "@example.invalid";
                MockHttpSession privileged = login(email);
                for (String format : List.of("csv", "xlsx")) {
                    mvc.perform(get("/api/v1/cujammu/library/export-" + format).session(privileged).param("date", "2026-10-09"))
                            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LIBRARY_VERIFICATION_UNAVAILABLE"))
                            .andExpect(header().string("Cache-Control", "no-store"));
                }
            }
            mvc.perform(get("/api/v1/cujammu/library/export-csv").param("date", "2026-10-09"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get("/api/v1/cujammu/library/history").session(login(studentEmail)))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
            assertEquals(before, libraryLogs.count(), "legacy passType-only endpoint must not create attendance");
        } finally {
            mvc.perform(post("/admin/features/toggle").session(admin).with(csrf())
                    .param("key", "MODULE_LIBRARY_TURNSTILE").param("enabled", "false")
                    .param("expectedRevision", String.valueOf(flags.findByFeatureKey("MODULE_LIBRARY_TURNSTILE").orElseThrow().getRevision()))
                    .param("reason", "Synthetic library workflow cleanup"));
        }
    }

    @Test void realLoginPrivateProfileIndependentVerificationAndFeatureRevocation() throws Exception {
        String studentEmail = "workflow-student@example.invalid";
        String adminEmail = "workflow-reviewer@example.invalid";
        create(studentEmail, Role.STUDENT);
        create(adminEmail, Role.ADMIN);
        MockHttpSession student = login(studentEmail);
        mvc.perform(get("/api/v1/session").session(student)).andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.email").doesNotExist());
        mvc.perform(get("/api/v1/cujammu/alumni/profile").session(student)).andExpect(status().isNoContent());
        String body = """
                {"expectedVersion":0,"degree":"Synthetic degree","graduationYear":2030,
                 "company":"Synthetic company","mentor":true,"refer":false,
                 "directoryVisible":true,"showCareer":false,"showLocation":false,
                 "showBio":false,"showLinkedIn":false}
                """;
        var created = mvc.perform(post("/api/v1/cujammu/alumni/profile").session(student).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.verifiedAlumni").value(false)).andReturn();
        var profile = json.readTree(created.getResponse().getContentAsString());
        long id = profile.get("id").asLong();
        long version = profile.get("version").asLong();
        mvc.perform(get("/api/v1/cujammu/alumni").session(student)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
        String decision = "{\"expectedVersion\":" + version + ",\"verified\":true}";
        mvc.perform(put("/api/v1/cujammu/alumni/profiles/" + id + "/verification")
                .session(student).with(csrf()).contentType("application/json").content(decision))
                .andExpect(status().isForbidden());
        MockHttpSession admin = login(adminEmail);
        mvc.perform(put("/api/v1/cujammu/alumni/profiles/" + id + "/verification")
                .session(admin).with(csrf()).contentType("application/json").content(decision))
                .andExpect(status().isOk()).andExpect(jsonPath("$.verifiedAlumni").value(true));
        var directory = mvc.perform(get("/api/v1/cujammu/alumni").session(student))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].displayName").value("Synthetic member"))
                .andExpect(jsonPath("$.items[0].currentCompany").isEmpty()).andReturn();
        assertFalse(directory.getResponse().getContentAsString().contains(studentEmail));
        mvc.perform(put("/api/v1/cujammu/features/MODULE_ALUMNI").session(admin).with(csrf())
                .contentType("application/json").content(featureChange("MODULE_ALUMNI", false)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/cujammu/alumni/profile").session(student)).andExpect(status().isNotFound());
    }

    private void create(String email, Role role) {
        users.saveAndFlush(User.builder().name("Synthetic member").email(email)
                .password(passwords.encode("Synthetic123!")).role(role).department("CSIT").enabled(true).build());
    }
    private String featureChange(String key, boolean enabled) throws Exception {
        return json.writeValueAsString(java.util.Map.of("enabled", enabled, "expectedRevision", flags.findByFeatureKey(key).orElseThrow().getRevision(),
                "reason", "Synthetic authorized revocation test"));
    }

    @Test void helpdeskCreateIsPrivateAndRetryReturnsOriginalTicket() throws Exception {
        create("ticket-owner@example.invalid", Role.STUDENT);
        create("ticket-outsider@example.invalid", Role.STUDENT);
        MockHttpSession owner = login("ticket-owner@example.invalid");
        MockHttpSession outsider = login("ticket-outsider@example.invalid");
        String body = """
                {"requestKey":"a73bab60-9c83-4c7a-923c-40b9da6b9081","subject":"Synthetic request",
                "description":"Synthetic private support details","category":"GENERAL","requestedPriority":"URGENT"}
                """;
        var created = mvc.perform(post("/api/v1/cujammu/helpdesk/tickets").session(owner).with(csrf())
                .contentType("application/json").content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.requestedPriority").value("URGENT")).andReturn();
        var ticket = json.readTree(created.getResponse().getContentAsString());
        String id = ticket.get("id").asText();
        assertTrue(ticket.get("ticketNumber").asText().matches("CUJ-TKT-[0-9]{6}-[0-9]{4,}"));
        mvc.perform(get("/api/v1/cujammu/helpdesk/my-tickets").session(owner).param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.items[0].id").value(id))
                .andExpect(jsonPath("$.items[0].version").isNumber())
                .andExpect(jsonPath("$.items[0].canManage").value(false));
        mvc.perform(post("/api/v1/cujammu/helpdesk/tickets").session(owner).with(csrf())
                .contentType("application/json").content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
        mvc.perform(get("/api/v1/cujammu/helpdesk/tickets/" + id).session(outsider)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/cujammu/helpdesk/tickets/" + id + "/thread").session(outsider))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/cujammu/helpdesk/tickets/" + id).session(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.createdBy").doesNotExist());
    }
    private MockHttpSession login(String email) throws Exception {
        var result = mvc.perform(formLogin("/login").user(email).password("Synthetic123!"))
                .andExpect(status().is3xxRedirection()).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session); return session;
    }
}

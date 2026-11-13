package com.college.events;

import com.college.events.model.*;
import com.college.events.repository.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Full Spring HTTP/session/CSRF boundary against isolated synthetic H2, never the workspace database. */
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:audience-poll-http;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop","spring.sql.init.mode=never","app.default-admin-password=",
        "app.upload.dir=target/test-uploads/audience-poll-http","logging.file.name=target/test-logs/audience-poll-http.log",
        "app.media.cleanup-cron=-","app.identity.enabled=false","app.email.backend=console","spring.cache.type=none"})
@AutoConfigureMockMvc @DirtiesContext
class AudiencePollHttpTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired EventRepository events;
    @Autowired EventOperationsRepository operations;
    @Autowired RegistrationRepository registrations;
    @Autowired PollVoteRepository votes;
    @Autowired FeatureFlagRepository flags;
    @Autowired PasswordEncoder passwords;
    private Event event;
    private User student;
    private MockHttpSession ownerSession, studentSession, outsiderSession, adminSession;
    private final String base="/api/v1/cujammu";

    @BeforeEach void setup() throws Exception {
        var owner=user(Role.FACULTY);student=user(Role.STUDENT);var outsider=user(Role.STUDENT);var admin=user(Role.ADMIN);
        event=events.saveAndFlush(Event.builder().title("Synthetic poll event").description("Synthetic")
                .venue("Synthetic venue").eventDate(LocalDateTime.of(2030,1,2,12,0)).maxSeats(10)
                .organizer(owner).status(EventStatus.APPROVED).build());
        operations.saveAndFlush(EventOperations.builder().event(event).build());
        registrations.saveAndFlush(Registration.builder().event(event).user(student).status(RegistrationStatus.REGISTERED).build());
        ownerSession=login(owner);studentSession=login(student);outsiderSession=login(outsider);adminSession=login(admin);
    }
    private User user(Role role){return users.saveAndFlush(User.builder().name("Synthetic private subject")
            .email(UUID.randomUUID()+"@example.invalid").password(passwords.encode("Synthetic123!"))
            .enabled(true).role(role).build());}
    private MockHttpSession login(User user) throws Exception {
        var result=mvc.perform(formLogin("/login").user(user.getEmail()).password("Synthetic123!"))
                .andExpect(status().is3xxRedirection()).andReturn();
        return (MockHttpSession)result.getRequest().getSession(false);
    }
    private String createUrl(){return base+"/events/"+event.getId()+"/polls";}
    private String body() throws Exception{return json.writeValueAsString(Map.of("question","Synthetic audience question",
            "options",List.of("Team Alpha","Team Beta"),"durationMinutes",10,"requestKey",UUID.randomUUID().toString()));}
    private JsonNode create() throws Exception {
        var result=mvc.perform(post(createUrl()).session(ownerSession).with(csrf()).contentType("application/json").content(body()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.state").value("DRAFT")).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }
    private JsonNode publish(JsonNode draft) throws Exception {
        var result=mvc.perform(post(base+"/polls/"+draft.get("id").asLong()+"/publish").session(ownerSession).with(csrf())
                .contentType("application/json").content("{\"expectedRevision\":"+draft.get("revision").asLong()+"}"))
                .andExpect(status().isOk()).andReturn();return json.readTree(result.getResponse().getContentAsString());
    }

    @Test void realHttpEnforcesAuthenticationCsrfScopeAndTypedCommandValidation() throws Exception {
        mvc.perform(get(base+"/polls/events")).andExpect(status().isUnauthorized());
        String body=body();
        mvc.perform(post(createUrl()).session(ownerSession).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post(createUrl()).session(studentSession).with(csrf()).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post(createUrl()).session(ownerSession).with(csrf()).contentType("application/json")
                .content("{\"question\":\"Synthetic\",\"options\":[\"A\"],\"durationMinutes\":10,\"requestKey\":\"bad\"}"))
                .andExpect(status().isBadRequest());
        var draft=create();long id=draft.get("id").asLong();
        for(String invalid:List.of("{}","{\"expectedRevision\":null}","{\"expectedRevision\":-1}"))
            mvc.perform(post(base+"/polls/"+id+"/publish").session(ownerSession).with(csrf()).contentType("application/json")
                    .content(invalid)).andExpect(status().isBadRequest());
        mvc.perform(get(base+"/polls/"+id+"/results").session(ownerSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("DRAFT"));
        mvc.perform(get(base+"/polls/"+id+"/results").session(studentSession)).andExpect(status().isForbidden());
        mvc.perform(post(base+"/polls/"+id+"/publish").session(outsiderSession).with(csrf()).contentType("application/json")
                .content("{\"expectedRevision\":0}")).andExpect(status().isForbidden());
        mvc.perform(post(base+"/polls/"+id+"/publish").session(ownerSession).with(csrf()).contentType("application/json")
                .content("{\"expectedRevision\":999}")).andExpect(status().isConflict());
    }

    @Test void privateOwnReceiptImmutableChoiceAndAggregatesSurviveRetryWithoutIdentityLeak() throws Exception {
        var poll=publish(create());long id=poll.get("id").asLong();String url=base+"/polls/"+id+"/vote";
        mvc.perform(post(url).session(studentSession).with(csrf()).contentType("application/json").content("{\"option\":\"unknown\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(url).session(outsiderSession).with(csrf()).contentType("application/json").content("{\"option\":\"Team Alpha\"}"))
                .andExpect(status().isForbidden());
        for(int attempt=0;attempt<2;attempt++) mvc.perform(post(url).session(studentSession).with(csrf())
                .contentType("application/json").content("{\"option\":\"Team Alpha\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.myChoice").value("Team Alpha")).andExpect(jsonPath("$.totalVotes").value(1))
                .andExpect(jsonPath("$.voterId").doesNotExist()).andExpect(jsonPath("$.voterIp").doesNotExist());
        mvc.perform(post(url).session(studentSession).with(csrf()).contentType("application/json").content("{\"option\":\"Team Beta\"}"))
                .andExpect(status().isConflict());
        var response=mvc.perform(get(base+"/polls/"+id+"/results").session(ownerSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$.myChoice").isEmpty()).andExpect(jsonPath("$.totalVotes").value(1)).andReturn();
        String payload=response.getResponse().getContentAsString();
        for(String forbidden:List.of("voterId","voterName","voterIp","requestKey","requestHash",student.getEmail()))assertFalse(payload.contains(forbidden));
        assertEquals(1,votes.countByPollId(id));
    }

    @Test void featureRevocationHidesReadAndVoteEndpointsIncludingPreviouslyAcceptedReplay() throws Exception {
        var poll=publish(create());long id=poll.get("id").asLong();String vote=base+"/polls/"+id+"/vote";
        mvc.perform(post(vote).session(studentSession).with(csrf()).contentType("application/json").content("{\"option\":\"Team Alpha\"}"))
                .andExpect(status().isOk());
        mvc.perform(put(base+"/features/MODULE_POLLS").session(adminSession).with(csrf())
                .contentType("application/json").content(featureChange("MODULE_POLLS", false))).andExpect(status().isOk());
        try {
            mvc.perform(get(base+"/polls/"+id+"/results").session(studentSession)).andExpect(status().isNotFound());
            mvc.perform(post(vote).session(studentSession).with(csrf()).contentType("application/json").content("{\"option\":\"Team Alpha\"}"))
                    .andExpect(status().isNotFound());
        }finally{mvc.perform(put(base+"/features/MODULE_POLLS").session(adminSession).with(csrf())
                .contentType("application/json").content(featureChange("MODULE_POLLS", true))).andExpect(status().isOk());}
        assertEquals(1,votes.countByPollId(id));
    }
    private String featureChange(String key, boolean enabled) throws Exception {
        return json.writeValueAsString(Map.of("enabled", enabled, "expectedRevision", flags.findByFeatureKey(key).orElseThrow().getRevision(),
                "reason", "Synthetic authorized revocation test"));
    }
}

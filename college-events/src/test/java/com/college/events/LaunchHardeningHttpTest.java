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
import java.time.Instant;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Actual security/session/CSRF chain and synthetic isolated H2; no campus records or workspace uploads. */
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:launch-hardening-http;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop","spring.sql.init.mode=never","app.default-admin-password=",
        "app.upload.dir=target/test-uploads/launch-hardening-http","logging.file.name=target/test-logs/launch-hardening-http.log",
        "app.media.cleanup-cron=-","app.identity.enabled=false","app.email.backend=console","spring.cache.type=none"})
@AutoConfigureMockMvc @DirtiesContext
class LaunchHardeningHttpTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords; @Autowired MediaAssetRepository assets;
    @Autowired LostAndFoundRepository items; @Autowired LostFoundClaimRepository claims;
    @Autowired LibraryGateLogRepository libraryVisits; @Autowired FeatureFlagRepository flags;
    User finder,claimant,stranger; MockHttpSession finderSession,claimantSession,strangerSession;
    final String base="/api/v1/cujammu/lost-and-found";
    @BeforeEach void setup() throws Exception {
        finder=user("finder");claimant=user("claimant");stranger=user("stranger");
        finderSession=login(finder);claimantSession=login(claimant);strangerSession=login(stranger);
    }
    User user(String label) {return users.saveAndFlush(User.builder().name("Synthetic "+label).email(UUID.randomUUID()+"@example.invalid")
            .password(passwords.encode("Synthetic123!")).phone("SYNTHETIC_PRIVATE_PHONE").enabled(true).role(Role.STUDENT).build());}
    MockHttpSession login(User user) throws Exception {
        var result=mvc.perform(formLogin("/login").user(user.getEmail()).password("Synthetic123!")).andExpect(status().is3xxRedirection()).andReturn();
        return (MockHttpSession)result.getRequest().getSession(false);
    }
    String body(Map<String,Object> value) throws Exception {return json.writeValueAsString(value);}
    JsonNode report() throws Exception {
        var result=mvc.perform(post(base).session(finderSession).with(csrf()).contentType("application/json")
                .content(body(Map.of("requestKey",UUID.randomUUID().toString(),"title","Synthetic keys","category","KEYS",
                        "description","Keys at synthetic desk","locationFound","Synthetic office"))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }
    @Test void csrfAndValidationPreventUnacceptedWrites() throws Exception {
        long before=items.count();String command=body(Map.of("requestKey",UUID.randomUUID().toString(),"title","Keys","category","KEYS","description","Synthetic","locationFound","Desk"));
        mvc.perform(post(base).session(finderSession).contentType("application/json").content(command)).andExpect(status().isForbidden());
        mvc.perform(post(base).session(finderSession).with(csrf()).contentType("application/json").content("{}" )).andExpect(status().isBadRequest());
        assertEquals(before,items.count());
    }
    @Test void claimsStayPrivateAndStrangerCannotHandover() throws Exception {
        var item=report();long id=item.get("id").asLong();
        String command=body(Map.of("requestKey",UUID.randomUUID().toString(),"expectedVersion",item.get("version").asLong(),"evidence","SYNTHETIC_SECRET_PROOF"));
        var submitted=mvc.perform(post(base+"/"+id+"/claim").session(claimantSession).with(csrf()).contentType("application/json").content(command))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn();
        long claimId=json.readTree(submitted.getResponse().getContentAsString()).get("id").asLong();
        assertEquals(claimant.getId(),claims.findById(claimId).orElseThrow().getClaimant().getId());
        mvc.perform(get(base+"/"+id+"/claims").session(strangerSession)).andExpect(status().isOk()).andExpect(content().json("[]"));
        var list=mvc.perform(get(base).session(strangerSession)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(list.contains("SYNTHETIC_SECRET_PROOF"));assertFalse(list.contains("SYNTHETIC_PRIVATE_PHONE"));assertFalse(list.contains(finder.getEmail()));
        String handover=body(Map.of("requestKey",UUID.randomUUID().toString(),"claimId",claimId,"expectedVersion",item.get("version").asLong(),"verificationReason","Synthetic verified detail"));
        mvc.perform(post(base+"/"+id+"/hand-over").session(strangerSession).with(csrf()).contentType("application/json").content(handover)).andExpect(status().isForbidden());
        assertEquals("REPORTED",items.findById(id).orElseThrow().getStatus());
        mvc.perform(post(base+"/"+id+"/hand-over").session(finderSession).with(csrf()).contentType("application/json").content(handover)).andExpect(status().isOk());
        assertEquals("HANDED_OVER",items.findById(id).orElseThrow().getStatus());
    }
    @Test void staticMediaEnforcesOwnerExpiryHoldAndUnknownMetadata() throws Exception {
        String key="profiles/"+UUID.randomUUID()+".png";Path file=Paths.get("target/test-uploads/launch-hardening-http").resolve(key);
        Files.createDirectories(file.getParent());Files.write(file,new byte[]{1,2,3});
        var asset=assets.saveAndFlush(MediaAsset.builder().objectKey(key).ownerUserId(finder.getId()).mediaType("image/png").sizeBytes(3)
                .uploadAcceptedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).status("ACTIVE").build());
        try {
            mvc.perform(get("/uploads/"+key).session(strangerSession)).andExpect(status().isNotFound());
            mvc.perform(get("/uploads/"+key).session(finderSession)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store, private"));
            asset.setLegalHold(true);asset.setExpiresAt(Instant.EPOCH);assets.saveAndFlush(asset);
            mvc.perform(get("/uploads/"+key).session(finderSession)).andExpect(status().isGone());
            assertTrue(Files.exists(file));
            mvc.perform(get("/uploads/profiles/untracked.png").session(finderSession)).andExpect(status().isNotFound());
        } finally {Files.deleteIfExists(file);}
    }
    @Test void libraryOffRetainsOwnerRecoveryWithoutCreditedAttendance() throws Exception {
        var flag=flags.findByFeatureKey("MODULE_LIBRARY_TURNSTILE").orElseThrow();flag.setEnabled(false);flags.saveAndFlush(flag);
        var visit=libraryVisits.saveAndFlush(LibraryGateLog.builder().user(finder).sessionType("STUDY")
                .entryTime(java.time.LocalDateTime.now().minusMinutes(20)).logDate(java.time.LocalDate.now()).status("ACTIVE").build());
        String url="/api/v1/cujammu/recovery/library/"+visit.getId()+"/safe-exit";
        String command=body(Map.of("requestKey",UUID.randomUUID().toString(),"expectedRevision",visit.getRevision(),"reasonCode","SERVICE_DISABLED"));
        mvc.perform(post(url).session(finderSession).contentType("application/json").content(command)).andExpect(status().isForbidden());
        mvc.perform(post(url).session(strangerSession).with(csrf()).contentType("application/json").content(command)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/cujammu/recovery/library/active").session(strangerSession)).andExpect(status().isOk()).andExpect(content().json("[]"));
        var first=mvc.perform(post(url).session(finderSession).with(csrf()).contentType("application/json").content(command))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.status").value("SAFE_EXIT_UNVERIFIED")).andExpect(jsonPath("$.durationQuality").value("UNVERIFIED")).andReturn();
        mvc.perform(post(url).session(finderSession).with(csrf()).contentType("application/json").content(command))
                .andExpect(status().isOk()).andExpect(content().json(first.getResponse().getContentAsString()));
        var saved=libraryVisits.findById(visit.getId()).orElseThrow();assertNull(saved.getDurationSeconds());assertNotNull(saved.getExitTime());
    }
    @Test void legacyFeatureEndpointCannotBypassRevisionAuditReasonOrFoundationLock() throws Exception {
        var admin=user("admin");admin.setRole(Role.ADMIN);admin=users.saveAndFlush(admin);var adminSession=login(admin);
        var flag=flags.findByFeatureKey("MODULE_AI_CHATBOT").orElseThrow();long revision=flag.getRevision();
        String url="/api/v1/cujammu/features/MODULE_AI_CHATBOT";
        String valid=body(Map.of("enabled",!flag.isEnabled(),"expectedRevision",revision,"reason","Synthetic change review"));
        mvc.perform(put(url).session(strangerSession).with(csrf()).contentType("application/json").content(valid)).andExpect(status().isForbidden());
        mvc.perform(put(url).session(adminSession).with(csrf()).contentType("application/json").content("{\"enabled\":false,\"reason\":\"Synthetic\"}")).andExpect(status().isBadRequest());
        mvc.perform(put(url).session(adminSession).with(csrf()).contentType("application/json").content(body(Map.of("enabled",false,"expectedRevision",revision,"reason","  ")))).andExpect(status().isBadRequest());
        mvc.perform(put(url).session(adminSession).with(csrf()).contentType("application/json").content(valid)).andExpect(status().isOk());
        mvc.perform(put(url).session(adminSession).with(csrf()).contentType("application/json").content(valid)).andExpect(status().isConflict());
        var foundation=flags.findByFeatureKey("FOUNDATION_AUTH").orElseThrow();
        mvc.perform(put("/api/v1/cujammu/features/FOUNDATION_AUTH").session(adminSession).with(csrf()).contentType("application/json")
                .content(body(Map.of("enabled",false,"expectedRevision",foundation.getRevision(),"reason","Synthetic lock test")))).andExpect(status().isBadRequest());
        assertTrue(flags.findByFeatureKey("FOUNDATION_AUTH").orElseThrow().isEnabled());
    }
}

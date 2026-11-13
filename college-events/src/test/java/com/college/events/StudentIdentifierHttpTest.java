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
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:student-identity-http;DB_CLOSE_DELAY=-1", "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.sql.init.mode=never", "app.default-admin-password=", "app.upload.dir=target/test-uploads/student-identity-http",
    "logging.file.name=target/test-logs/student-identity-http.log", "app.media.cleanup-cron=-", "app.identity.enabled=false", "app.email.backend=console", "spring.cache.type=none"})
@AutoConfigureMockMvc @DirtiesContext
class StudentIdentifierHttpTest {
    @Autowired MockMvc mvc; @Autowired UserRepository users; @Autowired DepartmentRepository departments;
    @Autowired AuditLogRepository audits; @Autowired NotificationRepository notifications; @Autowired ObjectMapper json;
    User admin, student; Department department;
    @BeforeEach void setup() {
        admin=account(Role.ADMIN); student=account(Role.STUDENT);
        department=departments.saveAndFlush(Department.builder().code(UUID.randomUUID().toString().substring(0,12)).name("Synthetic "+UUID.randomUUID()).active(true).build());
    }
    @Test void adminLookupReturnsOnlyBoundedOfficeProjectionAndCanonicalEmail() throws Exception {
        var response=mvc.perform(post("/api/v1/admin/students/lookup").with(user(admin)).with(csrf()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("email",student.getEmail().toUpperCase(Locale.ROOT)))))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.bindingState").value("UNVERIFIED")).andReturn();
        var body=json.readTree(response.getResponse().getContentAsString());
        Set<String> keys=new HashSet<>();body.fieldNames().forEachRemaining(keys::add);
        assertEquals(Set.of("studentId","name","email","verifiedIdentifier","verifiedDepartmentId","bindingState","credentialVersion"),keys);
    }
    @Test void firstBindingPersistsLeadingZerosAuditsAndNotifiesWithoutChangingProfile() throws Exception {
        var beforeAudit=audits.count();var beforeNotifications=notifications.count();
        mvc.perform(put(url()).with(user(admin)).with(csrf()).contentType("application/json").content(command("000123-"+student.getId(),department.getId(),0)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.credentialVersion").value(1));
        var saved=users.findById(student.getId()).orElseThrow();
        assertEquals("000123-"+student.getId(),saved.getVerifiedEnrollmentNo());assertEquals(department.getId(),saved.getVerifiedDepartmentId());
        assertEquals("Untrusted profile department",saved.getDepartment());assertNotNull(saved.getEnrollmentIdentityVerifiedAt());
        assertEquals(beforeAudit+1,audits.count());assertEquals(beforeNotifications+1,notifications.count());
        // Lost-response replay is no-write and does not refresh verification evidence or send again.
        mvc.perform(put(url()).with(user(admin)).with(csrf()).contentType("application/json").content(command(saved.getVerifiedEnrollmentNo(),department.getId(),0))).andExpect(status().isOk());
        assertEquals(saved.getEnrollmentIdentityVerifiedAt(),users.findById(student.getId()).orElseThrow().getEnrollmentIdentityVerifiedAt());
        assertEquals(beforeAudit+1,audits.count());assertEquals(beforeNotifications+1,notifications.count());
        mvc.perform(put(url()).with(user(admin)).with(csrf()).contentType("application/json").content(command("DIFFERENT",department.getId(),1))).andExpect(status().isConflict());
        assertEquals(saved.getVerifiedEnrollmentNo(),users.findById(student.getId()).orElseThrow().getVerifiedEnrollmentNo());
    }
    @Test void partialLegacyEvidenceCannotBeOverwritten() throws Exception {
        student.setEnrollmentIdentitySourceReference("Legacy evidence");users.saveAndFlush(student);
        mvc.perform(put(url()).with(user(admin)).with(csrf()).contentType("application/json").content(command("NEW",department.getId(),student.getCredentialVersion()))).andExpect(status().isConflict());
        assertNull(users.findById(student.getId()).orElseThrow().getVerifiedEnrollmentNo());
    }
    @Test void anonymousNonstudentDisabledAndRevokedAdministratorCannotDiscoverStudent() throws Exception {
        var payload=json.writeValueAsString(Map.of("email",student.getEmail()));
        mvc.perform(post("/api/v1/admin/students/lookup").with(csrf()).contentType("application/json").content(payload)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin/students/lookup").with(user(admin)).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("email",admin.getEmail())))).andExpect(status().isNotFound());
        student.setEnabled(false);users.saveAndFlush(student);
        mvc.perform(post("/api/v1/admin/students/lookup").with(user(admin)).with(csrf()).contentType("application/json").content(payload)).andExpect(status().isNotFound());
        User staleAdmin=admin;var revoked=users.findById(admin.getId()).orElseThrow();revoked.setRole(Role.FACULTY);users.saveAndFlush(revoked);
        // A changed role invalidates the old session before domain authorization.
        mvc.perform(post("/api/v1/admin/students/lookup").with(user(staleAdmin)).with(csrf()).contentType("application/json").content(payload)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin/students/lookup").with(user(revoked)).with(csrf()).contentType("application/json").content(payload)).andExpect(status().isForbidden());
    }
    @Test void roleCsrfDisabledAccountAndStaleVersionDenyWithoutMutation() throws Exception {
        var request=command("VALID",department.getId(),0);
        mvc.perform(put(url()).with(user(student)).with(csrf()).contentType("application/json").content(request)).andExpect(status().isForbidden());
        mvc.perform(put(url()).with(user(admin)).contentType("application/json").content(request)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/students/lookup").with(user(student)).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("email",student.getEmail())))).andExpect(status().isForbidden());
        student.setName("Changed");student=users.saveAndFlush(student);
        mvc.perform(put(url()).with(user(admin)).with(csrf()).contentType("application/json").content(request)).andExpect(status().isConflict());
        student.setEnabled(false);users.saveAndFlush(student);
        mvc.perform(put(url()).with(user(admin)).with(csrf()).contentType("application/json").content(command("VALID",department.getId(),student.getCredentialVersion()))).andExpect(status().isForbidden());
        assertNull(users.findById(student.getId()).orElseThrow().getVerifiedEnrollmentNo());
    }
    @Test void inactiveDepartmentAndDuplicateIdentifierCannotBind() throws Exception {
        department.setActive(false);departments.saveAndFlush(department);
        mvc.perform(put(url()).with(user(admin)).with(csrf()).contentType("application/json").content(command("VALID",department.getId(),0))).andExpect(status().isForbidden());
        department.setActive(true);departments.saveAndFlush(department);
        var other=account(Role.STUDENT);other.setVerifiedEnrollmentNo("TAKEN-"+student.getId());users.saveAndFlush(other);
        var before=audits.count();
        mvc.perform(put(url()).with(user(admin)).with(csrf()).contentType("application/json").content(command(other.getVerifiedEnrollmentNo(),department.getId(),0))).andExpect(status().isBadRequest());
        assertNull(users.findById(student.getId()).orElseThrow().getVerifiedEnrollmentNo());assertEquals(before,audits.count());
    }
    String url(){return "/api/v1/admin/students/"+student.getId()+"/verified-identifier";}
    String command(String identifier,long departmentId,long version)throws Exception{return json.writeValueAsString(Map.of("expectedCredentialVersion",version,"identifier",identifier,"departmentId",departmentId,"officialSourceReference","Synthetic reviewed source","reviewReason","Synthetic office verification"));}
    User account(Role role){return users.saveAndFlush(User.builder().name("Synthetic").email(UUID.randomUUID()+"@example.invalid").password("synthetic-only").department("Untrusted profile department").enabled(true).role(role).build());}
}

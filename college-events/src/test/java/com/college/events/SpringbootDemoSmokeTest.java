package com.college.events;

import com.college.events.model.*;
import com.college.events.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import javax.sql.DataSource;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"app.professor-demo.enabled=true","app.professor-demo.password=SyntheticDemo123!"})
@ActiveProfiles("professor-demo") @DirtiesContext
class SpringbootDemoSmokeTest {
    @Autowired UserRepository users;@Autowired DepartmentRepository departments;@Autowired EventRepository events;
    @Autowired EventOperationsRepository operations;@Autowired CertificateRepository certificates;
    @Autowired EventQrAttendanceEvidenceRepository evidence;@Autowired StudentScoreCardRepository scores;
    @Autowired MediaAssetRepository media;@Autowired FeatureFlagRepository flags;@Autowired DataSource datasource;
    @Test void isolatedFixturesStaySyntheticAndDoNotInventAcceptedOutcomes() throws Exception {
        try(var connection=datasource.getConnection()) {assertEquals("jdbc:h2:mem:pulse-professor-demo",connection.getMetaData().getURL());}
        assertEquals(7,users.count());assertEquals(22,departments.count());assertEquals(1,events.count());
        var applicant=users.findByEmail("applicant@example.invalid").orElseThrow();
        assertEquals(Role.STUDENT,applicant.getRole());assertNull(applicant.getVerifiedEnrollmentNo());
        assertNull(applicant.getVerifiedDepartmentId());assertNull(applicant.getEnrollmentIdentityVerifiedAt());
        assertNull(applicant.getEnrollmentIdentitySourceReference());assertNull(applicant.getEnrollmentIdentityReviewReason());
        assertTrue(users.findAll().stream().allMatch(u->u.getEmail().endsWith("@example.invalid")&&u.getName().startsWith("Synthetic ")));
        assertEquals(1,users.findAll().stream().filter(u->u.getRole()==Role.ADMIN).count());
        assertEquals(0,certificates.count());assertEquals(0,evidence.count());assertEquals(0,scores.count());assertEquals(0,media.count());
        assertTrue(operations.findAll().stream().allMatch(o->!o.isScannerMasterEnabled()&&!o.isManualWindowOpen()));
        assertFalse(flags.findByFeatureKey("MODULE_LIBRARY_TURNSTILE").orElseThrow().isEnabled());
        assertTrue(events.findAll().stream().allMatch(e->e.getStatus()==EventStatus.APPROVED&&e.getCompletedAt()==null));
    }
}

package com.college.events.service;

import com.college.events.model.*;
import com.college.events.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false",
        "spring.sql.init.mode=never", "logging.file.name=target/test-logs/alumni-privacy.log"}, showSql = false)
class AlumniRepositoryPrivacyTest {
    @Autowired UserRepository users;
    @Autowired AlumniProfileRepository profiles;
    @Autowired AlumniJobPostingRepository jobs;

    @Test void databaseQueryExcludesPrivateLegacyUnverifiedDisabledAndSelfVerifiedRows() {
        var visible = profile("visible");
        var privateProfile = profile("private"); privateProfile.setDirectoryVisible(false);
        var legacy = profile("legacy"); legacy.setDirectoryVisible(null); legacy.setVerifiedById(null); legacy.setVerifiedAt(null);
        var noProvenance = profile("no-provenance"); noProvenance.setVerifiedById(null);
        var unverified = profile("unverified"); unverified.setVerifiedAlumni(false);
        var disabled = profile("disabled"); disabled.getUser().setEnabled(false); users.saveAndFlush(disabled.getUser());
        var selfVerified = profile("self-verified"); selfVerified.setVerifiedById(selfVerified.getUser().getId());
        profiles.saveAllAndFlush(List.of(visible, privateProfile, legacy, noProvenance, unverified, disabled, selfVerified));
        var page = profiles.findVisible(null,false,PageRequest.of(0,24));
        assertEquals(List.of(visible.getId()),page.map(AlumniProfile::getId).getContent());
        assertTrue(profiles.findVisible("OTHER",false,PageRequest.of(0,24)).isEmpty());
        assertTrue(profiles.findVisible(null,true,PageRequest.of(0,24)).isEmpty());
        visible.setWillingToMentor(true);profiles.saveAndFlush(visible);
        assertEquals(1,profiles.findVisible("CSIT",true,PageRequest.of(0,24)).getTotalElements());
    }

    @Test void jobsRequireCurrentVerificationAndIncludeAllDepartmentListings() {
        var owner = profile("jobs"); profiles.saveAndFlush(owner);
        var all = job(owner.getUser(),"ALL"); var specific = job(owner.getUser(),"CSIT");
        var other = job(owner.getUser(),"OTHER"); var closed = job(owner.getUser(),"ALL"); closed.setActive(false); jobs.saveAndFlush(closed);
        assertEquals(2,jobs.findVisible("CSIT",PageRequest.of(0,24)).getTotalElements());
        assertEquals(3,jobs.findVisible(null,PageRequest.of(0,24)).getTotalElements());
        owner.setVerifiedAlumni(false);profiles.saveAndFlush(owner);
        assertTrue(jobs.findVisible(null,PageRequest.of(0,24)).isEmpty());
        assertEquals(4,jobs.findByPostedByIdOrderByIdDesc(owner.getUser().getId(),PageRequest.of(0,24)).getTotalElements());
    }

    @Test void legacyRowsRemainIntactButRequireReviewAndOptIn() {
        var legacy=profile("preserved");legacy.setVerifiedById(null);legacy.setVerifiedAt(null);legacy.setDirectoryVisible(null);
        profiles.saveAndFlush(legacy);
        assertTrue(profiles.findById(legacy.getId()).isPresent());
        assertTrue(profiles.findById(legacy.getId()).orElseThrow().isVerifiedAlumni());
        assertEquals(1,profiles.findPendingReview(PageRequest.of(0,24)).getTotalElements());
        assertTrue(profiles.findVisible(null,false,PageRequest.of(0,24)).isEmpty());
    }

    private AlumniProfile profile(String suffix) {
        User user=users.saveAndFlush(User.builder().name("Synthetic "+suffix).email(suffix+"@example.invalid")
                .password("synthetic-hash").role(Role.ALUMNI).department("CSIT").enabled(true).build());
        return AlumniProfile.builder().user(user).department("CSIT").degree("MSc").graduationYear(2025)
                .verifiedAlumni(true).verifiedById(99999L).verifiedAt(LocalDateTime.of(2026,1,1,0,0))
                .directoryVisible(true).build();
    }
    private AlumniJobPosting job(User owner,String department) {
        return jobs.saveAndFlush(AlumniJobPosting.builder().postedBy(owner).companyName("Synthetic employer")
                .jobTitle("Synthetic role").description("Synthetic description").targetDepartment(department).active(true).build());
    }
}

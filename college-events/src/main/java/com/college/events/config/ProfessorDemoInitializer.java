package com.college.events.config;

import com.college.events.model.*;
import com.college.events.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.*;

/** Small synthetic fixtures for office review, never records imported from the university. */
@Component @Profile("professor-demo") @RequiredArgsConstructor
public class ProfessorDemoInitializer implements CommandLineRunner {
    private final UserRepository users;
    private final DepartmentRepository departments;
    private final CategoryRepository categories;
    private final FacilityRepository facilities;
    private final HostelRepository hostels;
    private final EventRepository events;
    private final EventOperationsRepository operations;
    private final AcademicAuthorityGrantRepository academicGrants;
    private final ChatGroupRepository groups;
    private final ChatMembershipRepository memberships;
    private final FeatureFlagRepository flags;
    private final PasswordEncoder passwords;
    private final javax.sql.DataSource datasource;
    @Value("${app.professor-demo.password}") private String password;
    @Override @Transactional public void run(String... arguments) throws Exception {
        try(var connection=datasource.getConnection()) {
            if(!"jdbc:h2:mem:pulse-professor-demo".equals(connection.getMetaData().getURL()))
                throw new IllegalStateException("Synthetic fixtures require the isolated memory database");
        }
        if(users.count()!=0||events.count()!=0) throw new IllegalStateException("Synthetic startup requires an empty isolated database");
        Department department=null;
        for(int i=1;i<=22;i++) {
            var entry=departments.saveAndFlush(Department.builder().code("DEMO"+i)
                    .name(i==1?"Synthetic Engineering":"Synthetic Department "+i).active(true).build());
            if(i==1) department=entry;
        }
        var student=user("student",Role.STUDENT,department,"000001");
        var finder=user("finder",Role.STUDENT,department,"000002");
        // Unbound synthetic account for the real office-verification journey.
        users.saveAndFlush(User.builder().name("Synthetic applicant").email("applicant@example.invalid")
                .password(passwords.encode(password)).role(Role.STUDENT).enabled(true)
                .department("Self-entered synthetic department").academicLevel("UG").build());
        var faculty=user("faculty",Role.FACULTY,department,null);
        var reviewer=user("reviewer",Role.STAFF,department,null);
        var admin=user("admin",Role.ADMIN,department,null);
        var warden=user("warden",Role.WARDEN,department,null);
        for(var grant:java.util.List.of(new Object[]{faculty,"PUBLISH"},new Object[]{reviewer,"REVIEW"}))
            academicGrants.save(AcademicAuthorityGrant.builder().user((User)grant[0]).departmentName(department.getName())
                    .capability((String)grant[1]).enabled(true).grantedBy("SYNTHETIC_DEMO_FIXTURE").build());
        var category=categories.save(Category.builder().name("Synthetic workshop").icon("bi-book").colorClass("primary").build());
        facilities.save(Facility.builder().name("Synthetic Demo Hall").facilityType("SEMINAR_HALL").location("Synthetic campus")
                .capacity(30).hasProjector(true).active(true).build());
        hostels.save(Hostel.builder().name("Synthetic Demo Hostel").genderType("CO_ED").wardenName(warden.getName())
                .wardenEmail(warden.getEmail()).curfewTime("21:00").capacity(30).active(true).build());
        var event=events.saveAndFlush(Event.builder().title("Synthetic professor review workshop").description("Synthetic local data: register, cancel, review tasks and permissions.")
                .organizer(faculty).category(category).venue("Synthetic Demo Hall").eventDate(LocalDateTime.now().plusDays(1))
                .maxSeats(2).fees(0.0).targetDepartment("ALL").status(EventStatus.APPROVED).approvedAt(LocalDateTime.now()).build());
        operations.save(EventOperations.builder().event(event).attendanceMode("SINGLE_QR").singleQrPolicy("ENTRY_AND_ATTENDANCE")
                .certificateEvidencePolicy("POINT_ATTENDANCE").certificatePolicyVersion(1).recruitmentOpen(true).build());
        var group=groups.saveAndFlush(ChatGroup.builder().name("Synthetic permanent review group").groupType("PERMANENT_DEPARTMENT")
                .relatedDepartment(department.getName()).nightSilenceEnabled(true).build());
        for(var member:java.util.List.of(student,finder)) {
            var membership=new ChatMembership();membership.setGroupId(group.getId());membership.setUser(member);membership.setActive(true);memberships.save(membership);
        }
        for(String key:java.util.List.of("MODULE_CALENDAR","MODULE_READINESS","MODULE_INSIGHTS","MODULE_DISRUPTIONS","MODULE_CERTIFICATES","MODULE_OPERATIONS"))
            flags.findByFeatureKey(key).ifPresent(flag->{flag.setEnabled(true);flags.save(flag);});
        // Library challenge, scanner/device proof, uploads, certificates and academic results are NOT fabricated.
        // Their accepted state must arise from the actual reviewed API workflows.
    }
    private User user(String label,Role role,Department department,String identifier) {
        return users.saveAndFlush(User.builder().name("Synthetic "+label).email(label+"@example.invalid")
                .password(passwords.encode(password)).role(role).enabled(true).department(department.getName())
                .verifiedDepartmentId(department.getId()).verifiedEnrollmentNo(identifier).enrollmentNo(identifier)
                .enrollmentIdentityVerifiedAt(identifier==null?null:LocalDateTime.now()).enrollmentIdentityVerifiedBy(identifier==null?null:"SYNTHETIC_DEMO_FIXTURE")
                .enrollmentIdentitySourceReference(identifier==null?null:"SYNTHETIC_LOCAL_REVIEW").academicLevel(role==Role.STUDENT?"UG":"STAFF").build());
    }
}

package com.college.events.config;

import com.college.events.model.*;
import com.college.events.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@org.springframework.context.annotation.Profile("!professor-demo")
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class DataInitializer implements CommandLineRunner {

    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final HostelRepository hostelRepository;
    private final FacilityRepository facilityRepository;
    private final ChatGroupRepository chatGroupRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.default-admin-email:admin@cujammu.ac.in}")
    private String adminEmail;

    @Value("${app.default-admin-password:}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        seedCategories();
        seedAdminUser();
        seedCUJammuDepartments();
        seedCUJammuHostels();
        seedCUJammuFacilities();
        seedPermanentChatGroups();
    }

    private void seedCategories() {
        List<Object[]> categories = List.of(
            new Object[]{"Technical",  "bi-cpu",             "primary"},
            new Object[]{"Cultural",   "bi-music-note-beamed","danger"},
            new Object[]{"Sports",     "bi-trophy",          "success"},
            new Object[]{"Academic",   "bi-book",            "info"},
            new Object[]{"Social",     "bi-people",          "warning"},
            new Object[]{"Workshop",   "bi-tools",           "secondary"},
            new Object[]{"Hackathon",  "bi-laptop",          "dark"},
            new Object[]{"Seminar",    "bi-mic",             "primary"},
            new Object[]{"Conference", "bi-easel",           "info"},
            new Object[]{"Health & Welfare", "bi-heart-pulse","danger"},
            new Object[]{"Hostel Event", "bi-building",      "warning"},
            new Object[]{"Meeting",    "bi-calendar-event",  "secondary"}
        );

        for (Object[] cat : categories) {
            String name = (String) cat[0];
            if (!categoryRepository.existsByName(name)) {
                categoryRepository.save(Category.builder()
                    .name(name)
                    .icon((String) cat[1])
                    .colorClass((String) cat[2])
                    .build());
                log.info("Seeded category: {}", name);
            }
        }
    }

    private void seedAdminUser() {
        if (adminPassword == null || adminPassword.isBlank()) {
            log.info("Admin bootstrap skipped: no explicit bootstrap credential configured");
            return;
        }
        com.college.events.service.PasswordPolicy.validate(adminPassword);
        if (!userRepository.existsByEmail(adminEmail)) {
            userRepository.save(User.builder()
                .name("CUJammu System Administrator")
                .email(adminEmail)
                .password(passwordEncoder.encode(adminPassword))
                .role(Role.ADMIN)
                .department("Administration")
                .phone("01992249658")
                .academicLevel("STAFF")
                .enabled(true)
                .build());
            log.info("Default CUJammu admin created: {}", adminEmail);
        }
    }

    private void seedCUJammuDepartments() {
        List<String[]> depts = List.of(
            new String[]{"CSIT", "Computer Science and Information Technology"},
            new String[]{"MATH", "Mathematics"},
            new String[]{"PHY", "Physics and Astronomical Sciences"},
            new String[]{"CHEM", "Chemistry and Chemical Sciences"},
            new String[]{"HRM", "Human Resource Management and Organizational Behaviour"},
            new String[]{"TTM", "Tourism and Travel Management"},
            new String[]{"SCM", "Marketing and Supply Chain Management"},
            new String[]{"EDU", "Educational Studies"},
            new String[]{"CSE", "Computer Science and Engineering"},
            new String[]{"ECE", "Electronics and Communication Engineering"},
            new String[]{"ECON", "Economics"},
            new String[]{"PPPA", "Public Policy and Public Administration"},
            new String[]{"SW", "Social Work"},
            new String[]{"CRC", "Comparative Religion and Civilization"},
            new String[]{"MCNM", "Mass Communication and New Media"},
            new String[]{"ENG", "English"},
            new String[]{"HINDI", "Hindi and Other Indian Languages"},
            new String[]{"EVS", "Environmental Sciences"},
            new String[]{"ZOOL", "Zoology"},
            new String[]{"BOT", "Botany"},
            new String[]{"MBIO", "Molecular Biology"},
            new String[]{"NSS_DEPT", "National Security Studies"}
        );

        for (String[] d : depts) {
            String code = d[0];
            String name = d[1];
            if (!departmentRepository.existsByCode(code)) {
                departmentRepository.save(Department.builder()
                    .code(code)
                    .name(name)
                    .headOfDepartment("HOD, " + name)
                    .contactEmail(code.toLowerCase() + "@cujammu.ac.in")
                    .building("Academic Block, Rahya-Suchani Campus")
                    .active(true)
                    .build());
                log.info("Seeded CUJammu Department: {} ({})", name, code);
            }
        }
    }

    private void seedCUJammuHostels() {
        List<Object[]> hostels = List.of(
            new Object[]{"Brig. Rajendra Singh MVC Boys Hostel", "BOYS", "21:00", 350},
            new Object[]{"Shyama Prasad Mukherjee Boys Hostel", "BOYS", "21:00", 300},
            new Object[]{"Sailputri Girls Hostel", "GIRLS", "19:30", 400}
        );

        for (Object[] h : hostels) {
            String name = (String) h[0];
            if (!hostelRepository.existsByName(name)) {
                hostelRepository.save(Hostel.builder()
                    .name(name)
                    .genderType((String) h[1])
                    .curfewTime((String) h[2])
                    .capacity((Integer) h[3])
                    .wardenName("Warden, " + name)
                    .wardenEmail("warden." + ((String) h[1]).toLowerCase() + "@cujammu.ac.in")
                    .wardenPhone("01992249000")
                    .active(true)
                    .build());
                log.info("Seeded CUJammu Hostel: {}", name);
            }
        }
    }

    private void seedCUJammuFacilities() {
        List<Object[]> facilities = List.of(
            new Object[]{"Brig. Rajendra Singh Main Auditorium", "AUDITORIUM", "Admin Block", 800, true, true, true},
            new Object[]{"Mini Auditorium & Multi-Purpose Hall", "AUDITORIUM", "Academic Block 1", 300, true, true, true},
            new Object[]{"School of Business Seminar Hall", "SEMINAR_HALL", "Management Block", 150, true, true, false},
            new Object[]{"CSIT Advanced Computing Lab & Hall", "SEMINAR_HALL", "Science Block", 120, true, true, false},
            new Object[]{"Main Cricket & Athletics Ground", "SPORTS_GROUND", "Sports Complex", 2000, false, false, true},
            new Object[]{"Football & Kabaddi Arena", "SPORTS_GROUND", "Sports Complex", 1000, false, false, true},
            new Object[]{"Indoor Badminton & TT Courts", "SPORTS_GROUND", "Sports Gymnasium", 250, false, true, true},
            new Object[]{"Smart Lecture Theatre 101", "LECTURE_THEATRE", "Academic Block 2", 90, true, true, false}
        );

        for (Object[] f : facilities) {
            String name = (String) f[0];
            if (facilityRepository.findByName(name).isEmpty()) {
                facilityRepository.save(Facility.builder()
                    .name(name)
                    .facilityType((String) f[1])
                    .location((String) f[2])
                    .capacity((Integer) f[3])
                    .hasProjector((Boolean) f[4])
                    .hasSoundSystem((Boolean) f[5])
                    .hasStageLighting((Boolean) f[6])
                    .active(true)
                    .build());
                log.info("Seeded CUJammu Facility: {}", name);
            }
        }
    }

    private void seedPermanentChatGroups() {
        List<String[]> groups = List.of(
            new String[]{"CUJammu Central Student Lounge", "PERMANENT_UNIVERSITY", "ALL"},
            new String[]{"CUJammu Faculty Senate Room", "PERMANENT_FACULTY", "ALL"},
            new String[]{"CSIT Department Students", "PERMANENT_DEPARTMENT", "Computer Science and Information Technology"},
            new String[]{"CSE Department Students", "PERMANENT_DEPARTMENT", "Computer Science and Engineering"},
            new String[]{"Sports Department & Athletics Club", "CLUB", "Sports"}
        );

        for (String[] g : groups) {
            String name = g[0];
            String type = g[1];
            String dept = g[2];
            if (chatGroupRepository.findByGroupType(type).stream().noneMatch(grp -> grp.getName().equals(name))) {
                chatGroupRepository.save(ChatGroup.builder()
                    .name(name)
                    .groupType(type)
                    .relatedDepartment(dept)
                    .onlyAdminsCanPost(false)
                    .nightSilenceEnabled(true)
                    .build());
                log.info("Seeded Permanent Chat Group: {}", name);
            }
        }
    }
}

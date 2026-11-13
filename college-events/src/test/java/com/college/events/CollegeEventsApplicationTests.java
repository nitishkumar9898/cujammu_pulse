package com.college.events;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:application-smoke;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never",
    "app.default-admin-password=", "app.upload.dir=target/test-uploads/application-smoke",
    "logging.file.name=target/test-logs/application-smoke.log", "app.media.cleanup-cron=-",
    "app.identity.enabled=false", "app.email.backend=console"
})
@ActiveProfiles("h2")
class CollegeEventsApplicationTests {

    @Test
    void contextLoads() {
    }
}

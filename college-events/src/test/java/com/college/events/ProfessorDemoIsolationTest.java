package com.college.events;

import com.college.events.config.ProfessorDemoEnvironmentGuard;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ProfessorDemoIsolationTest {
    MockEnvironment valid() {
        var env=new MockEnvironment();env.setActiveProfiles("professor-demo");
        Path base=Path.of(System.getProperty("user.dir"),"target","professor-demo").toAbsolutePath();
        return env.withProperty("app.professor-demo.enabled","true").withProperty("app.professor-demo.password","SyntheticDemo123!")
                .withProperty("spring.datasource.url","jdbc:h2:mem:pulse-professor-demo;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
                .withProperty("spring.datasource.driver-class-name","org.h2.Driver").withProperty("spring.jpa.hibernate.ddl-auto","create-drop")
                .withProperty("spring.sql.init.mode","never").withProperty("server.address","127.0.0.1")
                .withProperty("app.upload.dir",base.resolve("uploads").toString()).withProperty("logging.file.name",base.resolve("logs/backend.log").toString())
                .withProperty("app.email.backend","console").withProperty("chatbot.ai.enabled","false").withProperty("app.identity.enabled","false");
    }
    void check(MockEnvironment env) {new ProfessorDemoEnvironmentGuard().postProcessEnvironment(env,new SpringApplication(CollegeEventsApplication.class));}
    @Test void validExplicitLoopbackEphemeralProfileAccepted() {assertDoesNotThrow(()->check(valid()));}
    @Test void productionOrFileDatabaseOrPublicListenerRejectedBeforeContext() {
        var prod=valid();prod.setActiveProfiles("professor-demo","prod");assertThrows(IllegalStateException.class,()->check(prod));
        assertThrows(IllegalStateException.class,()->check(valid().withProperty("spring.datasource.url","jdbc:h2:file:./data/eventhub")));
        assertThrows(IllegalStateException.class,()->check(valid().withProperty("server.address","0.0.0.0")));
    }
    @Test void providerAndWorkspacePathOverridesRejected() {
        assertThrows(IllegalStateException.class,()->check(valid().withProperty("app.identity.enabled","true")));
        assertThrows(IllegalStateException.class,()->check(valid().withProperty("app.email.backend","smtp")));
        assertThrows(IllegalStateException.class,()->check(valid().withProperty("app.upload.dir","./uploads")));
        assertThrows(IllegalStateException.class,()->check(valid().withProperty("logging.file.name","logs/eventhub.log")));
    }
    @Test void alternateDatasourceAndSqlInitializationCannotBypassMemoryUrlCheck() {
        assertThrows(IllegalStateException.class,()->check(valid().withProperty("spring.datasource.hikari.jdbc-url","jdbc:h2:file:./data/eventhub")));
        assertThrows(IllegalStateException.class,()->check(valid().withProperty("spring.datasource.jndi-name","java:comp/env/jdbc/Campus")));
        assertThrows(IllegalStateException.class,()->check(valid().withProperty("spring.sql.init.mode","always")));
        assertThrows(IllegalStateException.class,()->check(valid().withProperty("spring.datasource.hikari.connection-init-sql","SELECT 1")));
    }
}

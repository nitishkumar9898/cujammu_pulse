package com.college.events.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import java.nio.file.Path;
import java.util.Arrays;

/** Validate isolation BEFORE datasource creation or schema management can touch any records. */
public class ProfessorDemoEnvironmentGuard implements EnvironmentPostProcessor, Ordered {
    @Override public int getOrder() {return Ordered.LOWEST_PRECEDENCE;}
    @Override public void postProcessEnvironment(ConfigurableEnvironment env,SpringApplication application) {
        if(!Arrays.asList(env.getActiveProfiles()).contains("professor-demo")) return;
        if(Arrays.asList(env.getActiveProfiles()).contains("prod")||!env.getProperty("app.professor-demo.enabled",Boolean.class,false))
            throw new IllegalStateException("Professor demo must be explicitly enabled without a production profile");
        if(!"jdbc:h2:mem:pulse-professor-demo;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE".equals(env.getProperty("spring.datasource.url"))
                ||!"org.h2.Driver".equals(env.getProperty("spring.datasource.driver-class-name"))
                ||!"create-drop".equals(env.getProperty("spring.jpa.hibernate.ddl-auto")))
            throw new IllegalStateException("Professor demo requires its dedicated ephemeral database before any schema operation");
        if(!"never".equals(env.getProperty("spring.sql.init.mode")))
            throw new IllegalStateException("Professor demo forbids external SQL initialization");
        // Inspect canonicalized property names as well as values, including relaxed environment
        // spellings. Pool/JNDI/ORM alternate connections must not bypass the primary URL guard.
        for(var source:env.getPropertySources()) if(source instanceof org.springframework.core.env.EnumerablePropertySource<?> enumerable)
            for(String name:enumerable.getPropertyNames()) {
                String normalized=name.toLowerCase(java.util.Locale.ROOT).replaceAll("[_.-]","");
                if(normalized.startsWith("springdatasource")&&!java.util.Set.of("springdatasourceurl","springdatasourcedriverclassname","springdatasourceusername","springdatasourcepassword").contains(normalized)
                        ||normalized.startsWith("springjpapropertieshibernateconnection")||normalized.startsWith("springjpapropertiesjakartapersistencejdbc")
                        ||normalized.startsWith("springjpapropertiesjakartapersistence")||normalized.startsWith("springjpapropertiesjavaxpersistence")
                        ||normalized.startsWith("springjpapropertieshibernatehbm2ddl"))
                    throw new IllegalStateException("Professor demo forbids alternate datasource or SQL bootstrap configuration");
            }
        if(!"127.0.0.1".equals(env.getProperty("server.address")))
            throw new IllegalStateException("Professor demo must bind to loopback only");
        Path base=Path.of(System.getProperty("user.dir"),"target","professor-demo").toAbsolutePath().normalize();
        Path uploads=Path.of(env.getProperty("app.upload.dir","")).toAbsolutePath().normalize();
        Path logs=Path.of(env.getProperty("logging.file.name","")).toAbsolutePath().normalize();
        if(!uploads.equals(base.resolve("uploads"))||!logs.equals(base.resolve("logs/backend.log")))
            throw new IllegalStateException("Professor demo may write only its dedicated generated uploads and logs");
        if(!"console".equals(env.getProperty("app.email.backend"))||env.getProperty("chatbot.ai.enabled",Boolean.class,false)
                ||env.getProperty("app.identity.enabled",Boolean.class,false))
            throw new IllegalStateException("Professor demo cannot activate external email, AI or identity providers");
        com.college.events.service.PasswordPolicy.validate(env.getProperty("app.professor-demo.password"));
    }
}

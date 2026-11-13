package com.college.events.config;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * The current production profile has no approved Mongo persistence adapter.
 * Prevent Spring Boot's default H2 datasource from being selected by the
 * production container until the reviewed adapter and migration path exist.
 */
@Configuration(proxyBeanMethods = false)
@Profile("prod")
public class ProductionPersistenceGateConfiguration {
    static final String MESSAGE = "Production startup is blocked until the approved Mongo persistence adapter and migration path are implemented";

    @Bean
    @Primary
    DataSource productionPersistenceUnavailable() {
        throw new IllegalStateException(MESSAGE);
    }
}

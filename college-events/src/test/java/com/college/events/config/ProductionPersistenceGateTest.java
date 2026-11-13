package com.college.events.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionPersistenceGateTest {
    @Test
    void prodProfileFailsBeforeSpringCanSelectTheDefaultH2Datasource() {
        new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .withConfiguration(AutoConfigurations.of(
                        DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class))
                .withUserConfiguration(ProductionPersistenceGateConfiguration.class)
                .withPropertyValues(
                        "spring.datasource.url=jdbc:h2:mem:prod-gate-test;DB_CLOSE_DELAY=-1",
                        "spring.datasource.driver-class-name=org.h2.Driver",
                        "spring.datasource.username=sa",
                        "spring.datasource.password=",
                        "spring.jpa.hibernate.ddl-auto=create")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class);
                    assertThat(context.getStartupFailure()).hasRootCauseMessage(ProductionPersistenceGateConfiguration.MESSAGE);
                    try (var connection = DriverManager.getConnection(
                            "jdbc:h2:mem:prod-gate-test;DB_CLOSE_DELAY=-1", "sa", "");
                         Statement statement = connection.createStatement();
                         ResultSet tables = statement.executeQuery(
                                 "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'")) {
                        assertThat(tables.next()).isTrue();
                        assertThat(tables.getInt(1)).isZero();
                    } catch (Exception exception) {
                        throw new AssertionError("Could not verify production startup left the H2 schema untouched", exception);
                    }
                });
    }

    @Test
    void localProfileDoesNotActivateProductionPersistenceGate() {
        new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("professor-demo"))
                .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class))
                .withUserConfiguration(ProductionPersistenceGateConfiguration.class)
                .withPropertyValues("spring.datasource.url=jdbc:h2:mem:professor-demo-gate-test;DB_CLOSE_DELAY=-1")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(javax.sql.DataSource.class);
                });
    }
}

package com.college.events.service;

import com.college.events.model.Role;
import com.college.events.model.User;
import com.college.events.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false",
        "spring.sql.init.mode=never", "logging.file.name=target/test-logs/stream-account.log"}, showSql = false)
class NotificationStreamAccountPersistenceTest {
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;

    @Test void scalarAccountStateBypassesManagedEntityCacheAfterAccountRevocation() {
        User managed = users.saveAndFlush(User.builder().name("Synthetic stream account")
                .email("stream@example.invalid").password("synthetic-hash").role(Role.STUDENT).enabled(true).build());
        assertTrue(users.findCurrentStreamAccount(managed.getId()).orElseThrow().getEnabled());
        jdbc.update("update users set enabled = false, role = ?, email = ?, password = ? where id = ?",
                "ADMIN", "changed@example.invalid", "replaced-synthetic-hash", managed.getId());
        // findById deliberately remains cached; the stream projection must see stored values.
        assertSame(managed, users.findById(managed.getId()).orElseThrow());
        assertTrue(managed.isEnabled());
        var current = users.findCurrentStreamAccount(managed.getId()).orElseThrow();
        assertFalse(current.getEnabled());
        assertEquals(Role.ADMIN, current.getRole());
        assertEquals("changed@example.invalid", current.getEmail());
        assertEquals("replaced-synthetic-hash", current.getPassword());
    }
}

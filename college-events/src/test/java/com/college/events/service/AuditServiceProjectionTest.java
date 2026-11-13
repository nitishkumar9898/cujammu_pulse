package com.college.events.service;

import com.college.events.model.AuditLog;
import com.college.events.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuditServiceProjectionTest {
    @Test
    void featureHistoryRedactsEmailAndProjectsOnlySafeFields() {
        AuditLogRepository repo = mock(AuditLogRepository.class);
        when(repo.findByActionOrderByTimestampDesc(eq("FEATURE_STATE_CHANGED"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(AuditLog.builder().id(4L).userEmail("secret@example.test")
                        .role("ADMIN").action("FEATURE_STATE_CHANGED")
                        .details("feature=MODULE_EVENTS;previous=true;enabled=false;reason=maintenance")
                        .timestamp(LocalDateTime.now()).build())));

        var page = new AuditService(repo).featureControlHistory(0, 25);

        assertEquals("MODULE_EVENTS", page.items().get(0).featureKey());
        assertFalse(page.items().get(0).enabled());
        assertEquals("maintenance", page.items().get(0).reason());
        assertFalse(page.toString().contains("secret@example.test"));
    }
}

package com.college.events.service;

import com.college.events.model.*;
import com.college.events.repository.LibraryGateLogRepository;
import com.college.events.security.DomainAccess;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LibraryContinuityTest {
    final LibraryGateLogRepository visits=mock(LibraryGateLogRepository.class);
    final DomainAccess access=mock(DomainAccess.class);
    final AuditService audit=mock(AuditService.class);
    final LibraryContinuityService service=new LibraryContinuityService(visits,access,audit);
    final User owner=User.builder().id(1L).email("owner@example.invalid").role(Role.STUDENT).build();
    LibraryGateLog visit(String state) {
        when(access.currentUser()).thenReturn(owner);
        var visit=LibraryGateLog.builder().id(5L).user(owner).sessionType("STUDY").entryTime(LocalDateTime.now().minusMinutes(30)).status(state).revision(3L).build();
        when(visits.findOwnedForSafeExit(5L,1L)).thenReturn(Optional.of(visit));return visit;
    }
    LibraryContinuityService.Close command(long revision) {return new LibraryContinuityService.Close(UUID.randomUUID().toString(),revision,"SERVICE_DISABLED");}
    @Test void safeExitClosesButDoesNotInventCreditedDuration() {
        var visit=visit("ACTIVE");visit.setDurationSeconds(999L);
        var result=service.close(5L,command(3L));
        assertEquals("SAFE_EXIT_UNVERIFIED",result.status());assertEquals("UNVERIFIED",result.durationQuality());
        assertNull(visit.getDurationSeconds());assertNotNull(visit.getExitTime());assertEquals("Unverified safe exit",visit.getDurationFormatted());
        verify(visits).saveAndFlush(visit);
    }
    @Test void foreignIdCannotChangeState() {
        when(access.currentUser()).thenReturn(owner);when(visits.findOwnedForSafeExit(5L,1L)).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class,()->service.close(5L,command(0L)));verify(visits,never()).saveAndFlush(any());verifyNoInteractions(audit);
    }
    @Test void legacyUnknownStatusAndStaleRevisionArePreserved() {
        var visit=visit("LEGACY_UNKNOWN");assertThrows(IllegalStateException.class,()->service.close(5L,command(3L)));
        assertEquals("LEGACY_UNKNOWN",visit.getStatus());visit.setStatus("ACTIVE");
        assertThrows(IllegalStateException.class,()->service.close(5L,command(2L)));assertEquals("ACTIVE",visit.getStatus());
        verify(visits,never()).saveAndFlush(any());verifyNoInteractions(audit);
    }
    @Test void replayUsesOriginalRevisionAndDoesNotRepeatAudit() {
        var visit=visit("ACTIVE");var command=command(3L);service.close(5L,command);visit.setRevision(4L);
        var retry=service.close(5L,command);assertEquals(4L,retry.revision());
        verify(visits,times(1)).saveAndFlush(visit);verify(audit,times(1)).logAction(anyString(),anyString(),eq("LIBRARY_SAFE_EXIT_RECORDED"),anyString());
        assertThrows(IllegalStateException.class,()->service.close(5L,new LibraryContinuityService.Close(command.requestKey(),3L,"ACCESSIBILITY_SUPPORT")));
    }
}

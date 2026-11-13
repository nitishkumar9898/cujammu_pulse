package com.college.events.service;

import com.college.events.dto.LostFoundDtos;
import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.Pageable;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Independent negative-path checks; database contention is covered separately. */
class LostFoundWorkflowTest {
    final LostAndFoundRepository items=mock(LostAndFoundRepository.class);
    final LostFoundClaimRepository claims=mock(LostFoundClaimRepository.class);
    final FeatureFlagService flags=mock(FeatureFlagService.class);
    final DomainAccess access=mock(DomainAccess.class);
    final AuditService audit=mock(AuditService.class);
    final NotificationService notifications=mock(NotificationService.class);
    final LostAndFoundService service=new LostAndFoundService(items,claims,flags,access,audit,notifications,
            Validation.buildDefaultValidatorFactory().getValidator());
    final User finder=User.builder().id(1L).enabled(true).role(Role.STUDENT).email("finder@example.invalid").build();
    final User stranger=User.builder().id(2L).enabled(true).role(Role.STUDENT).email("stranger@example.invalid").build();
    LostAndFoundItem item() { return LostAndFoundItem.builder().id(10L).version(0L).reportedBy(finder).status("REPORTED")
            .title("Keys").category("KEYS").description("Found keys").locationFound("Office").build(); }
    @Test void unrelatedAccountCannotPerformHandover() {
        when(access.currentUser()).thenReturn(stranger); when(items.findForUpdate(10L)).thenReturn(Optional.of(item()));
        assertThrows(AccessDeniedException.class,()->service.handover(10L,new LostFoundDtos.Handover(UUID.randomUUID().toString(),9L,0L,"Verified proof")));
        verifyNoInteractions(claims,notifications,audit);
    }
    @Test void finderCannotClaimOwnItem() {
        when(access.currentUser()).thenReturn(finder); when(items.findForUpdate(10L)).thenReturn(Optional.of(item()));
        assertThrows(AccessDeniedException.class,()->service.claim(10L,new LostFoundDtos.Claim(UUID.randomUUID().toString(),0L,"Private evidence")));
        verifyNoInteractions(claims,notifications,audit);
    }
    @Test void otherAccountEvidenceReadsUseCallerScope() {
        when(access.currentUser()).thenReturn(stranger); when(items.findById(10L)).thenReturn(Optional.of(item()));
        when(claims.findByItemIdAndClaimantIdOrderByIdAsc(eq(10L),eq(2L),any(Pageable.class))).thenReturn(List.of());
        assertTrue(service.claims(10L,0).isEmpty());
        verify(claims,never()).findByItemIdOrderByIdAsc(anyLong(),any(Pageable.class));
    }
    @Test void staleClaimCannotWriteEvidenceOrNotify() {
        when(access.currentUser()).thenReturn(stranger); when(items.findForUpdate(10L)).thenReturn(Optional.of(item()));
        when(claims.findByItemIdAndRequestKey(anyLong(),anyString())).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class,()->service.claim(10L,new LostFoundDtos.Claim(UUID.randomUUID().toString(),1L,"Private proof")));
        verify(claims,never()).saveAndFlush(any()); verifyNoInteractions(audit,notifications);
    }
    @Test void legacyUnboundClaimCannotBeHandedOver() {
        var legacy=item(); legacy.setStatus("CLAIMED");
        when(access.currentUser()).thenReturn(finder); when(items.findForUpdate(10L)).thenReturn(Optional.of(legacy));
        when(claims.findByItemIdAndHandoverRequestKey(anyLong(),anyString())).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class,()->service.handover(10L,new LostFoundDtos.Handover(UUID.randomUUID().toString(),9L,0L,"Legacy claimant text")));
        verify(claims,never()).findById(anyLong()); verifyNoInteractions(audit,notifications);
    }
}

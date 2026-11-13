package com.college.events.service;

import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import com.college.events.dto.StudentIdentifierDtos;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StudentDepartmentScopeTest {
    final UserRepository users=mock(UserRepository.class);
    final DepartmentRepository departments=mock(DepartmentRepository.class);
    final DomainAccess access=mock(DomainAccess.class);
    final StudentScoreCardRepository cards=mock(StudentScoreCardRepository.class);
    final AcademicAuthorityService authority=mock(AcademicAuthorityService.class);
    final AuditService audit=mock(AuditService.class);
    final CoEAcademicService service=new CoEAcademicService(mock(ExamScheduleRepository.class),cards,users,access,audit,authority,mock(NotificationService.class),departments);
    final User publisher=User.builder().id(1L).role(Role.STAFF).email("publisher@example.invalid").build();
    final User student=User.builder().id(2L).enabled(true).role(Role.STUDENT).department("Self edited department")
            .verifiedEnrollmentNo("000123").enrollmentIdentityVerifiedAt(LocalDateTime.now()).name("Synthetic Student").build();
    void setupStudent() {
        when(access.currentUser()).thenReturn(publisher);
        when(users.findByVerifiedEnrollmentNoForAcademicUpdate("000123")).thenReturn(Optional.of(student));
    }
    @Test void selfEditedDepartmentCannotPermitAcademicSubmission() {
        setupStudent();
        assertThrows(IllegalStateException.class,()->service.distributeScoreCard("000123","Program","1","2026",8.0,8.0,"PASS",null,UUID.randomUUID().toString(),null));
        verifyNoInteractions(authority,cards,audit);
    }
    @Test void verifiedRegistryDepartmentDeterminesPublicationGrantAndStoredScope() {
        setupStudent();student.setVerifiedDepartmentId(5L);
        when(departments.findById(5L)).thenReturn(Optional.of(Department.builder().id(5L).name("Official Engineering").active(true).build()));
        when(authority.hasCapability(publisher,AcademicAuthorityService.Capability.PUBLISH,"Official Engineering")).thenReturn(true);
        when(cards.findBySubmissionRequestKey(anyString())).thenReturn(Optional.empty());
        when(cards.findTopByRollNumberAndSemesterAndAcademicYearOrderByPublicationVersionDesc(anyString(),anyString(),anyString())).thenReturn(Optional.empty());
        when(cards.save(any())).thenAnswer(call->{StudentScoreCard card=call.getArgument(0);card.setId(9L);return card;});
        service.distributeScoreCard("000123","Program","1","2026",8.0,8.0,"PASS",null,UUID.randomUUID().toString(),null);
        var captured=org.mockito.ArgumentCaptor.forClass(StudentScoreCard.class);verify(cards).save(captured.capture());
        assertEquals("Official Engineering",captured.getValue().getDepartmentName());
        assertEquals("PENDING_REVIEW",captured.getValue().getPublicationStatus());
        verify(authority,never()).hasCapability(any(),any(),eq("Self edited department"));
    }
    @Test void legacyIdentifierVerificationWithoutDepartmentFailsBeforeMutation() {
        when(access.requireRole(Role.ADMIN)).thenReturn(publisher);when(users.findForUpdate(2L)).thenReturn(Optional.of(student));
        student.setVerifiedEnrollmentNo(null);student.setEnrollmentIdentityVerifiedAt(null);
        var verification=new StudentIdentifierService(users,access,audit,departments,mock(NotificationService.class));
        assertThrows(IllegalArgumentException.class,()->verification.verify(2L,new StudentIdentifierDtos.Verify(0L,"000123","Synthetic source","Reviewed")));
        verify(users,never()).saveAndFlush(any());verifyNoInteractions(departments,audit);
    }
}

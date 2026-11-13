package com.college.events.service;

import com.college.events.dto.StudentIdentifierDtos;
import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;

/** Independent transactions against real H2/JPA; this is not Mongo concurrency qualification. */
@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never",
        "logging.file.name=target/test-logs/student-identifier-concurrency.log"}, showSql=false)
@Import({StudentIdentifierService.class, DomainAccess.class, AuditService.class, NotificationService.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class StudentIdentifierConcurrencyTest {
    @Autowired StudentIdentifierService service;
    @Autowired UserRepository users;
    @Autowired DepartmentRepository departments;
    @Autowired FeatureFlagRepository flags;
    @Autowired AuditLogRepository audits;
    @Autowired NotificationRepository notifications;
    @Autowired NotificationDeliveryIntentRepository intents;
    @MockitoSpyBean AuditService audit;
    User administrator;
    Department department;

    @BeforeEach void setup() {
        administrator = account(Role.ADMIN);
        department = departments.saveAndFlush(Department.builder()
                .code(UUID.randomUUID().toString().substring(0,12))
                .name("Synthetic identity department " + UUID.randomUUID()).active(true).build());
        var flag = flags.findByFeatureKey("MODULE_NOTIFICATIONS").orElseGet(() ->
                FeatureFlag.builder().featureKey("MODULE_NOTIFICATIONS").displayName("Synthetic notifications").enabled(true).build());
        flag.setEnabled(true);
        flags.saveAndFlush(flag);
    }

    @Test void simultaneousConflictingBindingsForSameStudentHaveExactlyOneWinner() throws Exception {
        User student = account(Role.STUDENT);
        long beforeAudits=audits.count(), beforeNotifications=notifications.count(), beforeIntents=intents.count();
        var outcomes = race(student, "000101-"+student.getId(), student, "000102-"+student.getId());
        assertEquals(1, outcomes.stream().filter(Outcome::succeeded).count());
        Outcome loser=outcomes.stream().filter(outcome -> !outcome.succeeded()).findFirst().orElseThrow();
        assertInstanceOf(IllegalStateException.class, loser.failure());
        var winner=outcomes.stream().filter(Outcome::succeeded).findFirst().orElseThrow().receipt();
        User saved=users.findById(student.getId()).orElseThrow();
        assertEquals(winner.verifiedIdentifier(), saved.getVerifiedEnrollmentNo());
        assertEquals(department.getId(), saved.getVerifiedDepartmentId());
        assertEquals(1, saved.getCredentialVersion());
        assertEquals(beforeAudits+1,audits.count());
        assertEquals(beforeNotifications+1,notifications.count());
        assertEquals(beforeIntents+1,intents.count());
    }

    @Test void twoStudentsCannotCommitSameIdentifierAndLoserHasNoPartialBinding() throws Exception {
        User first=account(Role.STUDENT), second=account(Role.STUDENT);
        String identifier="000200-"+UUID.randomUUID();
        long beforeAudits=audits.count(), beforeNotifications=notifications.count(), beforeIntents=intents.count();
        var outcomes=race(first,identifier,second,identifier);
        assertEquals(1,outcomes.stream().filter(Outcome::succeeded).count());
        Throwable failure=outcomes.stream().filter(outcome -> !outcome.succeeded()).findFirst().orElseThrow().failure();
        assertTrue(failure instanceof IllegalArgumentException || failure instanceof DataIntegrityViolationException,
                () -> "Unexpected race failure type: "+failure.getClass().getName());
        User winner=users.findByVerifiedEnrollmentNo(identifier).orElseThrow();
        User loser=users.findById(winner.getId().equals(first.getId())?second.getId():first.getId()).orElseThrow();
        assertUnbound(loser);
        assertEquals(beforeAudits+1,audits.count());
        assertEquals(beforeNotifications+1,notifications.count());
        assertEquals(beforeIntents+1,intents.count());
    }

    @Test void simultaneousIdenticalRetriesReturnOneImmutableBindingAndOneNotice() throws Exception {
        User student=account(Role.STUDENT);
        String identifier="000300-"+student.getId();
        long beforeAudits=audits.count(), beforeNotifications=notifications.count(), beforeIntents=intents.count();
        var outcomes=race(student,identifier,student,identifier);
        assertEquals(2,outcomes.stream().filter(Outcome::succeeded).count());
        var left=outcomes.get(0).receipt();var right=outcomes.get(1).receipt();
        assertEquals(left.studentId(),right.studentId());
        assertEquals(left.verifiedIdentifier(),right.verifiedIdentifier());
        // JPA timestamps round to the database's microsecond precision after a fresh read.
        assertTrue(java.time.Duration.between(left.verifiedAt(),right.verifiedAt()).abs().toNanos() <= 1_000,
                "Replay must preserve the persisted timestamp to database microsecond precision");
        assertEquals(1,left.credentialVersion());assertEquals(1,right.credentialVersion());
        assertEquals(beforeAudits+1,audits.count());
        assertEquals(beforeNotifications+1,notifications.count());
        assertEquals(beforeIntents+1,intents.count());
    }

    @Test void auditFailureAfterInsertRollsBackBindingAndAuditWithoutCreatingNotice() {
        User student=account(Role.STUDENT);
        long beforeAudits=audits.count(), beforeNotifications=notifications.count(), beforeIntents=intents.count();
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("Synthetic audit persistence failure");
        }).when(audit).logAction(anyString(),anyString(),eq("STUDENT_IDENTIFIER_VERIFIED"),anyString());
        authenticate();
        try {
            assertThrows(IllegalStateException.class, () -> service.verify(student.getId(), command(student,"000400-"+student.getId())));
        } finally { SecurityContextHolder.clearContext(); }
        assertUnbound(users.findById(student.getId()).orElseThrow());
        assertEquals(beforeAudits,audits.count());
        assertEquals(beforeNotifications,notifications.count());
        assertEquals(beforeIntents,intents.count());
    }

    private List<Outcome> race(User first,String firstIdentifier,User second,String secondIdentifier) throws Exception {
        var executor=Executors.newFixedThreadPool(2);
        var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        try {
            var left=executor.submit(() -> invokeAfterStart(first,firstIdentifier,ready,start));
            var right=executor.submit(() -> invokeAfterStart(second,secondIdentifier,ready,start));
            assertTrue(ready.await(5,TimeUnit.SECONDS));start.countDown();
            return List.of(left.get(20,TimeUnit.SECONDS),right.get(20,TimeUnit.SECONDS));
        } finally { start.countDown();executor.shutdownNow();assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS)); }
    }
    private Outcome invokeAfterStart(User student,String identifier,CountDownLatch ready,CountDownLatch start) throws Exception {
        authenticate();ready.countDown();
        try {
            if(!start.await(5,TimeUnit.SECONDS)) throw new TimeoutException("Synthetic race did not start");
            try { return new Outcome(service.verify(student.getId(),command(student,identifier)),null); }
            catch(RuntimeException failure) { return new Outcome(null,failure); }
        } finally { SecurityContextHolder.clearContext(); }
    }
    private void authenticate() { SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(administrator,null,administrator.getAuthorities())); }
    private StudentIdentifierDtos.Verify command(User student,String identifier) {
        return new StudentIdentifierDtos.Verify(student.getCredentialVersion(),identifier,
                "Synthetic official source", "Synthetic independent concurrency review", department.getId());
    }
    private User account(Role role) { return users.saveAndFlush(User.builder().name("Synthetic identity actor")
            .email(UUID.randomUUID()+"@example.invalid").password("synthetic-only").role(role).enabled(true).build()); }
    private static void assertUnbound(User student) {
        assertNull(student.getVerifiedEnrollmentNo());assertNull(student.getVerifiedDepartmentId());
        assertNull(student.getEnrollmentIdentityVerifiedAt());assertNull(student.getEnrollmentIdentityVerifiedBy());
        assertNull(student.getEnrollmentIdentitySourceReference());assertNull(student.getEnrollmentIdentityReviewReason());
        assertEquals(0,student.getCredentialVersion());
    }
    private record Outcome(StudentIdentifierDtos.Result receipt,RuntimeException failure) { boolean succeeded(){return receipt!=null;} }
}

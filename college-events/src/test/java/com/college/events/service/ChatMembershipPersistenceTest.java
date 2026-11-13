package com.college.events.service;

import com.college.events.dto.ChatMembershipDtos.Change;
import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never",
        "logging.file.name=target/test-logs/chat-membership.log"}, showSql=false)
@Import({ChatMembershipService.class, CommunityChatService.class, ChatPostingPolicy.class, DomainAccess.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class ChatMembershipPersistenceTest {
    @Autowired ChatMembershipService service;
    @Autowired CommunityChatService chat;
    @Autowired UserRepository users;
    @Autowired ChatGroupRepository groups;
    @Autowired ChatMembershipRepository members;
    @Autowired ChatMembershipChangeRepository changes;
    @Autowired ChatMessageRepository messages;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean FeatureFlagService features;
    @MockitoBean AuditService audit;
    @MockitoBean NotificationService notifications;
    User admin, student; ChatGroup group;

    @BeforeEach void setup() {
        admin=user(Role.ADMIN); student=user(Role.STUDENT);
        group=groups.saveAndFlush(ChatGroup.builder().name("Synthetic private department")
                .groupType("PERMANENT_DEPARTMENT").relatedDepartment("Synthetic department")
                .nightSilenceEnabled(false).build());
        when(features.isEnabled("MODULE_COMMUNITY_CHAT")).thenReturn(true);
        when(features.isEnabled("MODULE_EVENTS")).thenReturn(true);
        as(admin);
    }
    @AfterEach void clearActor() { SecurityContextHolder.clearContext(); }
    private User user(Role role) {
        return users.saveAndFlush(User.builder().name("Synthetic "+role).email(UUID.randomUUID()+"@example.invalid")
                .password("synthetic-hash").role(role).enabled(true).department("Synthetic department").build());
    }
    private void as(User user) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
    private Change grant(Long revision) { return new Change(revision,true,"SYNTHETIC-REGISTER-1","Reviewed synthetic assignment"); }
    private Change revoke(long revision) { return new Change(revision,false,null,"Synthetic assignment ended"); }

    @Test void explicitGrantEnablesPrivateChatAndRevokeRemovesEveryReadWritePath() {
        as(student);
        assertTrue(chat.groupPage(0).items().isEmpty());
        assertThrows(AccessDeniedException.class,()->chat.getGroupMessages(group.getId()));
        as(admin); var granted=service.change(group.getId(),student.getId(),grant(null));
        as(student);
        student.setDepartment("Unrelated self-edited department"); users.saveAndFlush(student);
        assertEquals(1,chat.groupPage(0).items().size());
        assertEquals("Private synthetic message",chat.postMessage(group.getId(), "Private synthetic message", java.util.UUID.randomUUID().toString()).content());
        assertEquals(1,chat.getGroupMessages(group.getId()).items().size());
        as(admin); var revoked=service.change(group.getId(),student.getId(),revoke(granted.revision()));
        assertTrue(revoked.revision()>granted.revision());
        var provenance=changes.findAll().stream().filter(row->row.getGroupId()==group.getId()).toList();
        assertEquals(2,provenance.size());
        assertEquals("SYNTHETIC-REGISTER-1",provenance.get(0).getSourceReference());
        assertTrue(provenance.get(0).isActive()); assertFalse(provenance.get(1).isActive());
        assertEquals(admin.getId().longValue(),provenance.get(0).getActorId());
        as(student);
        assertTrue(chat.groupPage(0).items().isEmpty());
        assertThrows(AccessDeniedException.class,()->chat.getGroupMessages(group.getId()));
        assertThrows(AccessDeniedException.class,()->chat.postMessage(group.getId(), "Denied", java.util.UUID.randomUUID().toString()));
        verify(notifications,times(2)).createNotification(any(),anyString(),eq("/app/"));
        verify(audit,times(2)).logAction(anyString(),eq("ADMIN"),eq("CHAT_MEMBERSHIP_CHANGED"),anyString());
    }

    @Test void adminCanManageGrantsWithoutReadingMessagesAndCannotSelfGrant() {
        assertThrows(AccessDeniedException.class,()->service.change(group.getId(),admin.getId(),grant(null)));
        service.change(group.getId(),student.getId(),grant(null));
        assertEquals(1,service.members(group.getId(),0).items().size());
        assertTrue(chat.groupPage(0).items().isEmpty());
        assertThrows(AccessDeniedException.class,()->chat.getGroupMessages(group.getId()));
        assertThrows(AccessDeniedException.class,()->chat.postMessage(group.getId(), "Admin cannot read/post private chat", java.util.UUID.randomUUID().toString()));
        as(student); assertThrows(AccessDeniedException.class,()->service.members(group.getId(),0));
        assertThrows(AccessDeniedException.class,()->service.change(group.getId(),student.getId(),grant(null)));
    }

    @Test void duplicateOrStaleMutationDoesNotAppendEvidenceOrNotifications() {
        var granted=service.change(group.getId(),student.getId(),grant(null));
        assertThrows(IllegalStateException.class,()->service.change(group.getId(),student.getId(),grant(null)));
        assertThrows(IllegalStateException.class,()->service.change(group.getId(),student.getId(),grant(granted.revision())));
        var revoked=service.change(group.getId(),student.getId(),revoke(granted.revision()));
        assertThrows(IllegalStateException.class,()->service.change(group.getId(),student.getId(),grant(granted.revision())));
        assertEquals(revoked.revision(),members.findByGroupIdAndUserId(group.getId(),student.getId()).orElseThrow().getRevision());
        assertEquals(2,changes.findAll().stream().filter(row->row.getGroupId()==group.getId()).count());
        verify(notifications,times(2)).createNotification(any(),anyString(),anyString());
    }

    @Test void disabledAccountDeniedAndRevocationStillWorksWhileChatDisabled() {
        student.setEnabled(false); student=users.saveAndFlush(student);
        assertThrows(AccessDeniedException.class,()->service.change(group.getId(),student.getId(),grant(null)));
        student.setEnabled(true); student=users.saveAndFlush(student);
        var granted=service.change(group.getId(),student.getId(),grant(null));
        student.setEnabled(false); student=users.saveAndFlush(student); as(student);
        assertThrows(AccessDeniedException.class,()->chat.groupPage(0));
        assertThrows(AccessDeniedException.class,()->chat.getGroupMessages(group.getId()));
        assertThrows(AccessDeniedException.class,()->chat.postMessage(group.getId(), "Disabled", java.util.UUID.randomUUID().toString()));
        as(admin); when(features.isEnabled("MODULE_COMMUNITY_CHAT")).thenReturn(false);
        doThrow(new IllegalStateException("Disabled")).when(features).requireEnabledForUpdate("MODULE_COMMUNITY_CHAT");
        assertEquals(1,service.members(group.getId(),0).items().size());
        assertFalse(service.change(group.getId(),student.getId(),revoke(granted.revision())).active());
    }

    @Test void twoAdministratorsCannotBothMutateTheSameRevision() throws Exception {
        var grant=service.change(group.getId(),student.getId(),grant(null));
        var pool=Executors.newFixedThreadPool(2); var ready=new CountDownLatch(2); var go=new CountDownLatch(1);
        try {
            Callable<Boolean> task=()->{ as(admin); ready.countDown(); assertTrue(go.await(10,TimeUnit.SECONDS));
                try { service.change(group.getId(),student.getId(),revoke(grant.revision())); return true; }
                catch(IllegalStateException conflict) { return false; }
                finally { SecurityContextHolder.clearContext(); } };
            var a=pool.submit(task); var b=pool.submit(task); assertTrue(ready.await(10,TimeUnit.SECONDS)); go.countDown();
            assertNotEquals(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
            assertEquals(2,changes.findAll().stream().filter(row->row.getGroupId()==group.getId()).count());
        } finally { go.countDown(); pool.shutdownNow(); }
    }

    @Test void directoryPaginationNeverLeaksUnassignedGroups() {
        var ownIds=new java.util.HashSet<Long>();
        for(int i=0;i<21;i++) {
            var privateGroup=groups.saveAndFlush(ChatGroup.builder().name("Synthetic group "+i)
                    .groupType("PERMANENT_DEPARTMENT").relatedDepartment("Synthetic").nightSilenceEnabled(false).build());
            var member=new ChatMembership(); member.setGroupId(privateGroup.getId()); member.setUser(student); member.setActive(true);
            members.saveAndFlush(member); ownIds.add(privateGroup.getId());
        }
        as(student);
        var first=chat.groupPage(0); var next=chat.groupPage(1);
        assertEquals(20,first.items().size()); assertTrue(first.hasMore());
        assertEquals(1,next.items().size()); assertFalse(next.hasMore());
        var actual=new java.util.HashSet<Long>();
        first.items().forEach(item->actual.add(item.id())); next.items().forEach(item->actual.add(item.id()));
        assertEquals(ownIds,actual);
        assertFalse(actual.contains(group.getId()));
    }

    @Test void committedRevocationWinsAgainstBlockedReadAndPost() throws Exception {
        var grant=service.change(group.getId(),student.getId(),grant(null));
        var pool=Executors.newFixedThreadPool(3); var revoked=new CountDownLatch(1); var release=new CountDownLatch(1);
        var attempts=new CountDownLatch(2);
        try {
            var revocation=pool.submit(()->{ as(admin); try { new TransactionTemplate(transactions).executeWithoutResult(status->{
                service.change(group.getId(),student.getId(),revoke(grant.revision())); revoked.countDown();
                try { assertTrue(release.await(10,TimeUnit.SECONDS)); } catch(InterruptedException e) { throw new RuntimeException(e); }
            }); } finally { SecurityContextHolder.clearContext(); } });
            assertTrue(revoked.await(10,TimeUnit.SECONDS));
            var posting=pool.submit(()->{ as(student); attempts.countDown(); try { assertThrows(AccessDeniedException.class,()->chat.postMessage(group.getId(), "Must never commit", java.util.UUID.randomUUID().toString())); }
                finally { SecurityContextHolder.clearContext(); } });
            var reading=pool.submit(()->{ as(student); attempts.countDown(); try { assertThrows(AccessDeniedException.class,()->chat.getGroupMessages(group.getId())); }
                finally { SecurityContextHolder.clearContext(); } });
            assertTrue(attempts.await(10,TimeUnit.SECONDS));
            assertThrows(TimeoutException.class,()->posting.get(100,TimeUnit.MILLISECONDS));
            assertThrows(TimeoutException.class,()->reading.get(100,TimeUnit.MILLISECONDS));
            release.countDown(); revocation.get(20,TimeUnit.SECONDS); posting.get(20,TimeUnit.SECONDS); reading.get(20,TimeUnit.SECONDS);
            assertEquals(0,messages.findAll().stream().filter(row->row.getGroup().getId().equals(group.getId())).count());
        } finally { release.countDown(); pool.shutdownNow(); }
    }
}

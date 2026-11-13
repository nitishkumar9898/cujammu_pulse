package com.college.events.service;

import com.college.events.dto.HelpdeskDtos.Create;
import com.college.events.model.Role;
import com.college.events.model.SupportTicket;
import com.college.events.model.SupportTicketCounter;
import com.college.events.model.User;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.show-sql=false","spring.sql.init.mode=never",
        "spring.datasource.url=jdbc:h2:mem:helpdesk-bounded-pool;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.hikari.maximum-pool-size=2",
        "spring.datasource.hikari.connection-timeout=1500","logging.file.name=target/test-logs/helpdesk-concurrency.log"},showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class HelpdeskConcurrencyTest {
    @Autowired SupportTicketRepository tickets;
    @Autowired SupportTicketCounterRepository counters;
    @Autowired SupportTicketEntryRepository entries;
    @Autowired UserRepository users;
    @Autowired EventRepository events;
    @Autowired NotificationRepository notifications;
    @Autowired PlatformTransactionManager manager;

    @Test void concurrentFirstMonthAllocationIsUniqueAndSurvivesServiceRestart() throws Exception {
        var allocator=allocator("2040-01-10T00:00:00Z");var pool=Executors.newFixedThreadPool(8);var ready=new CountDownLatch(8);var go=new CountDownLatch(1);
        try {
            var futures=new ArrayList<Future<String>>();
            for(int i=0;i<8;i++)futures.add(pool.submit(()->{ready.countDown();assertTrue(go.await(10,TimeUnit.SECONDS));return allocator.nextNumber();}));
            assertTrue(ready.await(10,TimeUnit.SECONDS));go.countDown();var assigned=new HashSet<String>();
            for(var future:futures)assigned.add(future.get(20,TimeUnit.SECONDS));
            assertEquals(8,assigned.size());assertTrue(assigned.contains("CUJ-TKT-204001-0001"));assertTrue(assigned.contains("CUJ-TKT-204001-0008"));
            assertEquals("CUJ-TKT-204001-0009",allocator("2040-01-10T00:00:00Z").nextNumber());
        }finally{go.countDown();pool.shutdownNow();}
    }

    @Test void rolloverUsesCampusMonthAndNumbersExpandBeyondFourDigits() {
        counters.saveAndFlush(new SupportTicketCounter("204002",9999));
        assertEquals("CUJ-TKT-204002-10000",allocator("2040-02-20T00:00:00Z").nextNumber());
        assertEquals("CUJ-TKT-204003-0001",allocator("2040-02-29T18:30:00Z").nextNumber());
    }

    @Test void counterBootstrapPreservesExistingMonthlyTickets() {
        tickets.saveAndFlush(SupportTicket.builder().ticketNumber("CUJ-TKT-204004-0017").subject("Synthetic legacy ticket").description("Synthetic").build());
        assertEquals("CUJ-TKT-204004-0018",allocator("2040-04-20T00:00:00Z").nextNumber());
        assertTrue(tickets.findByTicketNumber("CUJ-TKT-204004-0017").isPresent());
    }

    @Test void concurrentDuplicateRequestsCreateOneTicketThreadAndNotification() throws Exception {
        User owner=users.saveAndFlush(User.builder().name("Synthetic requester").email("helpdesk-concurrent@example.invalid").password("synthetic-hash").role(Role.STUDENT).enabled(true).build());
        var flags=mock(FeatureFlagService.class);when(flags.isFeatureEnabled("MODULE_HELPDESK")).thenReturn(true);
        var audit=mock(AuditService.class);
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var service=new SupportHelpdeskService(tickets,entries,users,new DomainAccess(users,events),flags,allocator("2040-05-20T00:00:00Z"),audit,notifications,factory.getValidator(),manager);
            var command=new Create("00000000-0000-0000-0000-000000000099","Synthetic duplicate","Private synthetic description","GENERAL","URGENT");
            var pool=Executors.newFixedThreadPool(6);var ready=new CountDownLatch(6);var go=new CountDownLatch(1);
            try {
                var futures=new ArrayList<Future<String>>();
                for(int i=0;i<6;i++)futures.add(pool.submit(()->{
                    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(owner,null,owner.getAuthorities()));
                    try {ready.countDown();assertTrue(go.await(10,TimeUnit.SECONDS));return service.create(command).id();}
                    finally {SecurityContextHolder.clearContext();}
                }));
                assertTrue(ready.await(10,TimeUnit.SECONDS));go.countDown();var ids=new HashSet<String>();for(var future:futures)ids.add(future.get(20,TimeUnit.SECONDS));assertEquals(1,ids.size());
                SupportTicket saved=tickets.findByPublicId(ids.iterator().next()).orElseThrow();assertEquals("MEDIUM",saved.getPriority());
                assertEquals(1,entries.findByTicketIdOrderByIdAsc(saved.getId(),org.springframework.data.domain.PageRequest.of(0,24)).getTotalElements());
                assertEquals(1,notifications.countByUserAndIsReadFalse(owner));verify(audit,times(1)).logAction(anyString(),anyString(),eq("HELPDESK_CREATED"),anyString());
                assertTrue(counters.findById("204005").orElseThrow().getLastNumber()>=1);
            }finally{go.countDown();pool.shutdownNow();}
        }
    }

    @Test void auditFailureRollsBackTicketThreadAndInboxButDoesNotReuseNumber() {
        User owner=users.saveAndFlush(User.builder().name("Synthetic rollback requester").email("helpdesk-rollback@example.invalid").password("synthetic-hash").role(Role.STUDENT).enabled(true).build());
        var flags=mock(FeatureFlagService.class);when(flags.isFeatureEnabled("MODULE_HELPDESK")).thenReturn(true);
        var audit=mock(AuditService.class);doThrow(new IllegalStateException("Synthetic audit outage")).when(audit).logAction(anyString(),anyString(),anyString(),anyString());
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var service=new SupportHelpdeskService(tickets,entries,users,new DomainAccess(users,events),flags,allocator("2040-06-20T00:00:00Z"),audit,notifications,factory.getValidator(),manager);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(owner,null,owner.getAuthorities()));
            try {
                assertThrows(IllegalStateException.class,()->service.create(new Create("00000000-0000-0000-0000-000000000098","Synthetic rollback","Synthetic private detail","GENERAL","LOW")));
                assertTrue(tickets.findByTicketNumber("CUJ-TKT-204006-0001").isEmpty());assertEquals(0,notifications.countByUserAndIsReadFalse(owner));
                assertEquals("CUJ-TKT-204006-0002",allocator("2040-06-20T00:00:00Z").nextNumber());
            } finally {SecurityContextHolder.clearContext();}
        }
    }
    private HelpdeskTicketNumberService allocator(String instant) {return new HelpdeskTicketNumberService(counters,tickets,manager,Clock.fixed(Instant.parse(instant),ZoneOffset.UTC));}
}

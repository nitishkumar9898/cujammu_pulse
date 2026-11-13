package com.college.events.service;

import com.college.events.dto.LostFoundDtos.*;
import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=create-drop","spring.sql.init.mode=never",
        "logging.file.name=target/test-logs/lostfound-persistence.log"},showSql=false)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class LostFoundPersistenceTest {
    @Autowired LostAndFoundRepository items;
    @Autowired LostFoundClaimRepository claims;
    @Autowired UserRepository users;
    @Autowired EventRepository events;
    @Autowired PlatformTransactionManager manager;
    LostAndFoundService service;
    AuditService audit;
    User finder,first,second;
    TransactionTemplate tx;
    jakarta.validation.ValidatorFactory validation;

    @BeforeEach void setup() {
        validation=Validation.buildDefaultValidatorFactory(); tx=new TransactionTemplate(manager);
        audit=mock(AuditService.class);
        service=new LostAndFoundService(items,claims,mock(FeatureFlagService.class),new DomainAccess(users,events),audit,mock(NotificationService.class),validation.getValidator());
        finder=user("finder"); first=user("first"); second=user("second");
    }
    @AfterEach void cleanup() {SecurityContextHolder.clearContext();validation.close();}
    User user(String name) {return users.saveAndFlush(User.builder().name("Synthetic "+name).email(name+UUID.randomUUID()+"@example.invalid").password("synthetic-hash").role(Role.STUDENT).enabled(true).build());}
    void as(User user) {SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,user.getAuthorities()));}
    Item report() {as(finder);return tx.execute(s->service.report(new Report(UUID.randomUUID().toString(),"Synthetic bag","BAGS_WALLETS","Red bag, proof details private","Synthetic desk")));}
    ClaimView claim(Item item,User actor) {as(actor);return tx.execute(s->service.claim(item.id(),new Claim(UUID.randomUUID().toString(),item.version(),"Synthetic private proof")));}

    @Test void acceptedRequestRetriesReturnOneClaimAndEvidenceIsCallerScoped() {
        var item=report();as(first);var command=new Claim(UUID.randomUUID().toString(),item.version(),"Private synthetic pocket detail");
        var receipt=tx.execute(s->service.claim(item.id(),command));
        assertEquals(receipt.id(),tx.execute(s->service.claim(item.id(),command)).id());
        assertEquals(1,claims.countByItemId(item.id()));
        as(second);assertTrue(tx.execute(s->service.claims(item.id(),0)).isEmpty());
        as(finder);assertEquals(1,tx.execute(s->service.claims(item.id(),0)).size());
        assertThrows(IllegalStateException.class,()->{as(first);tx.execute(s->service.claim(item.id(),new Claim(command.requestKey(),item.version(),"Changed evidence")));});
    }

    @Test void twoSimultaneousVerifiedHandoversCannotTransferOneItemTwice() throws Exception {
        var item=report();var a=claim(item,first);var b=claim(item,second);
        var pool=Executors.newFixedThreadPool(2);var ready=new CountDownLatch(2);var go=new CountDownLatch(1);
        try {
            var futures=new ArrayList<Future<Boolean>>();
            for(var c:List.of(a,b)) futures.add(pool.submit(()-> {
                as(finder);ready.countDown();go.await(10,TimeUnit.SECONDS);
                try {tx.execute(s->service.handover(item.id(),new Handover(UUID.randomUUID().toString(),c.id(),item.version(),"Synthetic proof checked in person")));return true;}
                catch(IllegalStateException conflict) {return false;}
                finally {SecurityContextHolder.clearContext();}
            }));
            assertTrue(ready.await(10,TimeUnit.SECONDS));go.countDown();
            int accepted=0;for(var f:futures) if(f.get(20,TimeUnit.SECONDS)) accepted++;
            assertEquals(1,accepted);
            var saved=items.findById(item.id()).orElseThrow();assertEquals("HANDED_OVER",saved.getStatus());assertNotNull(saved.getAcceptedClaimId());
            assertEquals(1,claims.findByItemIdAndStatusOrderByIdAsc(item.id(),"HANDED_OVER").size());
            assertEquals(1,claims.findByItemIdAndStatusOrderByIdAsc(item.id(),"NOT_SELECTED").size());
        } finally {go.countDown();pool.shutdownNow();}
    }

    @Test void auditFailureRollsBackHandoverAndClaimStatus() {
        var item=report();var c=claim(item,first);as(finder);
        doThrow(new IllegalStateException("Synthetic audit outage")).when(audit).logAction(anyString(),anyString(),eq("LOST_FOUND_HANDOVER_VERIFIED"),anyString());
        assertThrows(IllegalStateException.class,()->tx.execute(s->service.handover(item.id(),new Handover(UUID.randomUUID().toString(),c.id(),item.version(),"Synthetic proof verified"))));
        assertEquals("REPORTED",items.findById(item.id()).orElseThrow().getStatus());
        assertEquals("PENDING",claims.findById(c.id()).orElseThrow().getStatus());
    }

    @Test void finderRetryAfterLostResponsePreservesOriginalVerifiedHandover() {
        var item=report();var c=claim(item,first);as(finder);
        var command=new Handover(UUID.randomUUID().toString(),c.id(),item.version(),"Synthetic proof verified");
        var accepted=tx.execute(s->service.handover(item.id(),command));
        var retry=tx.execute(s->service.handover(item.id(),command));assertEquals(accepted.version(),retry.version());
        verify(audit,times(1)).logAction(anyString(),anyString(),eq("LOST_FOUND_HANDOVER_VERIFIED"),anyString());
    }
}

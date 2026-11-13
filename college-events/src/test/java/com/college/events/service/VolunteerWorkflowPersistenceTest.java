package com.college.events.service;
import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import com.college.events.dto.VolunteerDtos.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;import java.util.*;import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;

@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=create-drop","spring.sql.init.mode=never","logging.file.name=target/test-logs/volunteer-workflow.log"},showSql=false)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class VolunteerWorkflowPersistenceTest {
 @Autowired VolunteerTaskRepository tasks;@Autowired UserRepository users;@Autowired EventRepository events;
 @Autowired EventOperationsRepository operations;@Autowired VolunteerMembershipRepository memberships;@Autowired VolunteerCommandReceiptRepository receipts;@Autowired PlatformTransactionManager manager;
 VolunteerService service;TransactionTemplate tx;User owner,student,stranger;Event event;AuditService audit;
 @BeforeEach void setup(){tx=new TransactionTemplate(manager);tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);audit=mock(AuditService.class);
 service=new VolunteerService(tasks,users,events,operations,memberships,receipts,new DomainAccess(users,events),mock(FeatureFlagService.class),audit,mock(NotificationService.class),new ObjectMapper().findAndRegisterModules());
 owner=user(Role.ORGANIZER);student=user(Role.STUDENT);stranger=user(Role.STUDENT);
 event=events.saveAndFlush(Event.builder().title("Synthetic volunteer event").description("Synthetic").organizer(owner).venue("Demo hall").maxSeats(10).eventDate(LocalDateTime.now().minusHours(3)).approvedAt(LocalDateTime.now().minusDays(1)).status(EventStatus.APPROVED).build());
 operations.saveAndFlush(EventOperations.builder().event(event).build());member(student,"ACCEPTED");
 }
 User user(Role role){return users.saveAndFlush(User.builder().name("Synthetic volunteer").email(UUID.randomUUID()+"@example.invalid").password("synthetic-hash").verifiedEnrollmentNo("000"+UUID.randomUUID()).role(role).enabled(true).build());}
 void member(User u,String status){memberships.saveAndFlush(VolunteerMembership.builder().event(event).user(u).status(status).verifiedIdentifier(u.getVerifiedEnrollmentNo()).build());}
 void as(User u){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u,null,u.getAuthorities()));}
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 String key(){return UUID.randomUUID().toString();}
 TaskView create(){as(owner);return tx.execute(s->service.createTask(event.getId(),new CreateTask(key(),"Registration desk","Support arrivals","GENERAL")));}
 TaskView assigned(){TaskView t=create();as(owner);return tx.execute(s->service.assignVolunteer(t.id(),new Assign(key(),t.revision(),student.getId())));}
 TaskView pending(Instant start,Instant end){TaskView t=assigned();tx.executeWithoutResult(s->{var row=tasks.findForUpdate(t.id()).orElseThrow();row.setAssignedAt(start.minusSeconds(1));tasks.saveAndFlush(row);});as(student);long rev=tasks.findById(t.id()).orElseThrow().getRevision();return tx.execute(s->service.completeTask(t.id(),new Complete(key(),rev,0.5,start,end,"Synthetic supervisor-verifiable shift evidence")));}
 @Test void acceptedRosterRequiredAndPrivateViewsDoNotExposeUserEntity(){TaskView t=create();member(stranger,"APPLIED");as(owner);assertThrows(AccessDeniedException.class,()->tx.execute(s->service.assignVolunteer(t.id(),new Assign(key(),t.revision(),stranger.getId()))));assertEquals(1,tx.execute(s->service.eligible(event.getId(),0)).size());as(stranger);assertThrows(AccessDeniedException.class,()->tx.execute(s->service.getTasksForEvent(event.getId(),0)));assertTrue(tx.execute(s->service.getTasksForUser(stranger.getId(),0)).isEmpty());}
 @Test void assignmentRetryStableChangedPayloadAndStaleRevisionDenied(){TaskView t=create();Assign c=new Assign(key(),t.revision(),student.getId());as(owner);TaskView a=tx.execute(s->service.assignVolunteer(t.id(),c));assertEquals(a,tx.execute(s->service.assignVolunteer(t.id(),c)));member(stranger,"ACCEPTED");assertThrows(IllegalStateException.class,()->tx.execute(s->service.assignVolunteer(t.id(),new Assign(c.requestKey(),t.revision(),stranger.getId()))));assertThrows(IllegalStateException.class,()->tx.execute(s->service.assignVolunteer(t.id(),new Assign(key(),t.revision(),student.getId()))));}
 @Test void reviewedHoursAreSeparateFromClaimAndDoNotMintCertificate(){Instant end=Instant.now().minusSeconds(5),start=end.minusSeconds(3600);TaskView p=pending(start,end);assertEquals(0,p.hoursLogged());assertEquals(0.5,p.requestedHours());as(owner);Review c=new Review(key(),p.revision(),"Supervisor checked the shift record");TaskView a=tx.execute(s->service.approveCompletion(p.id(),c));assertEquals("COMPLETED",a.status());assertEquals(0.5,a.hoursLogged());assertFalse(a.certificateAwarded());assertEquals(a,tx.execute(s->service.approveCompletion(p.id(),c)));}
 @Test void rosterRemovalRevokesSubmissionAndApproval(){TaskView t=assigned();tx.executeWithoutResult(s->{var m=memberships.findByEventIdAndUserId(event.getId(),student.getId()).orElseThrow();m.setStatus("REMOVED");memberships.saveAndFlush(m);});as(student);assertThrows(AccessDeniedException.class,()->tx.execute(s->service.completeTask(t.id(),new Complete(key(),t.revision(),0.5,Instant.now().minusSeconds(3600),Instant.now(),"Synthetic evidence"))));as(owner);assertFalse(tx.execute(s->service.getTasksForEvent(event.getId(),0)).getFirst().canApprove());}
 @Test void futureOrExcessHoursAreRejected(){TaskView t=assigned();as(student);assertThrows(IllegalArgumentException.class,()->tx.execute(s->service.completeTask(t.id(),new Complete(key(),t.revision(),1,Instant.now(),Instant.now().plusSeconds(3600),"Synthetic"))));assertThrows(IllegalArgumentException.class,()->tx.execute(s->service.completeTask(t.id(),new Complete(key(),t.revision(),2,Instant.now().minusSeconds(3600),Instant.now().minusSeconds(1),"Synthetic"))));}
 @Test void overlapCannotReceiveDoubleCredit(){Instant end=Instant.now().minusSeconds(5),start=end.minusSeconds(3600);TaskView one=pending(start,end),two=pending(start,end);as(owner);tx.execute(s->service.approveCompletion(one.id(),new Review(key(),one.revision(),"Verified first shift")));assertThrows(IllegalStateException.class,()->tx.execute(s->service.approveCompletion(two.id(),new Review(key(),two.revision(),"Verify overlapping shift"))));assertEquals(0,tasks.findById(two.id()).orElseThrow().getHoursLogged());}
 @Test void concurrentOverlappingApprovalsAcrossEventsCreditOnlyOneShift()throws Exception{
  Instant end=Instant.now().minusSeconds(5),start=end.minusSeconds(3600);TaskView first=pending(start,end);
  Event secondEvent=events.saveAndFlush(Event.builder().title("Second synthetic event").description("Synthetic").organizer(owner).venue("Demo hall 2").maxSeats(10).eventDate(LocalDateTime.now().minusHours(3)).approvedAt(LocalDateTime.now().minusDays(1)).status(EventStatus.APPROVED).build());
  operations.saveAndFlush(EventOperations.builder().event(secondEvent).build());
  memberships.saveAndFlush(VolunteerMembership.builder().event(secondEvent).user(student).status("ACCEPTED").verifiedIdentifier(student.getVerifiedEnrollmentNo()).build());
  as(owner);TaskView draft=tx.execute(s->service.createTask(secondEvent.getId(),new CreateTask(key(),"Second shift",null,"GENERAL")));
  TaskView assignedSecond=tx.execute(s->service.assignVolunteer(draft.id(),new Assign(key(),draft.revision(),student.getId())));
  tx.executeWithoutResult(s->{var row=tasks.findForUpdate(assignedSecond.id()).orElseThrow();row.setAssignedAt(start.minusSeconds(1));tasks.saveAndFlush(row);});
  as(student);long revision=tasks.findById(assignedSecond.id()).orElseThrow().getRevision();
  TaskView second=tx.execute(s->service.completeTask(assignedSecond.id(),new Complete(key(),revision,0.5,start,end,"Synthetic second-event evidence")));
  CyclicBarrier gate=new CyclicBarrier(2);ExecutorService pool=Executors.newFixedThreadPool(2);
  try{
   record ApprovalAttempt(long taskId,boolean succeeded,String failure){}
   List<Future<ApprovalAttempt>> results=new ArrayList<>();
   for(TaskView candidate:List.of(first,second))results.add(pool.submit(()->{as(owner);try{gate.await();tx.execute(s->service.approveCompletion(candidate.id(),new Review(key(),candidate.revision(),"Independently reviewed overlapping shift")));return new ApprovalAttempt(candidate.id(),true,null);}catch(RuntimeException failure){return new ApprovalAttempt(candidate.id(),false,failure.getMessage());}finally{clear();}}));
   List<ApprovalAttempt> attempts=new ArrayList<>();for(Future<ApprovalAttempt> result:results)attempts.add(result.get(15,TimeUnit.SECONDS));
   List<ApprovalAttempt> credited=attempts.stream().filter(ApprovalAttempt::succeeded).toList();
   List<ApprovalAttempt> denied=attempts.stream().filter(attempt->!attempt.succeeded()).toList();
   assertEquals(1,credited.size());assertEquals(1,denied.size());assertTrue(denied.getFirst().failure().contains("overlaps previously credited volunteer work"));
   var winner=tasks.findById(credited.getFirst().taskId()).orElseThrow();var loser=tasks.findById(denied.getFirst().taskId()).orElseThrow();
   assertEquals("COMPLETED",winner.getStatus());assertEquals(0.5,winner.getHoursLogged());
   assertEquals("COMPLETION_REQUESTED",loser.getStatus());assertEquals(0.0,loser.getHoursLogged());
  }finally{pool.shutdownNow();}
 }
 @Test void cancelledEventRejectsNewAssignmentsWithoutDeletingEvidence(){TaskView t=create();tx.executeWithoutResult(s->{var e=events.findById(event.getId()).orElseThrow();e.setStatus(EventStatus.CANCELLED);events.saveAndFlush(e);});as(owner);assertThrows(IllegalStateException.class,()->tx.execute(s->service.assignVolunteer(t.id(),new Assign(key(),t.revision(),student.getId()))));assertTrue(tasks.existsById(t.id()));}
 @Test void failedAuditRollsBackTaskAndReceipt(){as(owner);long before=tasks.count(),prior=receipts.count();doThrow(new IllegalStateException("Synthetic audit failure")).when(audit).logAction(anyString(),anyString(),anyString(),anyString());assertThrows(IllegalStateException.class,()->tx.execute(s->service.createTask(event.getId(),new CreateTask(key(),"Rollback task",null,"GENERAL"))));assertEquals(before,tasks.count());assertEquals(prior,receipts.count());}
 @Test void concurrentAssignmentHasOneWinner()throws Exception{TaskView t=create();member(stranger,"ACCEPTED");ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);try{var results=new ArrayList<Future<Boolean>>();for(User candidate:List.of(student,stranger))results.add(pool.submit(()->{as(owner);start.await();try{tx.execute(s->service.assignVolunteer(t.id(),new Assign(key(),t.revision(),candidate.getId())));return true;}catch(IllegalStateException ex){return false;}finally{clear();}}));start.countDown();int wins=0;for(var result:results)if(result.get(10,TimeUnit.SECONDS))wins++;assertEquals(1,wins);}finally{pool.shutdownNow();}}
}

package com.college.events.service;

import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import com.college.events.dto.VolunteerDtos.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.data.domain.*;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Event-operation lock orders roster/lifecycle changes before task and cross-event credit locks. */
@Service @RequiredArgsConstructor
@Transactional(isolation=Isolation.READ_COMMITTED)
public class VolunteerService {
 private final VolunteerTaskRepository tasks;
 private final UserRepository users;
 private final EventRepository events;
 private final EventOperationsRepository operations;
 private final VolunteerMembershipRepository memberships;
 private final VolunteerCommandReceiptRepository receipts;
 private final DomainAccess access;
 private final FeatureFlagService featureFlags;
 private final AuditService audit;
 private final NotificationService notifications;
 private final ObjectMapper json;
 @org.springframework.beans.factory.annotation.Value("${app.timezone:Asia/Kolkata}") private String campusTimezone="Asia/Kolkata";

 public TaskView createTask(long eventId,CreateTask command) {
  Event event=lockEvent(eventId); User actor=access.currentUser(); manager(event); active(event);
  String key=key(command.requestKey()), hash=hash("CREATE",eventId,command);
  TaskView replay=replay(eventId,actor,key,hash); if(replay!=null)return replay;
  VolunteerTask task=tasks.saveAndFlush(VolunteerTask.builder().eventId(eventId).title(required(command.title(),120))
    .description(optional(command.description(),2000)).category(command.category()==null?"GENERAL":required(command.category(),50).toUpperCase(Locale.ROOT)).build());
  return finish(event,task,actor,key,hash,"VOLUNTEER_TASK_CREATED");
 }
 public TaskView assignVolunteer(long taskId,Assign command) {
  Event event=lockTaskEvent(taskId); manager(event); active(event); User actor=access.currentUser();
  String key=key(command.requestKey()), hash=hash("ASSIGN",taskId,command);
  User candidate=accepted(event.getId(),command.userId());
  TaskView replay=replay(event.getId(),actor,key,hash); if(replay!=null)return replay;
  VolunteerTask task=task(taskId); revision(task,command.expectedRevision());
  if(!"PENDING".equals(task.getStatus())||task.getAssignedUser()!=null)throw new IllegalStateException("Task is already assigned");
  task.setAssignedUser(candidate);task.setAssignedUserName(candidate.getName());task.setAssignedAt(Instant.now());task.setStatus("ASSIGNED");
  return finish(event,task,actor,key,hash,"VOLUNTEER_TASK_ASSIGNED");
 }
 public TaskView completeTask(long taskId,Complete command) {
  Event event=lockTaskEvent(taskId); reviewable(event); User actor=access.currentUser(); VolunteerTask task=task(taskId);
  if(task.getAssignedUser()==null||!task.getAssignedUser().getId().equals(actor.getId()))throw DomainAccess.denied();
  accepted(event.getId(),actor.getId()); String key=key(command.requestKey()), hash=hash("COMPLETE",taskId,command);
  TaskView replay=replay(event.getId(),actor,key,hash); if(replay!=null)return replay;
  revision(task,command.expectedRevision());
  if(!"ASSIGNED".equals(task.getStatus()))throw new IllegalStateException("Task is not awaiting a completion submission");
  double hours=command.hoursLogged(); Instant start=command.shiftStart(),end=command.shiftEnd();
  if(!Double.isFinite(hours)||hours<0.25||hours>24||start==null||end==null||!end.isAfter(start)||end.isAfter(Instant.now())
    ||Duration.between(start,end).compareTo(Duration.ofHours(24))>0||hours>Duration.between(start,end).toMillis()/3600000.0)
   throw new IllegalArgumentException("Provide a past shift of at most 24 hours; requested hours cannot exceed its duration");
  if(task.getAssignedAt()==null||start.isBefore(task.getAssignedAt()))throw new IllegalArgumentException("Shift must follow a recorded assignment; legacy assignments require reconciliation");
  if(event.getCompletedAt()!=null&&end.isAfter(event.getCompletedAt().atZone(ZoneId.of(campusTimezone)).toInstant()))throw new IllegalArgumentException("Shift cannot extend beyond event completion");
  task.setRequestedHours(hours);task.setReportedShiftStart(start);task.setReportedShiftEnd(end);task.setCompletionEvidence(required(command.evidence(),1000));
  task.setCompletionRequestedAt(LocalDateTime.now(ZoneOffset.UTC));task.setStatus("COMPLETION_REQUESTED");
  return finish(event,task,actor,key,hash,"VOLUNTEER_COMPLETION_SUBMITTED");
 }
 public TaskView approveCompletion(long taskId,Review command) {
  Event event=lockTaskEvent(taskId);manager(event);reviewable(event);User actor=access.currentUser();VolunteerTask task=task(taskId);
  if(task.getAssignedUser()==null||task.getAssignedUser().getId().equals(actor.getId()))throw DomainAccess.denied();
  // Common person contention prevents two different event managers crediting overlapping shifts.
  tasks.lockVolunteer(task.getAssignedUser().getId()).orElseThrow(DomainAccess::denied);
  accepted(event.getId(),task.getAssignedUser().getId());
  String key=key(command.requestKey()),hash=hash("APPROVE",taskId,command);TaskView replay=replay(event.getId(),actor,key,hash);if(replay!=null)return replay;
  revision(task,command.expectedRevision());
  if(!"COMPLETION_REQUESTED".equals(task.getStatus())||task.getRequestedHours()==null||task.getReportedShiftStart()==null||task.getReportedShiftEnd()==null||task.getCompletionEvidence()==null)
   throw new IllegalStateException("A complete evidence submission is required; legacy hours need reviewed reconciliation");
  if(tasks.countCreditedOverlap(task.getAssignedUser().getId(),taskId,task.getReportedShiftStart(),task.getReportedShiftEnd())>0)
   throw new IllegalStateException("This shift overlaps previously credited volunteer work");
  task.setHoursLogged(task.getRequestedHours());task.setCompletedAt(LocalDateTime.now(ZoneOffset.UTC));task.setStatus("COMPLETED");
  task.setApprovedByUserId(actor.getId());task.setReviewReason(required(command.reason(),500));task.setCertificateAwarded(false);
  return finish(event,task,actor,key,hash,"VOLUNTEER_HOURS_APPROVED");
 }
 @Transactional(readOnly=true) public List<TaskView> getTasksForEvent(long eventId,int page) {
  featureFlags.requireEnabledForRead("MODULE_VOLUNTEERS");Event event=access.requireEventManager(eventId);User actor=access.currentUser();
  return tasks.findByEventId(eventId,page(page)).map(t->view(t,event,actor)).getContent();
 }
 @Transactional(readOnly=true) public List<TaskView> getTasksForUser(long userId,int page) {
  featureFlags.requireEnabledForRead("MODULE_VOLUNTEERS");User actor=access.requireSelf(userId);
  return tasks.findByAssignedUserId(userId,page(page)).map(t->view(t,events.findById(t.getEventId()).orElseThrow(DomainAccess::denied),actor)).getContent();
 }
 @Transactional(readOnly=true) public List<EligibleVolunteer> eligible(long eventId,int page) {
  featureFlags.requireEnabledForRead("MODULE_VOLUNTEERS");Event event=access.requireEventManager(eventId);active(event);
  return memberships.findEligible(eventId,"ACCEPTED",page(page)).stream()
   .filter(m->eligibleMember(m,m.getUser())).map(m->new EligibleVolunteer(m.getUser().getId(),m.getUser().getName(),m.getVerifiedIdentifier())).toList();
 }
 private Event lockEvent(long id){featureFlags.requireEnabledForUpdate("MODULE_VOLUNTEERS");operations.findForUpdate(id).orElseThrow(()->new IllegalStateException("Approved event operations unavailable"));return events.findById(id).orElseThrow(DomainAccess::denied);}
 private Event lockTaskEvent(long id){return lockEvent(tasks.findEventId(id).orElseThrow(DomainAccess::denied));}
 private VolunteerTask task(long id){return tasks.findForUpdate(id).orElseThrow(DomainAccess::denied);}
 private void manager(Event event){access.requireEventManager(event.getId());}
 private void active(Event event){if(event.getStatus()!=EventStatus.APPROVED)throw new IllegalStateException("An approved active event is required");}
 private void reviewable(Event event){if(event.getStatus()!=EventStatus.APPROVED&&(event.getStatus()!=EventStatus.COMPLETED||event.getCompletedAt()==null||event.getCompletedByUserId()==null))throw new IllegalStateException("Volunteer evidence is unavailable for this event state");}
 private User accepted(long eventId,long id){User u=users.findById(id).orElseThrow(DomainAccess::denied);VolunteerMembership m=memberships.findByEventIdAndUserId(eventId,id).orElseThrow(DomainAccess::denied);if(!eligibleMember(m,u))throw DomainAccess.denied();return u;}
 private boolean eligibleMember(VolunteerMembership m,User u){return u.isEnabled()&&u.getRole()==Role.STUDENT&&u.getVerifiedEnrollmentNo()!=null&&!u.getVerifiedEnrollmentNo().isBlank()&&"ACCEPTED".equals(m.getStatus())&&u.getVerifiedEnrollmentNo().equals(m.getVerifiedIdentifier());}
 private boolean member(long eventId,User u){return memberships.findByEventIdAndUserId(eventId,u.getId()).filter(m->eligibleMember(m,u)).isPresent();}
 private void revision(VolunteerTask task,Long revision){if(revision==null||revision<0||revision!=task.getRevision())throw new IllegalStateException("Task changed; refresh before submitting a new request");}
 private Pageable page(int page){if(page<0||page>10000)throw new IllegalArgumentException("Invalid page");return PageRequest.of(page,50,Sort.by("id").descending());}
 private TaskView view(VolunteerTask t,Event e,User actor){boolean manager=actor.getRole()==Role.ADMIN||access.isEventOwner(actor,e);boolean active=e.getStatus()==EventStatus.APPROVED;boolean review=active||(e.getStatus()==EventStatus.COMPLETED&&e.getCompletedAt()!=null&&e.getCompletedByUserId()!=null);boolean assigned=t.getAssignedUser()!=null;boolean eligible=assigned&&member(e.getId(),t.getAssignedUser());
  return new TaskView(t.getId(),t.getEventId(),t.getRevision(),t.getTitle(),t.getDescription(),t.getCategory(),t.getStatus(),t.getHoursLogged(),t.getRequestedHours(),t.isCertificateAwarded(),t.getAssignedUserName(),t.getCompletedAt(),t.getReportedShiftStart(),t.getReportedShiftEnd(),t.getCompletionEvidence(),t.getReviewReason(),manager&&active&&"PENDING".equals(t.getStatus()),review&&eligible&&t.getAssignedUser().getId().equals(actor.getId())&&"ASSIGNED".equals(t.getStatus()),manager&&review&&eligible&&!t.getAssignedUser().getId().equals(actor.getId())&&"COMPLETION_REQUESTED".equals(t.getStatus()));
 }
 private TaskView finish(Event event,VolunteerTask task,User actor,String key,String hash,String action){tasks.saveAndFlush(task);audit.logAction(actor.getUsername(),actor.getRole().name(),action,"eventId="+event.getId()+";taskId="+task.getId()+";revision="+task.getRevision());
  Set<Long> notified=new HashSet<>(); for(User recipient:Arrays.asList(task.getAssignedUser(),event.getOrganizer()))if(recipient!=null&&notified.add(recipient.getId())&&!recipient.getId().equals(actor.getId()))notifications.createNotification(recipient,"A volunteer task has an update. Sign in to review your authorized task details.","/app/");
  TaskView response=view(task,event,actor);try{receipts.save(VolunteerCommandReceipt.builder().eventId(event.getId()).actorId(actor.getId()).requestKey(key).fingerprint(hash).responseJson(json.writeValueAsString(response)).build());}catch(Exception ex){throw new IllegalStateException("Unable to record volunteer command receipt",ex);}return response;
 }
 private TaskView replay(long eventId,User actor,String key,String hash){var receipt=receipts.findByEventIdAndActorIdAndRequestKey(eventId,actor.getId(),key);if(receipt.isEmpty())return null;if(!receipt.get().getFingerprint().equals(hash))throw new IllegalStateException("Request key was already used for different task details");try{return json.readValue(receipt.get().getResponseJson(),TaskView.class);}catch(Exception ex){throw new IllegalStateException("Stored task receipt unavailable",ex);}}
 private String key(String key){try{String normalized=UUID.fromString(key).toString();if(!normalized.equals(key))throw new IllegalArgumentException();return normalized;}catch(Exception ex){throw new IllegalArgumentException("A canonical UUID request key is required");}}
 private String hash(String action,long id,Object command){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(List.of(action,id,command)).getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException("Cannot fingerprint command",ex);}}
 private static String required(String text,int max){if(text==null||text.isBlank()||text.trim().length()>max)throw new IllegalArgumentException("Text is required and must fit the field limit");return text.trim();}
 private static String optional(String text,int max){return text==null||text.isBlank()?null:required(text,max);}
}

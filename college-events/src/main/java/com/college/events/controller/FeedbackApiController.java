package com.college.events.controller;
import com.college.events.dto.FeedbackDtos; import com.college.events.model.Feedback; import com.college.events.model.Event; import com.college.events.repository.EventRepository; import com.college.events.security.DomainAccess; import com.college.events.service.FeedbackService; import jakarta.validation.Valid; import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/cujammu/insights") @RequiredArgsConstructor
public class FeedbackApiController { private final FeedbackService feedback; private final EventRepository events; private final DomainAccess access;
 @PostMapping("/events/{eventId}/feedback") public FeedbackDtos.View submit(@PathVariable long eventId,@Valid @RequestBody FeedbackDtos.Submit c){return view(feedback.submitFeedback(access.currentUser(),eventId,c.rating(),c.comment()));}
 @GetMapping("/events/mine") public com.college.events.dto.CalendarDtos.Page registeredEvents(@RequestParam(defaultValue="0") int page){return feedback.registeredEvents(page);}
 @GetMapping("/events/{eventId}/feedback") public FeedbackDtos.Summary summary(@PathVariable long eventId){return feedback.summary(eventId);}
 @GetMapping("/events/{eventId}/feedback/mine") public org.springframework.http.ResponseEntity<FeedbackDtos.View> own(@PathVariable long eventId){return feedback.own(eventId).map(org.springframework.http.ResponseEntity::ok).orElseGet(()->org.springframework.http.ResponseEntity.noContent().build());}
 private FeedbackDtos.View view(Feedback f){return new FeedbackDtos.View(f.getId(),f.getEvent().getId(),f.getRating(),f.getComment(),f.getCreatedAt());}
}

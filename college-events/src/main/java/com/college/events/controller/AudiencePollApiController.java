package com.college.events.controller;

import com.college.events.dto.PollDtos;
import com.college.events.service.AudiencePollService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cujammu")
@RequiredArgsConstructor
public class AudiencePollApiController {
    private final AudiencePollService polls;

    @GetMapping("/polls/events")
    public ResponseEntity<PollDtos.Events> events(@RequestParam(defaultValue = "0") int page) {
        return privateResponse(polls.eligibleEvents(page));
    }
    @GetMapping("/events/{eventId}/polls")
    public ResponseEntity<PollDtos.Polls> list(@PathVariable Long eventId, @RequestParam(defaultValue = "0") int page) {
        return privateResponse(polls.pollsForEvent(eventId, page));
    }
    @PostMapping("/events/{eventId}/polls")
    public ResponseEntity<PollDtos.PollView> create(@PathVariable Long eventId, @Valid @RequestBody PollDtos.Create command) {
        return privateResponse(polls.createPoll(eventId, command));
    }
    @PostMapping("/polls/{id}/publish")
    public ResponseEntity<PollDtos.PollView> publish(@PathVariable Long id, @Valid @RequestBody PollDtos.Revise command) {
        return privateResponse(polls.publish(id, command));
    }
    @PostMapping("/polls/{id}/close")
    public ResponseEntity<PollDtos.PollView> close(@PathVariable Long id, @Valid @RequestBody PollDtos.Revise command) {
        return privateResponse(polls.close(id, command));
    }
    @PostMapping("/polls/{id}/vote")
    public ResponseEntity<PollDtos.PollView> vote(@PathVariable Long id, @Valid @RequestBody PollDtos.Vote command) {
        return privateResponse(polls.castVote(id, command));
    }
    @GetMapping("/polls/{id}/results")
    public ResponseEntity<PollDtos.PollView> results(@PathVariable Long id) {
        return privateResponse(polls.getPollResults(id));
    }
    // Legacy route is an authorized bounded DTO adapter, never serialized entities.
    @GetMapping("/events/{eventId}/polls/active")
    public ResponseEntity<java.util.List<PollDtos.PollView>> active(@PathVariable Long eventId) {
        return privateResponse(polls.pollsForEvent(eventId, 0).items().stream().filter(p -> "OPEN".equals(p.state())).toList());
    }
    private <T> ResponseEntity<T> privateResponse(T value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);
    }
}

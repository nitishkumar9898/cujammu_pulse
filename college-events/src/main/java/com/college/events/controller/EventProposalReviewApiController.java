package com.college.events.controller;

import com.college.events.dto.EventProposalReviewDtos.*;
import com.college.events.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/event-proposals")
@RequiredArgsConstructor
public class EventProposalReviewApiController {
    private final EventService events;
    @GetMapping public ResponseEntity<Queue> queue(@RequestParam(defaultValue="0") int page) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(events.proposalReviewQueue(page));
    }
    @GetMapping("/publication-queue") public ResponseEntity<Queue> publicationQueue(@RequestParam(defaultValue="0") int page) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(events.proposalPublicationQueue(page));
    }
    @GetMapping("/reviewers") public ResponseEntity<Reviewers> reviewers(@RequestParam(defaultValue="0") int page) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(events.proposalReviewers(page));
    }
    @PutMapping("/{id}/reviewer") public ResponseEntity<Proposal> assign(@PathVariable long id, @Valid @RequestBody Assign command) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(events.assignReviewer(id, command.reviewerId(), command.expectedRevision()));
    }
    @PostMapping("/{id}/approve") public ResponseEntity<Void> approve(@PathVariable long id, @Valid @RequestBody Approve command) {
        events.approveEvent(id, command.expectedRevision());
        return ResponseEntity.noContent().header("Cache-Control", "no-store").build();
    }
    @PostMapping("/{id}/publish") public ResponseEntity<Void> publish(@PathVariable long id, @Valid @RequestBody Approve command) {
        events.publishApprovedEvent(id, command.expectedRevision());
        return ResponseEntity.noContent().header("Cache-Control", "no-store").build();
    }
    @PostMapping("/{id}/reject") public ResponseEntity<Void> reject(@PathVariable long id, @Valid @RequestBody Reject command) {
        events.rejectEvent(id, command.reason(), command.expectedRevision());
        return ResponseEntity.noContent().header("Cache-Control", "no-store").build();
    }
}

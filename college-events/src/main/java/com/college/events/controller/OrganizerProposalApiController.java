package com.college.events.controller;

import com.college.events.dto.EventProposalReviewDtos.OwnQueue;
import com.college.events.dto.EventProposalReviewDtos.EditableProposal;
import com.college.events.dto.EventProposalReviewDtos.ProposalCategory;
import com.college.events.dto.EventProposalReviewDtos.Submit;
import com.college.events.dto.EventProposalReviewDtos.SubmitReceipt;
import com.college.events.dto.EventProposalReviewDtos.Update;
import com.college.events.model.User;
import com.college.events.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/organizer/event-proposals")
@RequiredArgsConstructor
public class OrganizerProposalApiController {
    private final EventService events;

    @GetMapping
    public ResponseEntity<OwnQueue> mine(@RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(events.ownProposalQueue(page));
    }

    @GetMapping("/categories")
    public ResponseEntity<java.util.List<ProposalCategory>> categories() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(events.proposalCategories());
    }

    @PostMapping
    public ResponseEntity<SubmitReceipt> submit(@Valid @RequestBody Submit command) {
        SubmitReceipt receipt = events.submitProposal(command);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(receipt);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EditableProposal> editable(@PathVariable long id, @AuthenticationPrincipal User user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(events.ownEditableProposal(id, user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EditableProposal> update(@PathVariable long id, @Valid @RequestBody Update command,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(events.updateOwnProposal(id, command, user));
    }
}

package com.college.events.controller;

import com.college.events.model.User;
import com.college.events.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/** Opaque, no-store proposal media routes; stored object keys never enter client DTOs or URLs. */
@RestController
@RequiredArgsConstructor
public class EventProposalMediaController {
    private final EventService events;
    private final MediaDeliveryController media;

    @GetMapping("/api/v1/organizer/event-proposals/{id}/media/{kind}")
    public ResponseEntity<byte[]> ownProposalMedia(@PathVariable long id, @PathVariable String kind,
            @AuthenticationPrincipal User user) throws IOException {
        return media.deliverEventObjectKey(events.ownProposalMediaKey(id, kind, user));
    }

    @GetMapping("/api/v1/admin/event-proposals/{id}/media/{kind}")
    public ResponseEntity<byte[]> administratorProposalMedia(@PathVariable long id, @PathVariable String kind)
            throws IOException {
        return media.deliverEventObjectKey(events.administratorProposalMediaKey(id, kind));
    }
}

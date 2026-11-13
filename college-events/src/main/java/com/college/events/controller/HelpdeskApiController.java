package com.college.events.controller;

import com.college.events.dto.HelpdeskDtos.*;
import com.college.events.service.SupportHelpdeskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cujammu/helpdesk")
@RequiredArgsConstructor
public class HelpdeskApiController {
    private final SupportHelpdeskService helpdesk;
    @PostMapping("/tickets") public Ticket create(@Valid @RequestBody Create command){return helpdesk.create(command);}
    @PostMapping("/tickets/link-legacy") public Ticket linkLegacy(@Valid @RequestBody LegacyLink command){return helpdesk.linkLegacy(command);}
    @GetMapping("/my-tickets") public PageResult<Ticket> mine(@RequestParam(defaultValue="0") int page){return helpdesk.ownTickets(page);}
    @GetMapping("/assigned") public PageResult<Ticket> assigned(@RequestParam(defaultValue="0") int page){return helpdesk.assignedTickets(page);}
    @GetMapping("/all") public PageResult<Ticket> all(@RequestParam(defaultValue="0") int page){return helpdesk.allTickets(page);}
    @GetMapping("/tickets/{id}") public Ticket ticket(@PathVariable String id){return helpdesk.ticket(id);}
    @GetMapping("/tickets/{id}/thread") public PageResult<Entry> thread(@PathVariable String id,@RequestParam(defaultValue="0") int page){return helpdesk.thread(id,page);}
    @PostMapping("/tickets/{id}/replies") public Ticket reply(@PathVariable String id,@Valid @RequestBody Reply command){return helpdesk.reply(id,command);}
    @PutMapping("/tickets/{id}/triage") public Ticket triage(@PathVariable String id,@Valid @RequestBody Update command){return helpdesk.update(id,command);}
    @PostMapping("/tickets/{id}/reopen") public Ticket reopen(@PathVariable String id,@Valid @RequestBody OwnerAction command){return helpdesk.reopen(id,command);}
    @PostMapping("/tickets/{id}/escalate") public Ticket escalate(@PathVariable String id,@Valid @RequestBody OwnerAction command){return helpdesk.escalate(id,command);}
}

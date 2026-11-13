package com.college.events.controller;

import com.college.events.model.Role;
import com.college.events.security.DomainAccess;
import com.college.events.service.SupportHelpdeskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class HelpdeskController {
    private final SupportHelpdeskService helpdesk;
    private final DomainAccess access;
    @GetMapping("/helpdesk") public String index(Model model){
        helpdesk.ownTickets(0); // service verifies account and runtime feature before rendering.
        Role role=access.currentUser().getRole();model.addAttribute("canAdmin",role==Role.ADMIN);
        model.addAttribute("canSupport",role==Role.ADMIN||role==Role.STAFF);return "helpdesk/index";
    }
}

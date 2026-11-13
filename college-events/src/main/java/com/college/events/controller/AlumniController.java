package com.college.events.controller;

import com.college.events.model.Role;
import com.college.events.security.DomainAccess;
import com.college.events.service.AlumniNetworkService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class AlumniController {
    private final AlumniNetworkService alumni;
    private final DomainAccess access;

    @GetMapping("/alumni")
    public String index(Model model) {
        model.addAttribute("profile", alumni.ownProfile());
        model.addAttribute("canVerify", access.currentUser().getRole() == Role.ADMIN);
        return "alumni/index";
    }
}

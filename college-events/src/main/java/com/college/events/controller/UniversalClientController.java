package com.college.events.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class UniversalClientController {
    @GetMapping({"/app", "/app/"})
    public String app() { return "forward:/app/index.html"; }

    @GetMapping("/app/sign-in")
    public String signIn(HttpSession session) {
        // Fixed local destination only; no request parameter can become an open redirect.
        session.setAttribute("universalClientSignIn", Boolean.TRUE);
        return "redirect:/login";
    }
}

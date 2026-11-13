package com.college.events.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpServletResponse;

@Controller
@RequestMapping("/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    @GetMapping("/verify/{token}")
    public String verifyTicket(@PathVariable String token, Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        model.addAttribute("valid", false);
        model.addAttribute("message", "Legacy QR passes are disabled. Open your authenticated campus dashboard for current event access.");
        return "attendance/verify";
    }
}

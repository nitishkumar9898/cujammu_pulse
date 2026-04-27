package com.college.events.controller;

import com.college.events.model.Registration;
import com.college.events.service.QRService;
import com.college.events.service.RegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Optional;

@Controller
@RequestMapping("/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final RegistrationService registrationService;
    private final QRService qrService;

    @GetMapping("/verify/{token}")
    public String verifyTicket(@PathVariable String token, Model model) {
        Optional<Registration> regOpt = registrationService.findByQrToken(token);
        if (regOpt.isEmpty()) {
            model.addAttribute("valid", false);
            model.addAttribute("message", "Invalid ticket token.");
            return "attendance/verify";
        }

        Registration reg = regOpt.get();
        String qrDataUri = qrService.getQrImageDataUri(token);

        model.addAttribute("valid", true);
        model.addAttribute("registration", reg);
        model.addAttribute("qrDataUri", qrDataUri);
        return "attendance/verify";
    }
}

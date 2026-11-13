package com.college.events.controller;

import com.college.events.service.ChatbotService;
import com.college.events.service.FeatureFlagService;
import com.college.events.dto.AssistantDtos;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cujammu/assistant")
@RequiredArgsConstructor
public class ChatbotController {

    private final ChatbotService chatbotService;
    private final FeatureFlagService featureFlags;

    @PostMapping("/chat")
    public ResponseEntity<AssistantDtos.ChatReply> chat(@Valid @RequestBody AssistantDtos.ChatCommand request) {
        featureFlags.requireEnabledForUpdate("MODULE_AI_CHATBOT");
        String reply = chatbotService.processMessage(request.message());
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(new AssistantDtos.ChatReply(reply, "SAFE_GROUNDED_ASSISTANT"));
    }
}

package com.college.events.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AssistantDtos {
    private AssistantDtos() { }

    public record ChatCommand(@NotBlank @Size(max = 2000) String message) { }

    public record ChatReply(@Size(max = 4000) String reply, String safety) { }
}

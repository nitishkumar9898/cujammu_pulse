package com.college.events.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** Private chat projections. Entity graphs and media references never cross the API boundary. */
public final class CommunityChatDtos {
    private CommunityChatDtos() { }
    public record Group(long id, String name, String type, boolean nightSilenceEnabled,
                        boolean canPost, boolean nightSilenceActive) { }
    public record Groups(List<Group> items) { }
    public record GroupPage(List<Group> items, int page, boolean hasMore) { }
    public record Message(long id, String senderName, String content, LocalDateTime createdAt) { }
    public record Messages(List<Message> items) { }
    public record Post(@NotBlank @Size(max = 2000) String content,
                       @NotBlank @jakarta.validation.constraints.Pattern(regexp="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String requestKey) { }
}

package com.college.events.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public final class FeedDtos {
    private FeedDtos() { }
    public record Create(@NotBlank @Size(max = 4000) String content,
                         @Size(max = 500) String mediaUrl,
                         @Size(max = 30) String mediaType,
                         @Size(max = 50) String category,
                         Long eventId,
                         @Size(max = 200) String eventTitle,
                         @Size(max = 160) String winnerName,
                         @Size(max = 160) String winningPrize,
                         boolean winnerAnnouncement) { }
    public record PostView(long id, String content, String mediaType, String category,
                           boolean winnerAnnouncement, Long relatedEventId, String relatedEventTitle,
                           String winnerName, String winningPrize, int likesCount, int commentsCount,
                           boolean pinned, LocalDateTime createdAt) { }
    public record LikeView(boolean liked, int likesCount) { }
    public record CommentView(long id, String content, LocalDateTime createdAt) { }
}

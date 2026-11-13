package com.college.events.dto;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;
public final class FeedbackDtos { private FeedbackDtos() {}
 public record Submit(@Min(1) @Max(5) int rating,@Size(max=2000) String comment) {}
 public record View(long id,long eventId,int rating,String comment,LocalDateTime createdAt) {}
 public record Summary(long eventId,double averageRating,long totalResponses,List<View> recent) {}
}

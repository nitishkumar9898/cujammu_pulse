package com.college.events.service;

import com.college.events.model.Event;
import com.college.events.model.Feedback;
import com.college.events.model.User;
import com.college.events.repository.EventRepository;
import com.college.events.repository.FeedbackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final EventRepository eventRepository;

    @Transactional
    public Feedback submitFeedback(User user, Long eventId, int rating, String comment) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new RuntimeException("Event not found"));

        if (feedbackRepository.existsByUserAndEvent(user, event)) {
            throw new RuntimeException("You have already submitted feedback for this event");
        }

        Feedback feedback = Feedback.builder()
            .event(event)
            .user(user)
            .rating(rating)
            .comment(comment)
            .build();

        Feedback saved = feedbackRepository.save(feedback);
        log.info("Feedback submitted by {} for event '{}'", user.getEmail(), event.getTitle());
        return saved;
    }

    public List<Feedback> getFeedbackForEvent(Event event) {
        return feedbackRepository.findByEventOrderByCreatedAtDesc(event);
    }

    public double getAverageRating(Event event) {
        List<Feedback> feedbacks = getFeedbackForEvent(event);
        if (feedbacks.isEmpty()) return 0.0;
        return feedbacks.stream().mapToInt(Feedback::getRating).average().orElse(0.0);
    }

    public boolean hasUserSubmittedFeedback(User user, Event event) {
        return feedbackRepository.existsByUserAndEvent(user, event);
    }
}

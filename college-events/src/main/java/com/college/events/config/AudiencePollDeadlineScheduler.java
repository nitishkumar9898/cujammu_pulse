package com.college.events.config;

import com.college.events.repository.EventPollRepository;
import com.college.events.service.AudiencePollService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Bounded restart-safe poll deadline reconciliation. Each poll commits independently. */
@Component
@RequiredArgsConstructor
public class AudiencePollDeadlineScheduler {
    private static final int BATCH_SIZE = 100;
    private final EventPollRepository polls;
    private final AudiencePollService service;

    @Scheduled(fixedDelayString = "${app.poll.deadline-poll-ms:5000}",
            initialDelayString = "${app.poll.deadline-initial-delay-ms:5000}")
    public void closeExpiredBatch() {
        var due = polls.findExpiredOpenPollIds(Instant.now(), PageRequest.of(0, BATCH_SIZE));
        for (Long pollId : due) service.closeExpiredPoll(pollId);
    }
}

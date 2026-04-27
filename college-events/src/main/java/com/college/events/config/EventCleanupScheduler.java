package com.college.events.config;

import com.college.events.service.EventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventCleanupScheduler {

    private final EventService eventService;

    /**
     * Runs every hour to auto-complete events that have passed their event date.
     * Cron: 0 0 * * * * = every top of hour
     */
    @Scheduled(cron = "0 0 * * * *")
    public void autoCompleteExpiredEvents() {
        log.debug("Running event cleanup scheduler...");
        try {
            eventService.autoCompleteExpiredEvents();
        } catch (Exception e) {
            log.error("Error during event cleanup", e);
        }
    }
}

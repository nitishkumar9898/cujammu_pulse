package com.college.events.config;

import com.college.events.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
@Slf4j
public class MediaCleanupScheduler {

    private final FileStorageService fileStorageService;

    @Value("${app.media.retention-days:30}")
    private long retentionDays;

    @Scheduled(cron = "${app.media.cleanup-cron:0 30 2 * * *}")
    public void deleteExpiredMedia() {
        long configuredDays = retentionDays < 1 ? 30 : Math.min(retentionDays, 30);
        if (retentionDays != configuredDays) {
            log.warn("Configured media retention {} days was clamped to the institutional 30-day maximum", retentionDays);
        }
        long deleted = fileStorageService.deleteMediaOlderThan(Duration.ofDays(configuredDays));
        if (deleted > 0) {
            log.info("Deleted {} uploaded media files older than {} days", deleted, retentionDays);
        }
    }
}

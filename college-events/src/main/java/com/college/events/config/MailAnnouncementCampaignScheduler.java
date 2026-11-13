package com.college.events.config;

import com.college.events.service.MailAnnouncementCampaignService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Restart-safe scheduler; persisted PENDING recipients resume after process restart. */
@Component @RequiredArgsConstructor
public class MailAnnouncementCampaignScheduler {
    private final MailAnnouncementCampaignService campaigns;
    @Scheduled(fixedDelayString="${app.mail-campaign.poll-ms:10000}", initialDelayString="${app.mail-campaign.initial-delay-ms:10000}")
    public void processBatch() {
        campaigns.scrubTerminalCampaigns(java.time.Instant.now());
        campaigns.processNextBatch();
    }
}

package com.college.events.service;

import com.college.events.model.*;
import com.college.events.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Durable bounded snapshot and resumable batches for large event announcement audiences. */
@Service @RequiredArgsConstructor
public class MailAnnouncementCampaignService {
    static final int MAX_CAMPAIGN_AUDIENCE = 20_000;
    static final int SNAPSHOT_PAGE_SIZE = 200;
    static final int DISPATCH_BATCH_SIZE = 100;
    private final MailAnnouncementCampaignRepository campaigns;
    private final MailAnnouncementRecipientRepository recipients;
    private final UserRepository users;
    private final EventRepository events;
    private final FeatureFlagRepository flags;
    private final MailOutboxService outbox;

    /** Called from event approval's transaction; campaign and audience snapshot commit with approval. */
    @Transactional
    public String createSnapshot(Event event, long expectedAudience, Long verifiedDepartmentId) {
        if (event == null || event.getId() == null || event.getStatus() != EventStatus.APPROVED ||
                event.getEventDate() == null || expectedAudience <= EmailService.MAX_SYNCHRONOUS_AUDIENCE ||
                expectedAudience > MAX_CAMPAIGN_AUDIENCE)
            throw new IllegalStateException("Announcement campaign audience is outside the supported bounded range");
        String id=UUID.randomUUID().toString();
        MailAnnouncementCampaign campaign=new MailAnnouncementCampaign();
        campaign.setId(id);campaign.setEventId(event.getId());campaign.setEventDate(event.getEventDate());
        campaign.setEventVersion(event.getLifecycleVersion());campaign.setVerifiedDepartmentId(verifiedDepartmentId);
        campaign.setAudienceSize((int)expectedAudience);campaign.setProcessedCount(0);
        Instant created=Instant.now();
        campaign.setStatus(MailAnnouncementCampaign.Status.QUEUED);campaign.setCreatedAt(created);
        campaign.setExpiresAt(created.plus(7,ChronoUnit.DAYS));
        campaigns.save(campaign);

        long captured=0; long afterId=0;
        while (true) {
            List<Long> ids=verifiedDepartmentId==null
                    ? users.findAnnouncementUserIdsAfter(Role.STUDENT,afterId,PageRequest.of(0,SNAPSHOT_PAGE_SIZE))
                    : users.findAnnouncementUserIdsByDepartmentAfter(Role.STUDENT,verifiedDepartmentId,afterId,
                            PageRequest.of(0,SNAPSHOT_PAGE_SIZE));
            if(ids.isEmpty()) break;
            List<MailAnnouncementRecipient> rows=new ArrayList<>(ids.size());
            for(Long userId:ids) {
                if(userId==null || ++captured>MAX_CAMPAIGN_AUDIENCE)
                    throw new IllegalStateException("Announcement audience changed beyond the campaign safety bound");
                MailAnnouncementRecipient row=new MailAnnouncementRecipient();
                row.setCampaignId(id);row.setRecipientUserId(userId);row.setStatus(MailAnnouncementRecipient.Status.PENDING);rows.add(row);
            }
            recipients.saveAll(rows);afterId=ids.get(ids.size()-1);
            if(ids.size()<SNAPSHOT_PAGE_SIZE) break;
        }
        if(captured!=expectedAudience)
            throw new IllegalStateException("Announcement audience changed during approval; retry after the audience stabilizes");
        return id;
    }

    /** One scheduler invocation commits no more than one recipient batch. */
    @Transactional
    public int processNextBatch() {
        List<String> ids=campaigns.findRunnable(List.of(MailAnnouncementCampaign.Status.QUEUED,
                MailAnnouncementCampaign.Status.PROCESSING),PageRequest.of(0,1));
        if(ids.isEmpty()) return 0;
        String id=ids.get(0);
        MailAnnouncementCampaign campaign=campaigns.lock(id).orElse(null);
        if(campaign==null || campaign.getStatus()==MailAnnouncementCampaign.Status.COMPLETED ||
                campaign.getStatus()==MailAnnouncementCampaign.Status.SUPPRESSED) return 0;
        List<MailAnnouncementRecipient> batch=recipients.lockPending(campaign.getId(),
                MailAnnouncementRecipient.Status.PENDING,PageRequest.of(0,DISPATCH_BATCH_SIZE));
        if(!campaign.getExpiresAt().isAfter(Instant.now())) {
            recipients.deleteAll(batch);
            campaign.setProcessedCount(campaign.getProcessedCount()+batch.size());
            if(recipients.countByCampaignIdAndStatus(campaign.getId(),MailAnnouncementRecipient.Status.PENDING)==0)
                finish(campaign,MailAnnouncementCampaign.Status.SUPPRESSED);
            return batch.size();
        }
        if(!enabled("MODULE_EVENTS")) return 0;
        Event event=events.findForApproval(campaign.getEventId()).orElse(null);
        // Align with event writers and mail claims: module, source event, notification control.
        if(!enabled("MODULE_NOTIFICATIONS")) return 0;
        boolean current=event!=null && event.getStatus()==EventStatus.APPROVED &&
                event.getLifecycleVersion()==campaign.getEventVersion() &&
                campaign.getEventDate().equals(event.getEventDate());
        if(batch.isEmpty()) {finish(campaign,current?MailAnnouncementCampaign.Status.COMPLETED:
                MailAnnouncementCampaign.Status.SUPPRESSED);return 0;}
        for(MailAnnouncementRecipient recipient:batch) {
            User user=users.findById(recipient.getRecipientUserId()).orElse(null);
            boolean eligible=current && user!=null && user.isEnabled() && user.getRole()==Role.STUDENT &&
                    (campaign.getVerifiedDepartmentId()==null || campaign.getVerifiedDepartmentId().equals(user.getVerifiedDepartmentId())) &&
                    user.getEmail()!=null && !user.getEmail().isBlank();
            if(eligible) {
                outbox.enqueueCampaignEventAnnouncement(user.getEmail(),event.getId(),
                        campaign.getEventDate(),campaign.getEventVersion(),campaign.getVerifiedDepartmentId());
                recipient.setStatus(MailAnnouncementRecipient.Status.ENQUEUED);
            } else recipient.setStatus(MailAnnouncementRecipient.Status.SUPPRESSED);
            campaign.setProcessedCount(campaign.getProcessedCount()+1);
        }
        recipients.deleteAll(batch);campaign.setStatus(MailAnnouncementCampaign.Status.PROCESSING);
        if(campaign.getProcessedCount()>=campaign.getAudienceSize())
            finish(campaign,current?MailAnnouncementCampaign.Status.COMPLETED:MailAnnouncementCampaign.Status.SUPPRESSED);
        return batch.size();
    }

    /** Bounded operational metadata cleanup; recipient membership has already been erased per batch. */
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public int scrubTerminalCampaigns(Instant now) {
        Instant cutoff=(now==null?Instant.now():now).minus(30,ChronoUnit.DAYS);
        List<String> ids=campaigns.findTerminalBefore(List.of(MailAnnouncementCampaign.Status.COMPLETED,
                MailAnnouncementCampaign.Status.SUPPRESSED),cutoff,PageRequest.of(0,100));
        int removed=0;
        for(String id:ids) {
            MailAnnouncementCampaign campaign=campaigns.lock(id).orElse(null);
            if(campaign==null || campaign.getTerminalAt()==null || campaign.getTerminalAt().isAfter(cutoff)) continue;
            if(recipients.countByCampaignIdAndStatus(id,MailAnnouncementRecipient.Status.PENDING)==0) {
                campaigns.delete(campaign);removed++;
            }
        }
        return removed;
    }

    private static void finish(MailAnnouncementCampaign campaign,MailAnnouncementCampaign.Status status) {
        campaign.setStatus(status);campaign.setTerminalAt(Instant.now());
    }

    private boolean enabled(String key) {
        return flags.findByFeatureKeyForUpdate(key).map(FeatureFlag::isEnabled).orElse(false);
    }
}

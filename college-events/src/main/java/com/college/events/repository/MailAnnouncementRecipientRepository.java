package com.college.events.repository;

import com.college.events.model.MailAnnouncementRecipient;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface MailAnnouncementRecipientRepository extends JpaRepository<MailAnnouncementRecipient,Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from MailAnnouncementRecipient r where r.campaignId=:campaignId and r.status=:status order by r.recipientUserId")
    List<MailAnnouncementRecipient> lockPending(@Param("campaignId") String campaignId,
            @Param("status") MailAnnouncementRecipient.Status status, Pageable page);
    long countByCampaignIdAndStatus(String campaignId, MailAnnouncementRecipient.Status status);
}

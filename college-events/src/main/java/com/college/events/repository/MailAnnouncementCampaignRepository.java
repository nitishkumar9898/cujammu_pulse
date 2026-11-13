package com.college.events.repository;

import com.college.events.model.MailAnnouncementCampaign;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface MailAnnouncementCampaignRepository extends JpaRepository<MailAnnouncementCampaign,String> {
    @Query("select c.id from MailAnnouncementCampaign c where c.status in :statuses order by c.createdAt, c.id")
    java.util.List<String> findRunnable(@Param("statuses") java.util.Collection<MailAnnouncementCampaign.Status> statuses,
                                        org.springframework.data.domain.Pageable page);
    @Query("select c.id from MailAnnouncementCampaign c where c.status in :statuses and c.terminalAt <= :before order by c.terminalAt, c.id")
    java.util.List<String> findTerminalBefore(@Param("statuses") java.util.Collection<MailAnnouncementCampaign.Status> statuses,
            @Param("before") java.time.Instant before, org.springframework.data.domain.Pageable page);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from MailAnnouncementCampaign c where c.id=:id")
    Optional<MailAnnouncementCampaign> lock(@Param("id") String id);
}

package com.college.events.repository;
import com.college.events.model.VolunteerCommandReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface VolunteerCommandReceiptRepository extends JpaRepository<VolunteerCommandReceipt,Long> {
 Optional<VolunteerCommandReceipt> findByEventIdAndActorIdAndRequestKey(Long eventId,Long actorId,String requestKey);
}

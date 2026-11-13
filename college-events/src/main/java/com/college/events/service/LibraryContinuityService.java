package com.college.events.service;

import com.college.events.dto.HelpdeskDtos;
import com.college.events.repository.LibraryGateLogRepository;
import com.college.events.security.DomainAccess;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** Protected owner recovery closes existing records; it never creates verified attendance. */
@Service @RequiredArgsConstructor
public class LibraryContinuityService {
    private final LibraryGateLogRepository visits;
    private final DomainAccess access;
    private final AuditService audit;
    public record Close(@NotBlank @Pattern(regexp=HelpdeskDtos.UUID_PATTERN) String requestKey,
            @PositiveOrZero long expectedRevision,@NotBlank @Pattern(regexp="SERVICE_DISABLED|SCANNER_UNAVAILABLE|ACCESSIBILITY_SUPPORT") String reasonCode) { }
    public record Active(long visitId,long revision,String purpose,LocalDateTime entryTime) { }
    public record Receipt(long visitId,long revision,String status,LocalDateTime exitTime,String durationQuality) { }
    @Transactional(readOnly=true)
    public List<Active> active() {
        var owner=access.currentUser();
        return visits.findByUserAndStatusOrderByIdDesc(owner,"ACTIVE",org.springframework.data.domain.PageRequest.of(0,20)).stream()
                .map(v->new Active(v.getId(),v.getRevision(),v.getSessionType(),v.getEntryTime())).toList();
    }
    @Transactional
    public Receipt close(long id,Close command) {
        var owner=access.currentUser();
        if(command==null||command.requestKey()==null||!command.requestKey().matches(HelpdeskDtos.UUID_PATTERN)
                ||command.expectedRevision()<0||command.reasonCode()==null||!Set.of("SERVICE_DISABLED","SCANNER_UNAVAILABLE","ACCESSIBILITY_SUPPORT").contains(command.reasonCode()))
            throw new IllegalArgumentException("Invalid continuity command");
        var visit=visits.findOwnedForSafeExit(id,owner.getId()).orElseThrow(DomainAccess::denied);
        String hash;
        try {hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest((command.expectedRevision()+":"+command.reasonCode()).getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
        if("SAFE_EXIT_UNVERIFIED".equals(visit.getStatus())&&command.requestKey().equals(visit.getSafeExitRequestKey())&&hash.equals(visit.getSafeExitPayloadHash()))
            return receipt(visit);
        if(!"ACTIVE".equals(visit.getStatus())||visit.getRevision()!=command.expectedRevision())
            throw new IllegalStateException("Visit changed; refresh before recovery");
        visit.setStatus("SAFE_EXIT_UNVERIFIED"); visit.setExitTime(LocalDateTime.now(ZoneId.of("Asia/Kolkata")).truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        visit.setDurationSeconds(null); visit.setDurationFormatted("Unverified safe exit");
        visit.setSafeExitRequestKey(command.requestKey()); visit.setSafeExitPayloadHash(hash);
        visits.saveAndFlush(visit);
        audit.logAction(owner.getEmail(),owner.getRole().name(),"LIBRARY_SAFE_EXIT_RECORDED","visitId="+id+";quality=UNVERIFIED");
        return receipt(visit);
    }
    private Receipt receipt(com.college.events.model.LibraryGateLog visit) {return new Receipt(visit.getId(),visit.getRevision(),visit.getStatus(),visit.getExitTime(),"UNVERIFIED");}
}

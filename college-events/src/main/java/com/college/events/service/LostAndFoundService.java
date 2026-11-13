package com.college.events.service;

import com.college.events.dto.LostFoundDtos.*;
import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Confidential claims do not reserve ownership. The finder verifies exactly one handover. */
@Service @RequiredArgsConstructor
public class LostAndFoundService {
    private final LostAndFoundRepository items;
    private final LostFoundClaimRepository claims;
    private final FeatureFlagService flags;
    private final DomainAccess access;
    private final AuditService audit;
    private final NotificationService notifications;
    private final Validator validator;

    @Transactional
    public Item report(Report command) {
        validate(command); flags.requireEnabledForUpdate("MODULE_LOST_FOUND"); User actor=access.currentUser();
        String hash=digest(command.title(),command.category(),command.description(),command.locationFound());
        var previous=items.findByRequestKey(command.requestKey());
        if(previous.isPresent()) {
            var i=previous.get(); if(!owned(i,actor)||!hash.equals(i.getPayloadHash())) throw conflict();
            return view(i,actor);
        }
        var item=items.saveAndFlush(LostAndFoundItem.builder().title(command.title().trim()).category(command.category())
                .description(command.description().trim()).locationFound(command.locationFound().trim())
                .reportedBy(actor).status("REPORTED").requestKey(command.requestKey()).payloadHash(hash).build());
        audit(actor,"LOST_FOUND_REPORTED",item.getId()); return view(item,actor);
    }

    @Transactional(readOnly=true)
    public Page list(String category,int page) {
        flags.requireEnabledForRead("MODULE_LOST_FOUND"); User actor=access.currentUser();
        if(page<0||page>10000) throw new IllegalArgumentException("Invalid page");
        if(category!=null&&!category.matches("ELECTRONICS|ID_CARDS_DOCS|BAGS_WALLETS|KEYS|CLOTHING|OTHER"))
            throw new IllegalArgumentException("Invalid category");
        var result=category==null?items.findAllByOrderByIdDesc(PageRequest.of(page,20)):
                items.findByCategoryOrderByIdDesc(category,PageRequest.of(page,20));
        return new Page(result.getContent().stream().map(i->view(i,actor)).toList(),page,result.hasNext());
    }

    @Transactional
    public ClaimView claim(long id,Claim command) {
        validate(command); flags.requireEnabledForUpdate("MODULE_LOST_FOUND"); User actor=access.currentUser();
        var item=items.findForUpdate(id).orElseThrow(DomainAccess::denied);
        if(owned(item,actor)) throw DomainAccess.denied();
        String hash=digest(command.expectedVersion(),command.evidence());
        var previous=claims.findByItemIdAndRequestKey(id,command.requestKey());
        if(previous.isPresent()) {
            var c=previous.get(); if(!Objects.equals(c.getClaimant().getId(),actor.getId())||!hash.equals(c.getPayloadHash())) throw conflict();
            return claimView(c);
        }
        if(item.getVersion()!=command.expectedVersion()||!claimable(item)) throw conflict();
        if(claims.findByItemIdAndClaimantId(id,actor.getId()).isPresent()) throw conflict();
        if(claims.countByItemId(id)>=100) throw new IllegalStateException("Claim review queue is full; contact the finder through the campus helpdesk");
        var claim=claims.saveAndFlush(LostFoundClaim.builder().item(item).claimant(actor).requestKey(command.requestKey())
                .payloadHash(hash).evidence(command.evidence().trim()).status("PENDING").submittedAt(LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).truncatedTo(java.time.temporal.ChronoUnit.MICROS)).build());
        audit(actor,"LOST_FOUND_CLAIM_SUBMITTED",id);
        notifications.createNotification(item.getReportedBy(),"A found-item claim needs your verification.","/app/");
        return claimView(claim);
    }

    @Transactional(readOnly=true)
    public List<ClaimView> claims(long id,int page) {
        flags.requireEnabledForRead("MODULE_LOST_FOUND"); User actor=access.currentUser();
        if(page<0||page>10000) throw new IllegalArgumentException("Invalid page");
        var item=items.findById(id).orElseThrow(DomainAccess::denied);
        var request=PageRequest.of(page,20);
        return (owned(item,actor)?claims.findByItemIdOrderByIdAsc(id,request):
                claims.findByItemIdAndClaimantIdOrderByIdAsc(id,actor.getId(),request)).stream().map(this::claimView).toList();
    }

    @Transactional
    public Item handover(long id,Handover command) {
        validate(command); flags.requireEnabledForUpdate("MODULE_LOST_FOUND"); User actor=access.currentUser();
        var item=items.findForUpdate(id).orElseThrow(DomainAccess::denied);
        if(!owned(item,actor)) throw DomainAccess.denied();
        String hash=digest(command.claimId(),command.expectedVersion(),command.verificationReason());
        var previous=claims.findByItemIdAndHandoverRequestKey(id,command.requestKey());
        if(previous.isPresent()) {
            if(!hash.equals(previous.get().getHandoverPayloadHash())) throw conflict();
            return view(item,actor);
        }
        if(!claimable(item)||item.getVersion()!=command.expectedVersion()) throw conflict();
        var claim=claims.findById(command.claimId()).filter(c->c.getItem().getId().equals(id)&&"PENDING".equals(c.getStatus()))
                .orElseThrow(DomainAccess::denied);
        if(Objects.equals(claim.getClaimant().getId(),actor.getId())||!claim.getClaimant().isEnabled()) throw DomainAccess.denied();
        claim.setStatus("HANDED_OVER"); claim.setHandedOverAt(LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).truncatedTo(java.time.temporal.ChronoUnit.MICROS)); claim.setVerifiedByUserId(actor.getId());
        claim.setVerificationReason(command.verificationReason().trim()); claim.setHandoverRequestKey(command.requestKey());
        claim.setHandoverPayloadHash(hash); item.setStatus("HANDED_OVER"); item.setAcceptedClaimId(claim.getId());
        claims.saveAndFlush(claim); items.saveAndFlush(item);
        for(var other:claims.findByItemIdAndStatusOrderByIdAsc(id,"PENDING")) {
            other.setStatus("NOT_SELECTED"); claims.save(other);
            notifications.createNotification(other.getClaimant(),"A found item was handed over after verification; your claim is closed.","/app/");
        }
        audit(actor,"LOST_FOUND_HANDOVER_VERIFIED",id);
        notifications.createNotification(claim.getClaimant(),"Your found-item handover was verified.","/app/");
        return view(item,actor);
    }

    private boolean owned(LostAndFoundItem item,User user) {return item.getReportedBy()!=null&&Objects.equals(item.getReportedBy().getId(),user.getId());}
    private boolean claimable(LostAndFoundItem item) {return "REPORTED".equals(item.getStatus())||"VERIFIED".equals(item.getStatus());}
    private Item view(LostAndFoundItem i,User actor) {
        String status=("CLAIMED".equals(i.getStatus())||("HANDED_OVER".equals(i.getStatus())&&i.getAcceptedClaimId()==null))?"LEGACY_REVIEW_REQUIRED":i.getStatus();
        return new Item(i.getId(),i.getVersion(),i.getTitle(),i.getCategory(),i.getDescription(),i.getLocationFound(),status,i.getReportedAt(),owned(i,actor)&&claimable(i));
    }
    private ClaimView claimView(LostFoundClaim c) {return new ClaimView(c.getId(),c.getItem().getId(),c.getStatus(),c.getEvidence(),c.getSubmittedAt());}
    private void validate(Object c) {if(c==null||!validator.validate(c).isEmpty()) throw new IllegalArgumentException("Invalid found-item command");}
    private void audit(User actor,String operation,long id) {audit.logAction(actor.getEmail(),actor.getRole().name(),operation,"itemId="+id);}
    private static IllegalStateException conflict() {return new IllegalStateException("Found item changed or request was reused; refresh and retry");}
    private static String digest(Object... values) {
        try {var digest=MessageDigest.getInstance("SHA-256"); for(var value:values) {var bytes=String.valueOf(value).getBytes(StandardCharsets.UTF_8); digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array()); digest.update(bytes);} return java.util.HexFormat.of().formatHex(digest.digest());}
        catch(java.security.NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
    }
}

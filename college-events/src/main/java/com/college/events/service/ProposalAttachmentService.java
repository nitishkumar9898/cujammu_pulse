package com.college.events.service;

import com.college.events.dto.*;
import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import com.college.events.exception.ProposalStateConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service @RequiredArgsConstructor
public class ProposalAttachmentService {
    private final DomainAccess access;
    private final UserRepository users;
    private final EventRepository events;
    private final EventService proposals;
    private final FeatureFlagService flags;
    private final MediaAssetRepository media;
    private final ProposalAttachmentReceiptRepository receipts;

    @Transactional
    public ProposalAttachmentResult replace(long eventId,String kind,long expectedRevision,UUID requestKey,MultipartFile file) {
        flags.requireEnabledForUpdate("MODULE_EVENTS");
        var principal=access.requireRole(Role.FACULTY,Role.ORGANIZER);
        var actor=users.findForUpdate(principal.getId()).filter(User::isEnabled)
            .filter(u->u.getRole()==Role.FACULTY||u.getRole()==Role.ORGANIZER).orElseThrow(DomainAccess::denied);
        var event=events.findForApproval(eventId).filter(e->actor.getId().equals(e.getOrganizer().getId())).orElseThrow(DomainAccess::denied);
        if(!Set.of("banner","certificate-template").contains(kind)||expectedRevision<0||requestKey==null||file==null||file.isEmpty()||file.getSize()>5*1024*1024)
            throw new IllegalArgumentException("A bounded image, revision and request key are required");
        var fingerprint=fingerprint(eventId,kind,expectedRevision,file);
        String hash=fingerprint.payloadHash();
        var prior=receipts.findByOwnerIdAndRequestKey(actor.getId(),requestKey.toString());
        if(prior.isPresent()) {
            var receipt=prior.get();
            if(!receipt.getPayloadHash().equals(hash)) throw new ProposalStateConflictException("Attachment request key already used for another payload");
            return result(receipt,true);
        }
        // Existing edit path owns pure-proposal policy, revision locking, resubmission,
        // reviewer invalidation, atomic audit and transactional upload cleanup.
        var current=proposals.ownEditableProposal(eventId,actor);
        String previousKey=kind.equals("banner")?event.getBannerImage():event.getCertificateTemplatePath();
        String previousHash=previousKey==null?null:media.findByObjectKey(previousKey).map(MediaAsset::getSha256).orElse(null);
        var dto=new EventDto(); dto.setExpectedLifecycleVersion(expectedRevision);
        dto.setTitle(current.title()); dto.setDescription(current.description()); dto.setCategoryId(current.categoryId());
        dto.setVenue(current.venue()); dto.setEventDate(current.eventDate()); dto.setEventEndsAt(current.eventEndsAt());
        dto.setMaxSeats(current.maxSeats()); dto.setFees(current.fees()); dto.setContactEmail(current.contactEmail());
        dto.setTargetDepartment(current.targetDepartment()); dto.setGenerateCertificate(current.generateCertificate());
        if(kind.equals("banner")) dto.setBannerImage(file); else dto.setCertificateTemplate(file);
        var updated=proposals.updateEvent(eventId,dto,actor);
        var asset=media.findByObjectKey(kind.equals("banner")?updated.getBannerImage():updated.getCertificateTemplatePath()).orElseThrow();
        // Compare sanitized image bytes, so changing only upload metadata cannot renew expiry.
        // A conflict rolls back the event/audit/metadata and deletes only the newly staged file.
        if(Objects.equals(previousHash,asset.getSha256())
            ||media.existsByOwnerUserIdAndResourceIdAndResourceTypeAndSha256AndIdNot(actor.getId(),eventId,asset.getResourceType(),asset.getSha256(),asset.getId())
            ||receipts.existsByOwnerIdAndEventIdAndKindAndContentHash(actor.getId(),eventId,kind,asset.getSha256()))
            throw new ProposalStateConflictException("Identical attachment was already accepted; reuse its original request receipt instead of renewing retention");
        var receipt=receipts.saveAndFlush(ProposalAttachmentReceipt.builder().ownerId(actor.getId()).requestKey(requestKey.toString())
            .eventId(eventId).kind(kind).payloadHash(hash).contentHash(asset.getSha256()).resultingRevision(updated.getLifecycleVersion())
            .uploadAcceptedAt(asset.getUploadAcceptedAt()).expiresAt(asset.getExpiresAt()).build());
        return result(receipt,false);
    }
    private static ProposalAttachmentResult result(ProposalAttachmentReceipt r,boolean replayed) {
        return new ProposalAttachmentResult(r.getEventId(),r.getResultingRevision(),r.getKind(),r.getUploadAcceptedAt(),r.getExpiresAt(),replayed);
    }
    private record Fingerprint(String payloadHash,String contentHash) {}
    private static Fingerprint fingerprint(long id,String kind,long revision,MultipartFile file) {
        try(var input=file.getInputStream()) {
            byte[] bytes=input.readNBytes(5*1024*1024+1);
            if(bytes.length==0||bytes.length>5*1024*1024) throw new IllegalArgumentException("Invalid image size");
            var digest=MessageDigest.getInstance("SHA-256");
            String contentHash=HexFormat.of().formatHex(digest.digest(bytes));
            for(String field:List.of(Long.toString(id),kind,Long.toString(revision),Objects.toString(file.getContentType(),""),Objects.toString(file.getOriginalFilename(),""),contentHash)) {
                byte[] value=field.getBytes(StandardCharsets.UTF_8);
                digest.update(java.nio.ByteBuffer.allocate(4).putInt(value.length).array()); digest.update(value);
            }
            return new Fingerprint(HexFormat.of().formatHex(digest.digest()),contentHash);
        } catch(java.io.IOException|java.security.NoSuchAlgorithmException failure) { throw new IllegalArgumentException("Attachment could not be read"); }
    }
}

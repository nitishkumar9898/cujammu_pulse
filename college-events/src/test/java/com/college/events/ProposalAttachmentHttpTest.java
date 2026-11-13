package com.college.events;

import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import java.time.*;
import java.util.UUID;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:proposal-attachment;DB_CLOSE_DELAY=-1","spring.jpa.hibernate.ddl-auto=create-drop","spring.sql.init.mode=never","app.default-admin-password=","app.upload.dir=target/test-uploads/proposal-attachment","app.media.cleanup-cron=-","app.identity.enabled=false","app.email.backend=console","spring.cache.type=none"})
@AutoConfigureMockMvc
class ProposalAttachmentHttpTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired EventRepository events;
    @Autowired MediaAssetRepository media;
    @Autowired AuditLogRepository audit;
    @Autowired FeatureFlagRepository flags;
    @Autowired ProposalAttachmentReceiptRepository receipts;
    @MockitoSpyBean AuditService auditService;
    private User owner,other;
    private Event event;
    @BeforeEach void setup() {
        owner=users.saveAndFlush(User.builder().name("Synthetic owner").email(UUID.randomUUID()+"@example.invalid").password("synthetic").role(Role.ORGANIZER).enabled(true).build());
        other=users.saveAndFlush(User.builder().name("Synthetic other").email(UUID.randomUUID()+"@example.invalid").password("synthetic").role(Role.ORGANIZER).enabled(true).build());
        var category=categories.saveAndFlush(Category.builder().name("Synthetic "+UUID.randomUUID()).build());
        event=events.saveAndFlush(Event.builder().title("Synthetic attachment proposal").description("Synthetic programme with sufficient description.").organizer(owner).category(category).venue("Synthetic room").eventDate(LocalDateTime.now().plusDays(5)).maxSeats(20).fees(0.0).targetDepartment("ALL").build());
    }
    private MockMultipartFile image() throws Exception {
        return image(0);
    }
    private MockMultipartFile image(int colour) throws Exception {
        var output=new ByteArrayOutputStream(); var bitmap=new BufferedImage(4,4,BufferedImage.TYPE_INT_RGB); bitmap.setRGB(0,0,colour); ImageIO.write(bitmap,"png",output);
        return new MockMultipartFile("file","banner.png","image/png",output.toByteArray());
    }
    @AfterEach void cleanup() { reset(auditService); }
    private String path() { return "/api/v1/organizer/event-proposals/"+event.getId()+"/attachments/banner"; }
    @Test void uploadRetryAndReplacementPreserveOriginalExpiryAndAudit() throws Exception {
        String key=UUID.randomUUID().toString(); long before=audit.count();
        mvc.perform(multipart(path()).file(image()).param("requestKey",key).param("expectedRevision","0").with(user(owner)).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("replayed").value(false)).andExpect(jsonPath("expiresAt").exists()).andExpect(jsonPath("objectKey").doesNotExist());
        var updated=events.findById(event.getId()).orElseThrow(); var old=media.findByObjectKey(updated.getBannerImage()).orElseThrow();
        long count=media.count(); var expiry=old.getExpiresAt();
        assertEquals(Duration.ofDays(30),Duration.between(old.getUploadAcceptedAt(),expiry));
        mvc.perform(multipart(path()).file(image()).param("requestKey",key).param("expectedRevision","0").with(user(owner)).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("replayed").value(true));
        assertEquals(count,media.count()); assertEquals(before+1,audit.count());
        mvc.perform(multipart(path()).file(image()).param("requestKey",UUID.randomUUID().toString()).param("expectedRevision",Long.toString(updated.getLifecycleVersion())).with(user(owner)).with(csrf())).andExpect(status().isConflict());
        mvc.perform(multipart(path()).file(image(0xff00ff)).param("requestKey",key).param("expectedRevision","0").with(user(owner)).with(csrf())).andExpect(status().isConflict());
        mvc.perform(multipart(path()).file(image(0xff00ff)).param("requestKey",UUID.randomUUID().toString()).param("expectedRevision",Long.toString(updated.getLifecycleVersion())).with(user(owner)).with(csrf())).andExpect(status().isOk());
        assertEquals(expiry,media.findById(old.getId()).orElseThrow().getExpiresAt());
        assertEquals(count+1,media.count());
    }
    @Test void certificateReplacementInvalidatesAssignmentAndDecision() throws Exception {
        event.setAssignedReviewerId(other.getId()); event.setReviewerAssignedByUserId(other.getId()); event.setReviewerAssignedAt(LocalDateTime.now()); event.setLastReviewerAssignmentRevision(0L);
        event.setStatus(EventStatus.REJECTED); event.setRejectionReason("Revise"); event.setLastProposalDecisionRevision(0L); event=events.saveAndFlush(event);
        mvc.perform(multipart(path().replace("/banner","/certificate-template")).file(image()).param("requestKey",UUID.randomUUID().toString()).param("expectedRevision",Long.toString(event.getLifecycleVersion())).with(user(owner)).with(csrf())).andExpect(status().isOk());
        var changed=events.findById(event.getId()).orElseThrow(); assertEquals(EventStatus.PENDING,changed.getStatus());
        assertNull(changed.getAssignedReviewerId()); assertNull(changed.getLastProposalDecisionRevision()); assertNull(changed.getRejectionReason());
        assertEquals("EVENT_CERT_TEMPLATE",media.findByObjectKey(changed.getCertificateTemplatePath()).orElseThrow().getResourceType());
    }
    @Test void disabledAccountAndModuleDenyBeforeUpload() throws Exception {
        long count=media.count(); owner.setEnabled(false); owner=users.saveAndFlush(owner);
        mvc.perform(multipart(path()).file(image()).param("requestKey",UUID.randomUUID().toString()).param("expectedRevision","0").with(user(owner)).with(csrf())).andExpect(status().isUnauthorized());
        owner.setEnabled(true); owner=users.saveAndFlush(owner);
        var flag=flags.findByFeatureKey("MODULE_EVENTS").orElseThrow(); boolean enabled=flag.isEnabled(); flag.setEnabled(false); flags.saveAndFlush(flag);
        try { mvc.perform(multipart(path()).file(image()).param("requestKey",UUID.randomUUID().toString()).param("expectedRevision","0").with(user(owner)).with(csrf())).andExpect(status().isConflict()); }
        finally { var restored=flags.findByFeatureKey("MODULE_EVENTS").orElseThrow(); restored.setEnabled(enabled); flags.saveAndFlush(restored); }
        assertEquals(count,media.count());
    }
    @Test void auditFailureRollsBackMediaAndProposal() throws Exception {
        long count=media.count(); doThrow(new RuntimeException("synthetic audit failure")).when(auditService).logAction(anyString(),anyString(),eq("EVENT_UPDATED"),anyString());
        mvc.perform(multipart(path()).file(image()).param("requestKey",UUID.randomUUID().toString()).param("expectedRevision","0").with(user(owner)).with(csrf())).andExpect(status().is5xxServerError());
        assertEquals(count,media.count()); assertNull(events.findById(event.getId()).orElseThrow().getBannerImage());
    }
    @Test void simultaneousMatchingRequestsCreateOneAssetAndAudit() throws Exception {
        long count=media.count(),beforeAudit=audit.count(); String key=UUID.randomUUID().toString();
        var barrier=new java.util.concurrent.CyclicBarrier(2); var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.Callable<Integer> upload=()->{
            barrier.await(10,java.util.concurrent.TimeUnit.SECONDS);
            return mvc.perform(multipart(path()).file(image()).param("requestKey",key).param("expectedRevision","0").with(user(owner)).with(csrf())).andReturn().getResponse().getStatus();
        };
        try {
            var first=executor.submit(upload); var second=executor.submit(upload);
            assertEquals(200,first.get(30,java.util.concurrent.TimeUnit.SECONDS)); assertEquals(200,second.get(30,java.util.concurrent.TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); }
        assertEquals(count+1,media.count()); assertEquals(beforeAudit+1,audit.count());
    }
    @Test void existingAttachmentWithoutReceiptCannotRenewIdenticalContent() throws Exception {
        mvc.perform(multipart(path()).file(image()).param("requestKey",UUID.randomUUID().toString()).param("expectedRevision","0").with(user(owner)).with(csrf())).andExpect(status().isOk());
        // Historical/legacy uploads have media bindings but no attachment-command receipt.
        receipts.deleteAll(); var current=events.findById(event.getId()).orElseThrow();
        var original=media.findByObjectKey(current.getBannerImage()).orElseThrow(); long count=media.count();
        mvc.perform(multipart(path()).file(image()).param("requestKey",UUID.randomUUID().toString()).param("expectedRevision",Long.toString(current.getLifecycleVersion())).with(user(owner)).with(csrf())).andExpect(status().isConflict());
        assertEquals(count,media.count()); assertEquals(original.getExpiresAt(),media.findById(original.getId()).orElseThrow().getExpiresAt());
    }
    @Test void historicalNonCurrentLegacyAssetCannotRenewIdenticalContent() throws Exception {
        mvc.perform(multipart(path()).file(image()).param("requestKey",UUID.randomUUID().toString()).param("expectedRevision","0").with(user(owner)).with(csrf())).andExpect(status().isOk());
        receipts.deleteAll(); var originalEvent=events.findById(event.getId()).orElseThrow();
        var original=media.findByObjectKey(originalEvent.getBannerImage()).orElseThrow();
        mvc.perform(multipart(path()).file(image(0xff00ff)).param("requestKey",UUID.randomUUID().toString()).param("expectedRevision",Long.toString(originalEvent.getLifecycleVersion())).with(user(owner)).with(csrf())).andExpect(status().isOk());
        receipts.deleteAll(); var current=events.findById(event.getId()).orElseThrow(); long count=media.count();
        assertNotEquals(original.getObjectKey(),current.getBannerImage());
        mvc.perform(multipart(path()).file(image()).param("requestKey",UUID.randomUUID().toString()).param("expectedRevision",Long.toString(current.getLifecycleVersion())).with(user(owner)).with(csrf())).andExpect(status().isConflict());
        assertEquals(count,media.count()); assertEquals(original.getExpiresAt(),media.findById(original.getId()).orElseThrow().getExpiresAt());
    }
    @Test void rejectsCrossOwnerCsrfAndStaleRevisionWithoutUploads() throws Exception {
        long count=media.count();
        mvc.perform(multipart(path()).file(image()).param("requestKey",UUID.randomUUID().toString()).param("expectedRevision","0").with(user(other)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(multipart(path()).file(image()).param("requestKey",UUID.randomUUID().toString()).param("expectedRevision","0").with(user(owner))).andExpect(status().isForbidden());
        mvc.perform(multipart(path()).file(image()).param("requestKey",UUID.randomUUID().toString()).param("expectedRevision","99").with(user(owner)).with(csrf())).andExpect(status().isConflict());
        assertEquals(count,media.count());
    }
}

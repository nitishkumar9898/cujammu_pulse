package com.college.events.controller;
import com.college.events.dto.ProposalAttachmentResult;
import com.college.events.service.ProposalAttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/organizer/event-proposals")
public class ProposalAttachmentController {
    private final ProposalAttachmentService attachments;
    @PostMapping(value="/{id}/attachments/{kind}",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProposalAttachmentResult> replace(@PathVariable long id,@PathVariable String kind,
        @RequestParam long expectedRevision,@RequestParam UUID requestKey,@RequestParam MultipartFile file) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
            .body(attachments.replace(id,kind,expectedRevision,requestKey,file));
    }
}

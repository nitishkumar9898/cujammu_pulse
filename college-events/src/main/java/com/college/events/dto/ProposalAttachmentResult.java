package com.college.events.dto;
import java.time.Instant;
public record ProposalAttachmentResult(long id,long revision,String kind,Instant uploadAcceptedAt,Instant expiresAt,boolean replayed) {}

package com.college.events.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public final class StudentIdentifierDtos {
    private StudentIdentifierDtos() {}
    public record Lookup(@NotBlank @Email @Size(max = 254) String email) {}
    public record Candidate(long studentId, String name, String email, String verifiedIdentifier,
            Long verifiedDepartmentId, String bindingState, long credentialVersion) {}
    public record Verify(@PositiveOrZero long expectedCredentialVersion,
            @NotBlank @Size(max = 64) String identifier,
            @NotBlank @Size(max = 120) String officialSourceReference,
            @NotBlank @Size(max = 240) String reviewReason,
            @NotNull @Positive Long departmentId) {
        public Verify(long expectedCredentialVersion,String identifier,String officialSourceReference,String reviewReason) {
            this(expectedCredentialVersion,identifier,officialSourceReference,reviewReason,null);
        }
    }
    public record Result(long studentId, String verifiedIdentifier, LocalDateTime verifiedAt, long credentialVersion) {}
}

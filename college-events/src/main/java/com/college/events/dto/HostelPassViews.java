package com.college.events.dto;

import com.college.events.model.HostelGatePass;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDateTime;

public final class HostelPassViews {
    private HostelPassViews() {}
    public record OwnPass(Long id, String hostel, String type, String status, String destination,
                          String reason, LocalDateTime expectedExit, LocalDateTime expectedReturn,
                          LocalDateTime actualExit, LocalDateTime actualReturn) {
        public static OwnPass from(HostelGatePass p) {
            return new OwnPass(p.getId(), p.getHostel().getName(), p.getPassType(), p.getStatus(),
                    p.getDestination(), p.getReason(), p.getExpectedExitTime(), p.getExpectedReturnTime(),
                    p.getActualExitTime(), p.getActualReturnTime());
        }
    }
    public record Apply(Long hostelId, String passType, String destination, String reason,
                        LocalDateTime expectedExitTime, LocalDateTime expectedReturnTime) {}
    public record Decision(Long passId, boolean approve, String rejectionReason) {}
    public record PendingPass(Long id, String hostel, String type, String destination, String reason,
                              LocalDateTime expectedExit, LocalDateTime expectedReturn, String studentName, String enrollmentNo) {
        public static PendingPass from(HostelGatePass pass) {
            return new PendingPass(pass.getId(), pass.getHostel().getName(), pass.getPassType(), pass.getDestination(), pass.getReason(),
                    pass.getExpectedExitTime(), pass.getExpectedReturnTime(), pass.getUser().getName(), pass.getUser().getEnrollmentNo());
        }
    }
    public record Hostel(Long id, String name, String curfewTime) {
        public static Hostel from(com.college.events.model.Hostel value) {
            return new Hostel(value.getId(), value.getName(), value.getCurfewTime());
        }
    }
    public record ScanReceipt(Long id, String status, String displayName, String enrollmentNo,
                              LocalDateTime departedAt, LocalDateTime returnedAt) {
        public static ScanReceipt from(HostelGatePass p) {
            return new ScanReceipt(p.getId(), p.getStatus(), p.getUser().getName(), p.getUser().getEnrollmentNo(),
                    p.getActualExitTime(), p.getActualReturnTime());
        }
    }

    public record CredentialView(Long passId, String action, String credential,
                                 LocalDateTime expiresAt, long credentialVersion) { }

    public record ScanIntentCreated(String intentKey, Long passId, String action,
                                    String challengeCode, LocalDateTime expiresAt, String status) { }

    public record PendingScanIntent(String intentKey, Long passId, String action,
                                    LocalDateTime expiresAt, String status) { }

    public record ConfirmScan(String intentKey, String challengeCode) { }

    public record ScanIntent(@NotBlank String credential,
                             @NotBlank @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String requestKey) { }

    public record OwnerConfirmedReceipt(Long evidenceId, Long passId, String action,
                                        String status, LocalDateTime occurredAt,
                                        CredentialView nextCredential) { }

    public record DeviceRegister(String deviceId, String platform) {}

    public record DeviceRevoke(String deviceId) {}
}

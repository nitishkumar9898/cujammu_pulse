package com.college.events.service;

import com.college.events.model.Registration;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class QRService {

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    /**
     * Generate a QR code with enriched student + event information as JSON payload.
     */
    public String generateEnrichedQR(Registration reg) {
        String studentName = reg.getUser().getName();
        String enrollment = reg.getUser().getEnrollmentNo() != null ? reg.getUser().getEnrollmentNo() : "N/A";
        String department = reg.getUser().getDepartment() != null ? reg.getUser().getDepartment() : "N/A";
        String eventName = reg.getEvent().getTitle();
        String eventDate = reg.getEvent().getEventDate() != null ? reg.getEvent().getEventDate().format(DATE_FMT) : "TBD";
        String venue = reg.getEvent().getVenue();
        String verifyUrl = baseUrl + "/attendance/verify/" + reg.getQrToken();

        String qrContent = "{"
            + "\"student\":\"" + escapeJson(studentName) + "\","
            + "\"enrollment\":\"" + escapeJson(enrollment) + "\","
            + "\"department\":\"" + escapeJson(department) + "\","
            + "\"event\":\"" + escapeJson(eventName) + "\","
            + "\"date\":\"" + escapeJson(eventDate) + "\","
            + "\"venue\":\"" + escapeJson(venue) + "\","
            + "\"token\":\"" + escapeJson(reg.getQrToken()) + "\","
            + "\"verificationUrl\":\"" + verifyUrl + "\","
            + "\"verify\":\"" + verifyUrl + "\""
            + "}";

        return encodeToBase64(qrContent);
    }

    /** Simple URL-based QR (backward compat) */
    public String generateQRCodeBase64(String token) {
        String qrContent = baseUrl + "/attendance/verify/" + token;
        return encodeToBase64(qrContent);
    }

    public String getQrImageDataUri(String token) {
        return "data:image/png;base64," + generateQRCodeBase64(token);
    }

    public byte[] getQrImageBytes(String token) {
        String qrContent = baseUrl + "/attendance/verify/" + token;
        return encodeToBytes(qrContent);
    }

    public String getEnrichedQrDataUri(Registration reg) {
        return "data:image/png;base64," + generateEnrichedQR(reg);
    }

    public byte[] getEnrichedQrBytes(Registration reg) {
        String studentName = reg.getUser().getName();
        String enrollment = reg.getUser().getEnrollmentNo() != null ? reg.getUser().getEnrollmentNo() : "N/A";
        String department = reg.getUser().getDepartment() != null ? reg.getUser().getDepartment() : "N/A";
        String eventName = reg.getEvent().getTitle();
        String eventDate = reg.getEvent().getEventDate() != null ? reg.getEvent().getEventDate().format(DATE_FMT) : "TBD";
        String venue = reg.getEvent().getVenue();
        String verifyUrl = baseUrl + "/attendance/verify/" + reg.getQrToken();

        String qrContent = "{"
            + "\"student\":\"" + escapeJson(studentName) + "\","
            + "\"enrollment\":\"" + escapeJson(enrollment) + "\","
            + "\"department\":\"" + escapeJson(department) + "\","
            + "\"event\":\"" + escapeJson(eventName) + "\","
            + "\"date\":\"" + escapeJson(eventDate) + "\","
            + "\"venue\":\"" + escapeJson(venue) + "\","
            + "\"token\":\"" + escapeJson(reg.getQrToken()) + "\","
            + "\"verificationUrl\":\"" + verifyUrl + "\","
            + "\"verify\":\"" + verifyUrl + "\""
            + "}";
            
        return encodeToBytes(qrContent);
    }

    public String getCertificateVerificationQrDataUri(Registration reg) {
        String verificationUrl = baseUrl + "/attendance/certificate/" + reg.getQrToken();
        return "data:image/png;base64," + encodeToBase64(verificationUrl);
    }

    private String encodeToBase64(String content) {
        byte[] pngBytes = encodeToBytes(content);
        return Base64.getEncoder().encodeToString(pngBytes);
    }

    private byte[] encodeToBytes(String content) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();

            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H); // Maximum robust error correction (30%)
            hints.put(EncodeHintType.MARGIN, 4); // Required 4 module quiet zone
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

            // Generated at ultra-high 500x500 resolution for instant scanning
            BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, 500, 500, hints);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
            return outputStream.toByteArray();
        } catch (WriterException | IOException e) {
            log.error("Failed to generate QR code: {}", e.getMessage(), e);
            throw new RuntimeException("QR code generation failed: " + e.getMessage());
        }
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

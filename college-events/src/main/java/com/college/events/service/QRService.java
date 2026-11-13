package com.college.events.service;

import com.college.events.model.Registration;
import org.springframework.stereotype.Service;

/**
 * Legacy event QR generator disabled until scoped, owner-confirmed credentials exist.
 * Hostel gate passes are implemented separately and do not use this service.
 */
@Service
public class QRService {

    private UnsupportedOperationException disabled() {
        return new UnsupportedOperationException("Legacy event QR credentials are disabled.");
    }

    public String generateEnrichedQR(Registration registration) { throw disabled(); }
    public String generateQRCodeBase64(String ignoredToken) { throw disabled(); }
    public String getQrImageDataUri(String ignoredToken) { throw disabled(); }
    public byte[] getQrImageBytes(String ignoredToken) { throw disabled(); }
    public String getEnrichedQrDataUri(Registration registration) { throw disabled(); }
    public byte[] getEnrichedQrBytes(Registration registration) { throw disabled(); }
    public String getCertificateVerificationQrDataUri(Registration registration) { throw disabled(); }
}

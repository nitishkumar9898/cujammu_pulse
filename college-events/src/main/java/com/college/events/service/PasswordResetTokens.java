package com.college.events.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Random bearer secrets are delivered only to the owner; the database stores SHA-256 digests. */
final class PasswordResetTokens {
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordResetTokens() { }

    static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String digest(String token) {
        // Legacy UUID tokens and stored digest strings are deliberately not valid credentials.
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) return null;
        try {
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Required SHA-256 algorithm unavailable", impossible);
        }
    }
}

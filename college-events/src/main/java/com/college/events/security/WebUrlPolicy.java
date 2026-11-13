package com.college.events.security;

import java.net.URI;

/** Safe external browser destinations; no fetching or ownership is implied. */
public final class WebUrlPolicy {
    private WebUrlPolicy() { }

    public static String optionalHttpUrl(String value) {
        if (value == null || value.isEmpty()) return null;
        if (value.length() > 2048 || value.indexOf('\\') >= 0
                || value.chars().anyMatch(c -> Character.isISOControl(c) || Character.isWhitespace(c))) {
            throw new IllegalArgumentException("Invalid external URL");
        }
        try {
            URI uri = URI.create(value);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getRawUserInfo() != null
                    || uri.getPort() == 0 || uri.getPort() > 65535) throw new IllegalArgumentException("Invalid external URL");
            return value;
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Invalid external URL");
        }
    }

    /** Historical unsafe records are retained, but never exposed as active browser URLs. */
    public static String safeHttpUrl(String value) {
        try { return optionalHttpUrl(value); }
        catch (IllegalArgumentException unsafe) { return null; }
    }
}

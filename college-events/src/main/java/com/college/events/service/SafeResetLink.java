package com.college.events.service;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Builds and validates bearer reset links against an exact HTTPS host allowlist. */
public final class SafeResetLink {
    private SafeResetLink() { }

    public static Optional<String> create(String baseUrl, String token, String configuredHosts) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) return Optional.empty();
        URI base = parseBase(baseUrl, configuredHosts).orElse(null);
        if (base == null) return Optional.empty();
        String authority = base.getRawAuthority();
        if (authority == null) return Optional.empty();
        return Optional.of("https://" + authority + "/reset-password?token=" + token);
    }

    public static boolean isApprovedBaseUrl(String baseUrl, String configuredHosts) {
        return parseBase(baseUrl, configuredHosts).isPresent();
    }

    public static boolean isAllowedResetLink(String resetLink, String configuredHosts) {
        if (resetLink == null) return false;
        try {
            URI uri = URI.create(resetLink);
            Set<String> allowed = allowedHosts(configuredHosts);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && allowed.contains(uri.getHost().toLowerCase(Locale.ROOT))
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && uri.getUserInfo() == null
                    && "/reset-password".equals(uri.getPath())
                    && uri.getFragment() == null
                    && uri.getRawQuery() != null
                    && uri.getRawQuery().matches("token=[A-Za-z0-9_-]{43}");
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static Optional<URI> parseBase(String baseUrl, String configuredHosts) {
        if (baseUrl == null) return Optional.empty();
        try {
            URI uri = URI.create(baseUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)
                    || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))
                    || !allowedHosts(configuredHosts).contains(uri.getHost().toLowerCase(Locale.ROOT))) {
                return Optional.empty();
            }
            return Optional.of(uri);
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    private static Set<String> allowedHosts(String configuredHosts) {
        if (configuredHosts == null || configuredHosts.isBlank()) return Set.of();
        return Arrays.stream(configuredHosts.split(","))
                .map(String::trim)
                .filter(host -> !host.isEmpty())
                .map(host -> host.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }
}

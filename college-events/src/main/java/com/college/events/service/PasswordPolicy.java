package com.college.events.service;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** Shared validation for all local-password write paths; BCrypt accepts at most 72 bytes. */
public final class PasswordPolicy {
    public static final String MESSAGE = "Password must be at least 8 characters, at most 72 UTF-8 bytes, and contain uppercase, lowercase, a digit and one of @$!%*?&.";
    private static final Pattern COMPLEXITY = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).*$");

    private PasswordPolicy() { }

    public static void validate(String password) {
        if (password == null || password.length() < 8 || password.length() > 72
                || password.getBytes(StandardCharsets.UTF_8).length > 72
                || !COMPLEXITY.matcher(password).matches()) {
            throw new IllegalArgumentException(MESSAGE);
        }
    }
}

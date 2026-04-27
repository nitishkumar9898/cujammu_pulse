package com.college.events.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds all custom "app.*" properties so that the IDE
 * recognises them and stops showing "unknown property" warnings.
 */
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String name;
    private String baseUrl;
    private String defaultAdminEmail;
    private String defaultAdminPassword;
    private String uploadDir;
    private EmailProps email = new EmailProps();

    // ── getters / setters ───────────────────────────────────────

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getDefaultAdminEmail() { return defaultAdminEmail; }
    public void setDefaultAdminEmail(String defaultAdminEmail) { this.defaultAdminEmail = defaultAdminEmail; }

    public String getDefaultAdminPassword() { return defaultAdminPassword; }
    public void setDefaultAdminPassword(String defaultAdminPassword) { this.defaultAdminPassword = defaultAdminPassword; }

    public String getUploadDir() { return uploadDir; }
    public void setUploadDir(String uploadDir) { this.uploadDir = uploadDir; }

    public EmailProps getEmail() { return email; }
    public void setEmail(EmailProps email) { this.email = email; }

    public static class EmailProps {
        private String backend;
        public String getBackend() { return backend; }
        public void setBackend(String backend) { this.backend = backend; }
    }
}

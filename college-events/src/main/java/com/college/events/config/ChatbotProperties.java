package com.college.events.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds "chatbot.ai.*" properties to a typed POJO
 * so the IDE recognises them and stops showing warnings.
 */
@Configuration
@ConfigurationProperties(prefix = "chatbot.ai")
public class ChatbotProperties {

    private boolean enabled;
    private String apiKey;
    private String model;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
}

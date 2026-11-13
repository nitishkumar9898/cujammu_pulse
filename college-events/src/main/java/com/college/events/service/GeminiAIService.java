package com.college.events.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Google Gemini API integration for AI-powered chatbot responses.
 * Falls back gracefully if API key is missing or network fails.
 */
@Service
@Slf4j
public class GeminiAIService {

    @Value("${chatbot.ai.api-key:}")
    private String apiKey;

    @Value("${chatbot.ai.model:}")
    private String model;

    @Value("${chatbot.ai.enabled:false}")
    private boolean enabled;

    private final ObjectMapper mapper = new ObjectMapper();
    private volatile HttpClient httpClient;

    private static final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    /**
     * Send a prompt to Gemini and return the text response.
     * Returns null if API is unavailable or fails (caller should use fallback).
     */
    public String generateResponse(String systemPrompt, String userMessage) {
        if (!isAvailable()) {
            log.debug("Gemini AI disabled or no API key configured");
            return null;
        }

        try {
            String url = String.format(GEMINI_URL, model);

            // Build Gemini API request body
            String requestBody = mapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
                put("contents", new Object[]{
                    new java.util.LinkedHashMap<>() {{
                        put("role", "user");
                        put("parts", new Object[]{
                            new java.util.LinkedHashMap<>() {{
                                put("text", systemPrompt + "\n\nUser Question: " + userMessage);
                            }}
                        });
                    }}
                });
                put("generationConfig", new java.util.LinkedHashMap<>() {{
                    put("temperature", 0.7);
                    put("maxOutputTokens", 800);
                    put("topP", 0.9);
                }});
            }});

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

            HttpResponse<byte[]> response = client().send(request,
                    com.college.events.security.BoundedResponseBodyHandler.ofByteArray(64 * 1024));

            if (response.statusCode() != 200) {
                log.warn("Gemini API returned status {}", response.statusCode());
                return null;
            }

            JsonNode root = mapper.readTree(response.body());
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode content = candidates.get(0).path("content").path("parts");
                if (content.isArray() && !content.isEmpty()) {
                    String text = content.get(0).path("text").asText("");
                    if (!text.isBlank()) {
                        log.debug("Gemini response received ({} chars)", text.length());
                        return text.trim();
                    }
                }
            }

            log.warn("Gemini returned empty response");
            return null;

        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.error("Gemini API call failed: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    public boolean isAvailable() {
        return enabled && apiKey != null && !apiKey.isBlank()
                && model != null && model.matches("[A-Za-z0-9._-]{1,100}");
    }

    private synchronized HttpClient client() {
        if (httpClient == null) httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        return httpClient;
    }

    @jakarta.annotation.PreDestroy
    public void close() {
        if (httpClient != null) httpClient.close();
    }
}

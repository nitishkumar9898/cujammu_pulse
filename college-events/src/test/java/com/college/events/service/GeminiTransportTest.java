package com.college.events.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.http.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GeminiTransportTest {
    @Test void disabledProviderNeedsNoNetworkClient() {
        var service = new GeminiAIService();
        assertFalse(service.isAvailable());
        assertNull(service.generateResponse("synthetic context", "hello"));
        assertNull(ReflectionTestUtils.getField(service, "httpClient"));
    }

    @Test @SuppressWarnings("unchecked") void keyTravelsInHeaderNeverInUrl() throws Exception {
        var service = new GeminiAIService();
        var client = mock(HttpClient.class);
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Synthetic answer\"}]}}]}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "model", "synthetic-model");
        ReflectionTestUtils.setField(service, "apiKey", "synthetic-test-key");
        ReflectionTestUtils.setField(service, "httpClient", client);
        assertEquals("Synthetic answer", service.generateResponse("synthetic context", "hello"));
        var request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(request.capture(), any(HttpResponse.BodyHandler.class));
        assertNull(request.getValue().uri().getQuery());
        assertEquals("synthetic-test-key", request.getValue().headers().firstValue("x-goog-api-key").orElseThrow());
    }

    @Test void invalidProviderModelCannotAlterRequestPath() {
        var service = new GeminiAIService();
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "apiKey", "synthetic-test-key");
        ReflectionTestUtils.setField(service, "model", "../other?key=bad");
        assertFalse(service.isAvailable());
        assertNull(service.generateResponse("synthetic", "hello"));
    }
}

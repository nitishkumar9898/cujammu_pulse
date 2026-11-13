package com.college.events.service;

import com.college.events.model.EventStatus;
import com.college.events.repository.EventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatbotQueryBoundsTest {
    @Test void providerContextQueriesAreBoundedBeforeMaterialization() {
        EventRepository events = mock(EventRepository.class);
        when(events.findUpcomingApprovedEvents(any(), eq(PageRequest.of(0, 10)))).thenReturn(List.of());
        when(events.findPastApprovedEvents(any(), eq(PageRequest.of(0, 5)))).thenReturn(List.of());
        GeminiAIService provider = mock(GeminiAIService.class);
        when(provider.isAvailable()).thenReturn(true);
        when(provider.generateResponse(anyString(), anyString())).thenReturn("No approved events");
        assertEquals("No approved events", new ChatbotService(events, provider).processMessage("upcoming events"));
        verify(events).findUpcomingApprovedEvents(any(), eq(PageRequest.of(0, 10)));
        verify(events).findPastApprovedEvents(any(), eq(PageRequest.of(0, 5)));
        verifyNoMoreInteractions(events);
    }
    @Test void statisticsCountInDatabaseWithoutLoadingEventRows() {
        EventRepository events = mock(EventRepository.class);
        GeminiAIService provider = mock(GeminiAIService.class);
        when(events.count()).thenReturn(500L);
        when(events.countByStatus(EventStatus.APPROVED)).thenReturn(300L);
        when(events.countByStatusAndEventDateAfter(eq(EventStatus.APPROVED), any())).thenReturn(212L);
        String reply = new ChatbotService(events, provider).processMessage("how many events");
        assertTrue(reply.contains("212 upcoming events"));
        verify(events).count();
        verify(events).countByStatus(EventStatus.APPROVED);
        verify(events).countByStatusAndEventDateAfter(eq(EventStatus.APPROVED), any());
        verifyNoMoreInteractions(events);
    }
}

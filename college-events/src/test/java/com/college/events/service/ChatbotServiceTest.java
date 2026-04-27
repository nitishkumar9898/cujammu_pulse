package com.college.events.service;

import com.college.events.model.*;
import com.college.events.repository.EventRepository;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

import static org.mockito.Mockito.when;

/**
 * Comprehensive chatbot test suite with 50+ test questions covering:
 * - Greetings (English / Hindi / Hinglish)
 * - Event queries, venue, count, today, free events
 * - Registration, certificates, QR tickets
 * - Category filters (technical, cultural, sports, hackathon, etc.)
 * - About, help, thank you
 * - Edge cases (empty, random, emoji)
 */
@ExtendWith(MockitoExtension.class)
class ChatbotServiceTest {

    @Mock private EventRepository eventRepository;

    private ChatbotService chatbotService;

    @BeforeEach
    void setUp() {
        // Use a simple stub instead of Mockito mock (avoids Java 25 inline mock issues)
        GeminiAIService geminiStub = new GeminiAIService() {
            @Override public boolean isAvailable() { return false; }
            @Override public String generateResponse(String s, String u) { return null; }
        };
        chatbotService = new ChatbotService(eventRepository, geminiStub);
    }

    // ═══════════════════════════════════════════════════════════════════
    // GREETINGS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Greetings — English")
    class GreetingsEnglish {

        @ParameterizedTest
        @CsvSource({"Hello", "Hi", "Hey", "Good morning", "Good evening"})
        void shouldRespondToEnglishGreetings(String greeting) {
            String response = chatbotService.processMessage(greeting);
            assertThat(response).containsIgnoringCase("Hello");
            assertThat(response).contains("CUJAMMU EVENTHUB Assistant");
        }
    }

    @Nested
    @DisplayName("Greetings — Hindi/Hinglish")
    class GreetingsHindi {

        @ParameterizedTest
        @CsvSource({"Namaste", "Namaskar", "Kaise ho"})
        void shouldRespondToHindiGreetings(String greeting) {
            String response = chatbotService.processMessage(greeting);
            assertThat(response).contains("CUJAMMU EVENTHUB Assistant");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELP
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Help Queries")
    class HelpQueries {

        @Test void shouldShowEnglishHelp() {
            String response = chatbotService.processMessage("help");
            assertThat(response).contains("can help you with");
            assertThat(response).contains("Upcoming events");
        }

        @Test void shouldShowHindiHelp() {
            String response = chatbotService.processMessage("madad");
            assertThat(response).contains("madad kar sakta");
        }

        @Test void shouldShowHindiHelp2() {
            String response = chatbotService.processMessage("kya kar sakte ho");
            assertThat(response).contains("madad kar sakta");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // UPCOMING EVENTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Upcoming Events")
    class UpcomingEvents {

        @Test void shouldHandleNoUpcomingEvents() {
            when(eventRepository.findUpcomingApprovedEvents(any())).thenReturn(List.of());
            String response = chatbotService.processMessage("What events are coming up?");
            assertThat(response).contains("No upcoming events");
        }

        @Test void shouldListUpcomingEvents() {
            Event event = mockEvent("AI Workshop", "Seminar Hall A");
            when(eventRepository.findUpcomingApprovedEvents(any())).thenReturn(List.of(event));
            String response = chatbotService.processMessage("upcoming events");
            assertThat(response).contains("AI Workshop");
            assertThat(response).contains("Seminar Hall A");
        }

        @Test void shouldHandleHindiUpcomingQuery() {
            when(eventRepository.findUpcomingApprovedEvents(any())).thenReturn(List.of());
            String response = chatbotService.processMessage("Kaunse events aa rahe hain?");
            assertThat(response).contains("upcoming event nahi hai");
        }

        @Test void shouldHandleHindiUpcomingQuery2() {
            when(eventRepository.findUpcomingApprovedEvents(any())).thenReturn(List.of());
            String response = chatbotService.processMessage("Aane wale event dikha do");
            assertThat(response).containsAnyOf("upcoming event", "Aane Wale Events");
        }

        @Test void shouldHandleHindiUpcomingQuery3() {
            when(eventRepository.findUpcomingApprovedEvents(any())).thenReturn(List.of());
            String response = chatbotService.processMessage("Koi event hai kya?");
            assertThat(response).containsAnyOf("event", "Events");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // EVENT COUNT / STATS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Event Statistics")
    class EventStats {

        @Test void shouldShowEventStats() {
            when(eventRepository.count()).thenReturn(5L);
            when(eventRepository.countByStatus(EventStatus.APPROVED)).thenReturn(3L);
            when(eventRepository.findUpcomingApprovedEvents(any())).thenReturn(List.of());
            String response = chatbotService.processMessage("How many events are there?");
            assertThat(response).contains("Event Statistics");
            assertThat(response).contains("5");
        }

        @Test void shouldShowHindiStats() {
            when(eventRepository.count()).thenReturn(2L);
            when(eventRepository.countByStatus(EventStatus.APPROVED)).thenReturn(1L);
            when(eventRepository.findUpcomingApprovedEvents(any())).thenReturn(List.of());
            String response = chatbotService.processMessage("Kitne event hain?");
            assertThat(response).contains("2");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // REGISTRATION
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Registration Queries")
    class Registration {

        @Test void shouldExplainRegistration() {
            String response = chatbotService.processMessage("How do I register for an event?");
            assertThat(response).contains("Registration Process");
            assertThat(response).contains("Register Now");
        }

        @Test void shouldExplainRegistrationHindi() {
            String response = chatbotService.processMessage("Register kaise karein?");
            assertThat(response).contains("Registration");
        }

        @Test void shouldExplainSignUp() {
            String response = chatbotService.processMessage("How to sign up?");
            assertThat(response).contains("Registration Process");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CERTIFICATES
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Certificate Queries")
    class Certificates {

        @Test void shouldExplainCertificates() {
            String response = chatbotService.processMessage("How do I get my certificate?");
            assertThat(response).contains("Certificates");
        }

        @Test void shouldExplainCertificatesHindi() {
            String response = chatbotService.processMessage("Certificate kaise milega?");
            assertThat(response).contains("Certificates");
        }

        @Test void shouldExplainProofQuery() {
            String response = chatbotService.processMessage("I need proof of attendance");
            assertThat(response).contains("Certificates");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // QR / TICKETS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("QR & Ticket Queries")
    class QRTickets {

        @Test void shouldExplainQRTickets() {
            String response = chatbotService.processMessage("Where is my QR ticket?");
            assertThat(response).contains("QR Tickets");
        }

        @Test void shouldExplainQRHindi() {
            String response = chatbotService.processMessage("Mera ticket kahan hai?");
            assertThat(response).contains("QR Tickets");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // VENUE
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Venue Queries")
    class Venues {

        @Test void shouldShowVenues() {
            Event event = mockEvent("Tech Talk", "Auditorium B");
            when(eventRepository.findUpcomingApprovedEvents(any())).thenReturn(List.of(event));
            String response = chatbotService.processMessage("Where is the event?");
            assertThat(response).contains("Venues");
            assertThat(response).contains("Auditorium B");
        }

        @Test void shouldShowVenuesHindi() {
            when(eventRepository.findUpcomingApprovedEvents(any())).thenReturn(List.of());
            String response = chatbotService.processMessage("Event kahan hai?");
            assertThat(response).containsIgnoringCase("event");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // TODAY'S EVENTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Today's Events")
    class TodayEvents {

        @Test void shouldHandleNoTodayEvents() {
            when(eventRepository.findUpcomingApprovedEvents(any())).thenReturn(List.of());
            String response = chatbotService.processMessage("Any events today?");
            assertThat(response).containsIgnoringCase("today");
        }

        @Test void shouldHandleHindiToday() {
            when(eventRepository.findUpcomingApprovedEvents(any())).thenReturn(List.of());
            String response = chatbotService.processMessage("Aaj koi event hai?");
            assertThat(response).containsAnyOf("today", "Today", "aaj", "Aaj", "nahi hai");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // FREE EVENTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Free Events")
    class FreeEvents {

        @Test void shouldShowFreeEvents() {
            Event freeEvent = mockEvent("Free Talk", "Room 101");
            freeEvent.setFees(0.0);
            when(eventRepository.findUpcomingApprovedEvents(any())).thenReturn(List.of(freeEvent));
            String response = chatbotService.processMessage("Show me free events");
            assertThat(response).contains("Free Events");
        }

        @Test void shouldShowFreeHindi() {
            when(eventRepository.findUpcomingApprovedEvents(any())).thenReturn(List.of());
            String response = chatbotService.processMessage("Muft events dikha do");
            assertThat(response).containsAnyOf("free", "Free", "muft");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CATEGORY QUERIES
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Category Queries")
    class CategoryQueries {

        @ParameterizedTest
        @CsvSource({
            "Show me technical events, Technical",
            "Any cultural events?, Cultural",
            "Sports events?, Sports",
            "Any workshops?, Workshop",
            "Hackathon events, Hackathon",
            "Debate competitions?, Debate",
            "Any quiz competitions?, Quiz"
        })
        void shouldQueryByCategory(String question, String expectedCategory) {
            org.mockito.Mockito.lenient().when(eventRepository.findUpcomingApprovedEvents(any())).thenReturn(List.of());
            String response = chatbotService.processMessage(question);
            assertThat(response).containsIgnoringCase(expectedCategory);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ABOUT
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("About Platform")
    class About {

        @Test void shouldExplainPlatform() {
            String response = chatbotService.processMessage("What is CUJAMMU EVENTHUB?");
            assertThat(response).contains("CUJAMMU EVENTHUB");
        }

        @Test void shouldExplainPlatformHindi() {
            String response = chatbotService.processMessage("Ye kya hai?");
            assertThat(response).contains("CUJAMMU EVENTHUB");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // THANK YOU
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Thank You Responses")
    class ThankYou {

        @Test void shouldRespondToThanks() {
            String response = chatbotService.processMessage("Thank you!");
            assertThat(response).containsIgnoringCase("welcome");
        }

        @Test void shouldRespondToHindiThanks() {
            String response = chatbotService.processMessage("Dhanyavaad!");
            assertThat(response).containsIgnoringCase("welcome");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // EDGE CASES
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test void shouldHandleEmptyMessage() {
            String response = chatbotService.processMessage("");
            assertThat(response).contains("CUJAMMU EVENTHUB Assistant");
        }

        @Test void shouldHandleNullMessage() {
            String response = chatbotService.processMessage(null);
            assertThat(response).contains("CUJAMMU EVENTHUB Assistant");
        }

        @Test void shouldHandleRandomText() {
            String response = chatbotService.processMessage("asdfghjkl xyzzy");
            assertThat(response).containsIgnoringCase("help");
        }

        @Test void shouldHandleEmojiOnly() {
            String response = chatbotService.processMessage("😀🎉🎊");
            assertThat(response).isNotEmpty();
        }

        @Test void shouldHandleVeryLongMessage() {
            String longMsg = "a".repeat(1000);
            String response = chatbotService.processMessage(longMsg);
            assertThat(response).isNotEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // TEST QUESTIONS LIST
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Should have 50+ test questions defined")
    void shouldHave50PlusTestQuestions() {
        List<ChatbotService.TestQuestion> questions = ChatbotService.getTestQuestions();
        assertThat(questions).hasSizeGreaterThanOrEqualTo(50);
    }

    @Test
    @DisplayName("All test categories should be covered")
    void shouldCoverAllCategories() {
        List<ChatbotService.TestQuestion> questions = ChatbotService.getTestQuestions();
        List<String> categories = questions.stream()
            .map(ChatbotService.TestQuestion::category)
            .distinct().toList();
        assertThat(categories).contains("Greeting", "Help", "EventQuery", "Registration",
            "Certificate", "QR", "Venue", "Today", "Free", "Category", "About", "Thanks", "EdgeCase");
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private Event mockEvent(String title, String venue) {
        Event event = new Event();
        event.setTitle(title);
        event.setVenue(venue);
        event.setEventDate(LocalDateTime.now().plusDays(7));
        event.setStatus(EventStatus.APPROVED);
        event.setFees(0.0);
        event.setMaxSeats(100);

        User organizer = new User();
        organizer.setName("Test Organizer");
        organizer.setEmail("org@test.com");
        event.setOrganizer(organizer);

        return event;
    }
}

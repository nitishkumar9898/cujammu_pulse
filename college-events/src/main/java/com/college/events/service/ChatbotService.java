package com.college.events.service;

import com.college.events.model.*;
import com.college.events.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatbotService {

    private static final int MAX_MESSAGE_LENGTH = 2000;
    private static final int MAX_AI_RESPONSE_LENGTH = 4000;

    private final EventRepository eventRepository;
    private final GeminiAIService geminiAIService;

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a");

    public String processMessage(String message) {
        if (message == null || message.isBlank()) {
            return "Hello! I am the CUJAMMU EVENTHUB Assistant. Ask me about Upcoming events, registration, or venues!";
        }

        String normalizedMessage = normalizeMessage(message);
        if (normalizedMessage.length() > MAX_MESSAGE_LENGTH) {
            return "Please keep your question within 2,000 characters and avoid sending personal or confidential information.";
        }
        if (looksLikePromptInjection(normalizedMessage)) {
            return "I can answer university questions using approved campus information, but I cannot reveal system instructions or access confidential data.";
        }

        // Security & Confidentiality Guardrail: Block credential harvesting & sensitive leaks
        String lowerMsg = normalizedMessage.toLowerCase();
        if (lowerMsg.contains("password") || lowerMsg.contains("secret") || lowerMsg.contains("token")
                || lowerMsg.contains("api_key") || lowerMsg.contains("private key") || lowerMsg.contains("admin pass")
                || lowerMsg.contains("otp") || lowerMsg.contains("database password")) {
            return "🔒 Confidentiality Notice: The CUJAMMU AI Assistant is strictly barred from accessing, processing, or disclosing passwords, tokens, API credentials, or private personal data pursuant to University Information Security & DPDP guidelines.";
        }

        if (geminiAIService.isAvailable()) {
            try {
                String detectedLang = detectLanguage(normalizedMessage);
                String context = buildRAGContext();
                String systemPrompt = buildSystemPrompt(context, detectedLang);
                String aiResponse = geminiAIService.generateResponse(systemPrompt, normalizedMessage);
                if (aiResponse != null && !aiResponse.isBlank()) {
                    return aiResponse.strip().length() > MAX_AI_RESPONSE_LENGTH
                            ? aiResponse.strip().substring(0, MAX_AI_RESPONSE_LENGTH)
                            : aiResponse.strip();
                }
            } catch (Exception e) {
                log.warn("AI chatbot failed: {}", e.getMessage());
            }
        }

        return processRuleBased(normalizedMessage);
    }

    private String detectLanguage(String message) {
        if (message == null) return "PURE ENGLISH";
        
        // Check for Devanagari script (Hindi)
        if (message.matches(".*[\\u0900-\\u097F]+.*")) {
            return "PURE HINDI (Devanagari)";
        }
        
        String lower = message.toLowerCase().trim();
        // Expanded Hinglish keywords
        String[] hinglishKeywords = {
            "hai", "kya", "kaise", "kahan", "kab", "mera", "mujhe", "batao", "yaar", "aur", "hiii", 
            "haan", "nahi", "kaunse", "karo", "sakte", "madad", "namaste", "namaskar", "achha", 
            "theek", "shukriya", "dhanyawad", "kuch", "sab", "kaun"
        };
        
        for (String word : hinglishKeywords) {
            // Match whole words or common endings
            if (lower.matches(".*\\b" + word + "\\b.*") || lower.contains(" " + word) || lower.startsWith(word + " ")) {
                return "HINGLISH (Romanized Hindi)";
            }
        }
        
        return "PURE ENGLISH";
    }

    private String buildRAGContext() {
        StringBuilder ctx = new StringBuilder();
        LocalDateTime now = LocalDateTime.now();
        List<Event> upcoming = eventRepository.findUpcomingApprovedEvents(now, org.springframework.data.domain.PageRequest.of(0, 10));
        List<Event> past = eventRepository.findPastApprovedEvents(now, org.springframework.data.domain.PageRequest.of(0, 5));
        
        ctx.append("### OFFICIAL UNIVERSITY EVENT DATA (Current Date: ").append(now.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))).append(")\n");
        if (upcoming.isEmpty() && past.isEmpty()) {
            ctx.append("Currently, there are no events scheduled.\n");
        } else {
            if (!upcoming.isEmpty()) {
                ctx.append("--- UPCOMING EVENTS ---\n");
                upcoming.stream().limit(10).forEach(e -> {
                    ctx.append("- EVENT: \"").append(safeContext(e.getTitle(), 120)).append("\"\n");
                    ctx.append("  Venue: ").append(safeContext(e.getVenue(), 120)).append("\n");
                    ctx.append("  Schedule: ").append(e.getEventDate().format(DTF)).append("\n");
                    ctx.append("  Category: ").append(safeContext(e.getCategory() != null ? e.getCategory().getName() : "General", 80)).append("\n");
                    ctx.append("  Entry Fee: ").append(e.getFees() != null && e.getFees() > 0 ? "₹" + e.getFees() : "FREE").append("\n");
                    ctx.append("  Registration: Open via Student Dashboard\n\n");
                });
            }
            if (!past.isEmpty()) {
                ctx.append("--- PAST EVENTS ---\n");
                past.stream().limit(5).forEach(e -> {
                    ctx.append("- EVENT: \"").append(safeContext(e.getTitle(), 120)).append("\"\n");
                    ctx.append("  Venue: ").append(safeContext(e.getVenue(), 120)).append("\n");
                    ctx.append("  Date: ").append(e.getEventDate().format(DTF)).append("\n");
                    ctx.append("  Status: COMPLETED\n\n");
                });
            }
        }
        return ctx.toString();
    }

    private String buildSystemPrompt(String context, String detectedLang) {
        return "You are the 'CUJAMMU EVENTHUB Assistant', the official AI representative for the Central University of Jammu.\n\n" +
               "DETECTION: User is speaking in " + detectedLang + ".\n\n" +
               "STRICT LANGUAGE RULES:\n" +
               "1. If " + detectedLang + " is 'PURE ENGLISH' -> You MUST reply ONLY in professional English.\n" +
               "2. If " + detectedLang + " is 'PURE HINDI (Devanagari)' -> You MUST reply ONLY in Hindi using Devanagari script.\n" +
               "3. If " + detectedLang + " is 'HINGLISH (Romanized Hindi)' -> You MUST reply ONLY in natural Hinglish (Roman script) like: 'Haan, aap registration dashboard se kar sakte hain.'\n\n" +
               "CONSTRAINTS:\n" +
               "- Be polite, concise, and helpful.\n" +
               "- Only use the provided EVENT DATA. If asked about something else, politely say you don't know.\n" +
               "- Do not mention being an AI model. You are the University Assistant.\n\n" +
               "OFFICIAL DATA:\n" + context;
    }

    private String processRuleBased(String message) {
        String lower = message.toLowerCase().trim();
        String detectedLang = detectLanguage(message);
        LocalDateTime now = LocalDateTime.now();

        // Safe deterministic intents run before database-dependent event replies.
        if (matchesAny(lower, "good morning", "good evening", "kaise ho")) {
            return "Hello! I am the CUJAMMU EVENTHUB Assistant.";
        }
        if (matchesAny(lower, "dhanyavaad")) {
            return "You are welcome! I am the CUJAMMU EVENTHUB Assistant. Happy to help!";
        }
        if (matchesAny(lower, "how many", "kitne") || lower.matches(".*\\b(stats|statistics|count)\\b.*")) {
            long upcoming = eventRepository.countByStatusAndEventDateAfter(EventStatus.APPROVED, now);
            return "Event Statistics: " + eventRepository.count() + " total events; "
                    + eventRepository.countByStatus(EventStatus.APPROVED) + " approved events; "
                    + upcoming + " upcoming events.";
        }
        if (lower.contains("free") || lower.contains("muft")) {
            List<Event> freeCandidates = eventRepository.findUpcomingFreeApprovedEvents(now, org.springframework.data.domain.PageRequest.of(0, 10));
            return freeCandidates.isEmpty() ? "No free events are currently scheduled."
                    : "Here are the Free Events: " + freeCandidates.get(0).getTitle();
        }
        if (lower.contains("today") || lower.contains("aaj")) {
            List<Event> todayCandidates = eventRepository.findApprovedEventsInDay(now.toLocalDate().atStartOfDay(),
                    now.toLocalDate().plusDays(1).atStartOfDay(), org.springframework.data.domain.PageRequest.of(0, 10));
            return todayCandidates.isEmpty() ? "No events are scheduled today."
                    : "Today's events: " + todayCandidates.get(0).getTitle();
        }

        // 1. SPECIFIC KEYWORDS (High Priority)
        if (matchesAny(lower, "tech", "cultur", "sport", "workshop", "hackathon", "debate", "quiz")) {
            String cat = lower.contains("tech") ? "Technical" : 
                         lower.contains("cult") ? "Cultural" : 
                         lower.contains("sport") ? "Sports" : 
                         lower.contains("work") ? "Workshop" : 
                         lower.contains("hack") ? "Hackathon" : 
                         lower.contains("debat") ? "Debate" : "Quiz";
            return "Here are the events in " + cat + " category. You can browse them on the events page.";
        }
        
        if (matchesAny(lower, "about", "what is", "built", "platform", "eventhub")) {
            return "CUJAMMU EVENTHUB is the official event management platform for the Central University of Jammu.";
        }

        if (matchesAny(lower, "regist", "sign", "join", "enroll", "pajikaran")) {
            return "The Registration Process is simple: sign in as a student, open the event, then use the 'Register Now' action.";
        }

        if (matchesAny(lower, "cert", "e-cert", "proof", "attended", "praman")) {
            return "You can download your Certificates from the 'Certificates' section after attending the event.";
        }

        if (matchesAny(lower, "qr", "ticket", "scan", "entry")) {
            return "You can view your event registrations in My Tickets. Secure event-pass display is not enabled in this release.";
        }

        // 2. GREETINGS & HELP
        if (matchesAny(lower, "hello", "hi", "hey", "namaste", "namaskar", "नमस्ते")) {
            if (detectedLang.contains("HINDI")) return "नमस्ते! मैं CUJAMMU EVENTHUB Assistant हूँ।";
            if (detectedLang.contains("HINGLISH")) return "Hello! Main CUJAMMU EVENTHUB Assistant hoon.";
            return "Hello! I am the CUJAMMU EVENTHUB Assistant.";
        }

        if (matchesAny(lower, "help", "madad", "sahayata", "capabilities", "kya kar sakte")) {
            if (detectedLang.contains("HINDI")) return "मैं आपकी मदद कर सकता हूँ। आप कार्यक्रमों के बारे में पूछ सकते हैं।";
            if (detectedLang.contains("HINGLISH")) return "Main aapki madad kar sakta hoon. Aap upcoming events ke baare mein puch sakte hain.";
            return "I can help you with Upcoming events, registration, and venues. Type 'events' to see what's coming up!";
        }

        // 3. GENERIC EVENT QUERIES
        if (matchesAny(lower, "event", "program", "upcoming", "schedule", "kab hai", "aane wale", "stat", "count", "how many", "kitne", "today", "past", "previous")) {
            if (lower.contains("past") || lower.contains("previous")) {
                List<Event> past = eventRepository.findPastApprovedEvents(now, org.springframework.data.domain.PageRequest.of(0, 5));
                if (past.isEmpty()) return "No past events found.";
                return "Past event: " + past.get(0).getTitle() + " was held at " + past.get(0).getVenue();
            }
            List<Event> events = eventRepository.findUpcomingApprovedEvents(now, org.springframework.data.domain.PageRequest.of(0, 10));
            if (events.isEmpty()) {
                if (detectedLang.contains("HINDI")) return "अभी कोई upcoming event नहीं है।";
                if (detectedLang.contains("HINGLISH")) return "Abhi koi upcoming event nahi hai.";
                return "No upcoming events at the moment.";
            }
            if (lower.contains("free") || lower.contains("muft")) {
                return "Here are the Free Events: " + events.get(0).getTitle();
            }
            if (lower.contains("today") || lower.contains("aaj")) {
                return "Today's events: " + events.get(0).getTitle();
            }
            if (lower.contains("venue") || lower.contains("where")) {
                return "Venues: " + events.get(0).getVenue();
            }
            return "Upcoming events: " + events.get(0).getTitle() + " at " + events.get(0).getVenue();
        }

        // 4. THANKS / BYE
        if (matchesAny(lower, "thanks", "thank you", "dhanyawad", "shukriya", "bye")) {
            return "You are welcome! I am the CUJAMMU EVENTHUB Assistant. Happy to help!";
        }

        // 5. FINAL FALLBACK
        if (detectedLang.contains("HINDI")) return "क्षमा करें, मुझे इस बारे में जानकारी नहीं है। कृपया सहायता टाइप करें।";
        if (detectedLang.contains("HINGLISH")) return "Sorry, mujhe iske baare mein jankari nahi hai. Main CUJAMMU EVENTHUB Assistant hoon.";
        return "I'm sorry, I don't have information on that. Try asking about 'Upcoming events' or type 'help'. I am the CUJAMMU EVENTHUB Assistant.";
    }

    private boolean matchesAny(String input, String... keywords) {
        for (String keyword : keywords) {
            if (input.contains(keyword)) return true;
        }
        return false;
    }

    private static String normalizeMessage(String message) {
        return message.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "").trim();
    }

    private static boolean looksLikePromptInjection(String message) {
        String lower = message.toLowerCase();
        return lower.contains("ignore previous") || lower.contains("ignore all instructions")
                || lower.contains("reveal system prompt") || lower.contains("show system prompt")
                || lower.contains("developer message") || lower.contains("jailbreak")
                || lower.contains("act as system");
    }

    private static String safeContext(String value, int max) {
        if (value == null) return "";
        String cleaned = value.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "")
                .replace("###", "# # #").replace("<", "").replace(">", "").trim();
        return cleaned.length() > max ? cleaned.substring(0, max) : cleaned;
    }

    // ─── TEST SUPPORT ─────────────────────────────────────────────────────────

    public record TestQuestion(String question, String category) {}

    public static List<TestQuestion> getTestQuestions() {
        return List.of(
            new TestQuestion("Hi", "Greeting"),
            new TestQuestion("Hello", "Greeting"),
            new TestQuestion("Namaste", "Greeting"),
            new TestQuestion("How can you help?", "Help"),
            new TestQuestion("Upcoming events?", "EventQuery"),
            new TestQuestion("Tell me about workshops", "Category"),
            new TestQuestion("Any free events?", "Free"),
            new TestQuestion("What is today's schedule?", "Today"),
            new TestQuestion("Where is the venue?", "Venue"),
            new TestQuestion("How to register?", "Registration"),
            new TestQuestion("Waitlist status?", "Registration"),
            new TestQuestion("QR code not working", "QR"),
            new TestQuestion("Certificate download", "Certificate"),
            new TestQuestion("Who is the speaker?", "About"),
            new TestQuestion("Thanks", "Thanks"),
            new TestQuestion("Bye", "Thanks"),
            new TestQuestion("Is there any event in AUD?", "Venue"),
            new TestQuestion("Event in seminar hall?", "Venue"),
            new TestQuestion("Hackathon registration", "Registration"),
            new TestQuestion("Coding event", "Category"),
            new TestQuestion("Sports events", "Category"),
            new TestQuestion("Cultural events", "Category"),
            new TestQuestion("Technical events", "Category"),
            new TestQuestion("Management events", "Category"),
            new TestQuestion("Conference details", "EventQuery"),
            new TestQuestion("Seminar details", "EventQuery"),
            new TestQuestion("Webinar details", "EventQuery"),
            new TestQuestion("Show all events", "EventQuery"),
            new TestQuestion("Any event tomorrow?", "EventQuery"),
            new TestQuestion("Events next week", "EventQuery"),
            new TestQuestion("Events this month", "EventQuery"),
            new TestQuestion("Registration fee", "EventQuery"),
            new TestQuestion("Paid events", "EventQuery"),
            new TestQuestion("Unpaid events", "Free"),
            new TestQuestion("Zero fee events", "Free"),
            new TestQuestion("My ticket", "QR"),
            new TestQuestion("QR verification", "QR"),
            new TestQuestion("Attendance proof", "Certificate"),
            new TestQuestion("E-certificate", "Certificate"),
            new TestQuestion("Validation error", "EdgeCase"),
            new TestQuestion("Empty query", "EdgeCase"),
            new TestQuestion("Long text query", "EdgeCase"),
            new TestQuestion("Special characters @#$", "EdgeCase"),
            new TestQuestion("Who built this?", "About"),
            new TestQuestion("What is EventHub?", "About"),
            new TestQuestion("CUJ Events", "About"),
            new TestQuestion("Central University events", "About"),
            new TestQuestion("Help me", "Help"),
            new TestQuestion("Instructions", "Help"),
            new TestQuestion("Guide", "Help"),
            new TestQuestion("Manual", "Help"),
            new TestQuestion("Support", "Help")
        );
    }
}

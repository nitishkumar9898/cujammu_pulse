package com.college.events.service;

import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.security.WebUrlPolicy;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.StringTemplateResolver;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventExternalUrlSecurityTest {
    private final List<String> unsafe = List.of("javascript:alert(1)", "JaVaScRiPt:alert(1)",
            "data:text/html,<script>alert(1)</script>", "//example.invalid/path", "/relative",
            "https://user:pass@example.invalid/", "https://example.invalid\\@evil.invalid/",
            "https://example.invalid/\r\nheader", "https://example.invalid/\tpath", "https://",
            "https://example.invalid:70000/", "https://example.invalid/" + "a".repeat(2048));

    @Test void newSpeakerAndSponsorRejectUnsafeDestinationsBeforeSave() {
        SpeakerRepository speakers = mock(SpeakerRepository.class);
        SponsorRepository sponsors = mock(SponsorRepository.class);
        var speakerService = new SpeakerService(speakers);
        var sponsorService = new SponsorService(sponsors);
        for (String value : unsafe) {
            assertThrows(IllegalArgumentException.class, () -> speakerService.addSpeaker(null, "Synthetic", "Bio", value, null));
            assertThrows(IllegalArgumentException.class, () -> speakerService.addSpeaker(null, "Synthetic", "Bio", null, value));
            assertThrows(IllegalArgumentException.class, () -> sponsorService.addSponsor(null, "Synthetic", null, value, "GOLD"));
            assertThrows(IllegalArgumentException.class, () -> sponsorService.addSponsor(null, "Synthetic", value, null, "GOLD"));
        }
        verifyNoInteractions(speakers, sponsors);
    }

    @Test void safeUrlsAndOptionalFieldsArePreservedByPersistenceCommands() {
        SpeakerRepository speakers = mock(SpeakerRepository.class);
        SponsorRepository sponsors = mock(SponsorRepository.class);
        when(speakers.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sponsors.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var speaker = new SpeakerService(speakers).addSpeaker(null, "Synthetic", "Bio", "https://example.invalid/profile", "");
        var sponsor = new SponsorService(sponsors).addSponsor(null, "Synthetic", null, "http://example.invalid/path?q=a&b=c", "GOLD");
        assertEquals("https://example.invalid/profile", speaker.getLinkedInUrl());
        assertNull(speaker.getProfileImageUrl());
        assertEquals("http://example.invalid/path?q=a&b=c", sponsor.getWebsiteUrl());
        assertNull(sponsor.getLogoUrl());
        String boundary = "https://example.invalid/" + "a".repeat(2048 - "https://example.invalid/".length());
        assertEquals(boundary, WebUrlPolicy.optionalHttpUrl(boundary));
        assertThrows(IllegalArgumentException.class, () -> WebUrlPolicy.optionalHttpUrl(boundary + "a"));
    }

    @Test void historicalUnsafeValuesRemainStoredButSafeModelPropertiesSuppressThem() {
        for (String value : unsafe) {
            var speaker = Speaker.builder().linkedInUrl(value).profileImageUrl(value).build();
            var sponsor = Sponsor.builder().websiteUrl(value).logoUrl(value).build();
            assertNull(speaker.getSafeLinkedInUrl()); assertNull(speaker.getSafeProfileImageUrl());
            assertNull(sponsor.getSafeWebsiteUrl()); assertNull(sponsor.getSafeLogoUrl());
            assertEquals(value, speaker.getLinkedInUrl()); assertEquals(value, sponsor.getWebsiteUrl());
        }
    }

    @Test void actualStudentSpeakerSponsorTemplateSuppressesUnsafeHistoricalLinksAndKeepsSafeLinks() throws Exception {
        String source;
        try (var input = getClass().getResourceAsStream("/templates/student/event-detail.html")) {
            assertNotNull(input); source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        String section = source.substring(source.indexOf("<!-- Speakers Selection -->"), source.indexOf("<!-- Sidebar info -->"));
        var engine = new SpringTemplateEngine(); engine.setTemplateResolver(new StringTemplateResolver());
        var context = new Context();
        context.setVariable("speakers", List.of(
                Speaker.builder().name("Unsafe Speaker").bio("Synthetic").linkedInUrl("javascript:alert(1)").profileImageUrl("data:text/html,bad").build(),
                Speaker.builder().name("Safe Speaker").bio("Synthetic").linkedInUrl("https://example.invalid/profile").build()));
        context.setVariable("sponsors", List.of(
                Sponsor.builder().companyName("Unsafe Sponsor").sponsorshipTier("GOLD").websiteUrl("javascript:alert(1)").logoUrl("data:text/html,bad").build(),
                Sponsor.builder().companyName("Safe Sponsor").sponsorshipTier("GOLD").websiteUrl("https://example.invalid/sponsor").build()));
        String rendered = engine.process(section, context);
        assertFalse(rendered.contains("javascript:")); assertFalse(rendered.contains("data:text/html"));
        assertTrue(rendered.contains("Unsafe Speaker")); assertTrue(rendered.contains("Unsafe Sponsor"));
        assertTrue(rendered.contains("href=\"https://example.invalid/profile\""));
        assertTrue(rendered.contains("href=\"https://example.invalid/sponsor\""));
        assertEquals(2, rendered.split("rel=\"noopener noreferrer\"", -1).length - 1);
    }
}

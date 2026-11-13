package com.college.events.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SafeResetLinkTest {
    private static final String TOKEN="A".repeat(43);

    @Test void buildsOnlyAllowlistedHttpsOriginResetLinks() {
        assertEquals("https://pulse.example.invalid/reset-password?token="+TOKEN,
                SafeResetLink.create("https://pulse.example.invalid/",TOKEN,"pulse.example.invalid").orElseThrow());
        assertTrue(SafeResetLink.isAllowedResetLink("https://pulse.example.invalid/reset-password?token="+TOKEN,
                "pulse.example.invalid"));
    }

    @Test void rejectsInsecureOrUnapprovedResetOrigins() {
        for(String baseUrl:new String[]{"http://pulse.example.invalid","https://attacker.example.invalid",
                "https://user@pulse.example.invalid","https://pulse.example.invalid?next=attacker",
                "https://pulse.example.invalid/#fragment","https://pulse.example.invalid:8443",
                "https://pulse.example.invalid/app"}) {
            assertTrue(SafeResetLink.create(baseUrl,TOKEN,"pulse.example.invalid").isEmpty(),baseUrl);
        }
        assertTrue(SafeResetLink.create("https://pulse.example.invalid",TOKEN,"").isEmpty());
        assertTrue(SafeResetLink.create("https://pulse.example.invalid","legacy-token","pulse.example.invalid").isEmpty());
    }

    @Test void rejectsMutatedOrAmbiguousBearerLinks() {
        String safe="https://pulse.example.invalid/reset-password?token="+TOKEN;
        for(String mutated:new String[]{safe+"&next=https://attacker.example.invalid",safe+"#fragment",
                safe.replace("https://","http://"),safe.replace("pulse.example.invalid","attacker.example.invalid"),
                "https://user@pulse.example.invalid/reset-password?token="+TOKEN}) {
            assertFalse(SafeResetLink.isAllowedResetLink(mutated,"pulse.example.invalid"),mutated);
        }
    }
}

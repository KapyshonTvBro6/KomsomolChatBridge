package me.kprf.komsomolChatBridge.core;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitServiceTest {
    @Test
    void blocksTooManyMessagesInWindow() {
        RateLimitService service = new RateLimitService(true, 2, Duration.ofSeconds(10), Duration.ofSeconds(15));

        assertTrue(service.check(message("1"), false).allowed());
        assertTrue(service.check(message("2"), false).allowed());
        RateLimitService.Result blocked = service.check(message("3"), false);

        assertFalse(blocked.allowed());
        assertEquals("rate_limit", blocked.reason());
    }

    @Test
    void blocksDuplicateMessages() {
        RateLimitService service = new RateLimitService(true, 10, Duration.ofSeconds(10), Duration.ofSeconds(15));

        assertTrue(service.check(message("одинаково"), false).allowed());
        RateLimitService.Result blocked = service.check(message("одинаково"), false);

        assertFalse(blocked.allowed());
        assertEquals("duplicate", blocked.reason());
    }

    @Test
    void bypassSkipsLimits() {
        RateLimitService service = new RateLimitService(true, 1, Duration.ofSeconds(10), Duration.ofSeconds(15));

        assertTrue(service.check(message("1"), true).allowed());
        assertTrue(service.check(message("1"), true).allowed());
    }

    private BridgeMessage message(String text) {
        return BridgeMessage.builder(BridgePlatform.DISCORD)
                .sourceUserId("42")
                .sourceUserName("tester")
                .plainText(text)
                .build();
    }
}

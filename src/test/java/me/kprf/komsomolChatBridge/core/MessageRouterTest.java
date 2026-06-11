package me.kprf.komsomolChatBridge.core;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MessageRouterTest {
    @Test
    void minecraftMessagesGoToDiscordAndTelegram() {
        MessageRouter router = new MessageRouter();
        BridgeMessage message = BridgeMessage.builder(BridgePlatform.MINECRAFT)
                .plainText("Привет")
                .build();

        assertEquals(EnumSet.of(BridgePlatform.DISCORD, BridgePlatform.TELEGRAM), router.targetsFor(message));
    }

    @Test
    void mutedPlatformIsExcluded() {
        MessageRouter router = new MessageRouter();
        router.mute(BridgePlatform.DISCORD);

        BridgeMessage message = BridgeMessage.builder(BridgePlatform.TELEGRAM)
                .plainText("Привет")
                .build();

        assertEquals(EnumSet.of(BridgePlatform.MINECRAFT), router.targetsFor(message));
        assertFalse(router.targetsFor(message).contains(BridgePlatform.DISCORD));
    }

    @Test
    void externalOnlySystemMessagesSkipMinecraft() {
        MessageRouter router = new MessageRouter();
        BridgeMessage message = BridgeMessage.builder(BridgePlatform.SYSTEM)
                .plainText("Сервер запущен")
                .metadata("external_only", "true")
                .build();

        assertEquals(EnumSet.of(BridgePlatform.DISCORD, BridgePlatform.TELEGRAM), router.targetsFor(message));
    }
}

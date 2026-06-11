package me.kprf.komsomolChatBridge.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageFormatterTest {
    @Test
    void formatsMinecraftMessageForDiscord() {
        MessageFormatter formatter = new MessageFormatter();
        BridgeMessage message = BridgeMessage.builder(BridgePlatform.MINECRAFT)
                .minecraftName("Lenin")
                .plainText("Привет")
                .build();

        assertEquals("**Lenin**: Привет", formatter.format(message, BridgePlatform.DISCORD));
    }

    @Test
    void escapesDiscordMarkdownInUserMessage() {
        MessageFormatter formatter = new MessageFormatter();
        BridgeMessage message = BridgeMessage.builder(BridgePlatform.MINECRAFT)
                .minecraftName("Player")
                .plainText("*важно*")
                .build();

        assertEquals("**Player**: \\*важно\\*", formatter.format(message, BridgePlatform.DISCORD));
    }

    @Test
    void escapesMiniMessageTagsForMinecraftTarget() {
        MessageFormatter formatter = new MessageFormatter();
        BridgeMessage message = BridgeMessage.builder(BridgePlatform.DISCORD)
                .sourceUserName("User")
                .plainText("<red>не инжектить")
                .build();

        assertEquals("<gray>[<blue>DS</blue>]</gray> <white>User</white>: <gray>\\<red>не инжектить</gray>",
                formatter.format(message, BridgePlatform.MINECRAFT));
    }
}

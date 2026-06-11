package me.kprf.komsomolChatBridge.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageFormatterTest {
    @Test
    void formatsMinecraftMessageForDiscord() {
        MessageFormatter formatter = new MessageFormatter();
        BridgeMessage message = BridgeMessage.builder(BridgePlatform.MINECRAFT)
                .minecraftName("Lenin")
                .plainText("Hello")
                .build();

        assertEquals("Lenin » Hello", formatter.format(message, BridgePlatform.DISCORD));
    }

    @Test
    void escapesDiscordMarkdownInUserMessage() {
        MessageFormatter formatter = new MessageFormatter();
        BridgeMessage message = BridgeMessage.builder(BridgePlatform.MINECRAFT)
                .minecraftName("Player")
                .plainText("*important*")
                .build();

        assertEquals("Player » \\*important\\*", formatter.format(message, BridgePlatform.DISCORD));
    }

    @Test
    void escapesMiniMessageTagsForMinecraftTarget() {
        MessageFormatter formatter = new MessageFormatter();
        BridgeMessage message = BridgeMessage.builder(BridgePlatform.DISCORD)
                .sourceUserName("User")
                .plainText("<red>no inject")
                .build();

        assertEquals("[<aqua>Discord</aqua>] User » \\<red>no inject",
                formatter.format(message, BridgePlatform.MINECRAFT));
    }

    @Test
    void formatsTelegramHtmlAndEscapesUserText() {
        MessageFormatter formatter = new MessageFormatter();
        BridgeMessage message = BridgeMessage.builder(BridgePlatform.MINECRAFT)
                .minecraftName("Player")
                .plainText("<hello>")
                .build();

        assertEquals("<b>[💬 Player]</b> &lt;hello&gt;", formatter.format(message, BridgePlatform.TELEGRAM));
    }

    @Test
    void usesPlatformSpecificSystemTemplate() {
        MessageFormatter formatter = new MessageFormatter();
        BridgeMessage message = BridgeMessage.builder(BridgePlatform.SYSTEM)
                .plainText("Server started")
                .metadata("system_key", "server_start")
                .metadata("server", "KomsomolCraft")
                .build();

        assertEquals(":white_check_mark: **Server has started**", formatter.format(message, BridgePlatform.DISCORD));
        assertEquals("✅ <b>Сервер KomsomolCraft запущен!</b>", formatter.format(message, BridgePlatform.TELEGRAM));
    }

    @Test
    void metadataPlayerOverridesSystemDisplayName() {
        MessageFormatter formatter = new MessageFormatter();
        BridgeMessage message = BridgeMessage.builder(BridgePlatform.SYSTEM)
                .sourceUserName("Minecraft")
                .plainText("Player joined")
                .metadata("system_key", "join")
                .metadata("player", "KapyshonTvBro6")
                .metadata("server", "KomsomolCraft")
                .build();

        assertEquals("KapyshonTvBro6 вошёл на сервер", formatter.format(message, BridgePlatform.DISCORD));
        assertEquals("🥳 <b>KapyshonTvBro6 зашёл на KomsomolCraft</b>", formatter.format(message, BridgePlatform.TELEGRAM));
    }
}

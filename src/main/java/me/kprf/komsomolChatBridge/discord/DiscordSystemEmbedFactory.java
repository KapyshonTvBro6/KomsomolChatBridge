package me.kprf.komsomolChatBridge.discord;

import me.kprf.komsomolChatBridge.config.BridgeConfig;
import me.kprf.komsomolChatBridge.core.BridgeMessage;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.awt.Color;

final class DiscordSystemEmbedFactory {
    private DiscordSystemEmbedFactory() {
    }

    static MessageEmbed create(BridgeMessage message, BridgeConfig.DiscordSettings settings) {
        String key = message.metadata().get("system_key");
        if (!settings.useEmbedsForPlayerEvents() || !isPlayerEvent(key)) {
            return null;
        }

        String player = message.metadata().getOrDefault("player", message.displayName());
        String avatarUrl = avatarUrl(settings.systemEmbedAvatarUrl(), player);
        EmbedBuilder builder = new EmbedBuilder()
                .setColor(colorFor(key))
                .setAuthor(titleFor(key, message, player), null, avatarUrl);
        return builder.build();
    }

    private static String titleFor(String key, BridgeMessage message, String player) {
        return switch (key) {
            case "join" -> player + " вошёл на сервер";
            case "quit" -> player + " вышел с сервера";
            default -> cleanTitle(message.formattedText());
        };
    }

    private static boolean isPlayerEvent(String key) {
        return "join".equals(key)
                || "quit".equals(key)
                || "death".equals(key)
                || "advancement".equals(key);
    }

    private static Color colorFor(String key) {
        return switch (key) {
            case "join" -> new Color(0x20, 0xd8, 0x3a);
            case "quit" -> new Color(0xff, 0x22, 0x22);
            case "death" -> new Color(0x2b, 0x2d, 0x31);
            case "advancement" -> new Color(0xff, 0xd7, 0x00);
            default -> new Color(0x58, 0x65, 0xf2);
        };
    }

    private static String avatarUrl(String template, String player) {
        if (template == null || template.isBlank() || player == null || player.isBlank()) {
            return null;
        }
        return template.replace("{player}", player);
    }

    private static String cleanTitle(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("**", "")
                .replace(":arrow_right:", "")
                .replace(":arrow_left:", "")
                .replace(":skull:", "")
                .replace(":trophy:", "")
                .trim();
    }
}

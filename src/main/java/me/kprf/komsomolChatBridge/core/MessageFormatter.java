package me.kprf.komsomolChatBridge.core;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MessageFormatter {
    public record FormatSettings(
            String serverName,
            boolean escapeMarkdown,
            String minecraftFromDiscord,
            String minecraftFromTelegram,
            String minecraftSystem,
            String discordFromMinecraft,
            String discordFromTelegram,
            String discordSystem,
            String telegramFromMinecraft,
            String telegramFromDiscord,
            String telegramSystem
    ) {
        public static FormatSettings defaults() {
            return new FormatSettings(
                    "КомсомолКрафт",
                    true,
                    "<gray>[<blue>DS</blue>]</gray> <white>{username}</white>: <gray>{message}</gray>",
                    "<gray>[<aqua>TG</aqua>]</gray> <white>{username}</white>: <gray>{message}</gray>",
                    "<gold>{message}</gold>",
                    "**{player}**: {message}",
                    "📨 **TG | {username}**: {message}",
                    "📢 {message}",
                    "🎮 {player}: {message}",
                    "💬 DS | {username}: {message}",
                    "📢 {message}"
            );
        }
    }

    private volatile FormatSettings settings = FormatSettings.defaults();

    public void configure(FormatSettings settings) {
        this.settings = settings == null ? FormatSettings.defaults() : settings;
    }

    public String format(BridgeMessage message, BridgePlatform target) {
        FormatSettings current = settings;
        String template = templateFor(current, message.sourcePlatform(), target);
        Map<String, String> placeholders = placeholdersFor(message, target, current);
        String formatted = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            formatted = formatted.replace('{' + entry.getKey() + '}', entry.getValue());
        }
        return formatted;
    }

    public String formatPlain(BridgeMessage message) {
        return placeholdersFor(message, BridgePlatform.SYSTEM, settings).get("message");
    }

    private String templateFor(FormatSettings current, BridgePlatform source, BridgePlatform target) {
        if (source == BridgePlatform.SYSTEM) {
            return switch (target) {
                case MINECRAFT -> current.minecraftSystem();
                case DISCORD -> current.discordSystem();
                case TELEGRAM -> current.telegramSystem();
                case SYSTEM -> "{message}";
            };
        }
        return switch (target) {
            case MINECRAFT -> source == BridgePlatform.DISCORD
                    ? current.minecraftFromDiscord()
                    : current.minecraftFromTelegram();
            case DISCORD -> source == BridgePlatform.MINECRAFT
                    ? current.discordFromMinecraft()
                    : current.discordFromTelegram();
            case TELEGRAM -> source == BridgePlatform.MINECRAFT
                    ? current.telegramFromMinecraft()
                    : current.telegramFromDiscord();
            case SYSTEM -> "{message}";
        };
    }

    private Map<String, String> placeholdersFor(BridgeMessage message, BridgePlatform target, FormatSettings current) {
        String rawMessage = message.plainText();
        String username = message.displayName();
        String player = message.minecraftName() == null || message.minecraftName().isBlank()
                ? username
                : message.minecraftName();

        if (target == BridgePlatform.MINECRAFT) {
            rawMessage = escapeMiniMessage(rawMessage);
            username = escapeMiniMessage(username);
            player = escapeMiniMessage(player);
        } else if (target == BridgePlatform.DISCORD && current.escapeMarkdown()) {
            rawMessage = escapeMarkdown(rawMessage);
            username = escapeMarkdown(username);
            player = escapeMarkdown(player);
        }

        Map<String, String> values = new LinkedHashMap<>();
        values.put("message", rawMessage);
        values.put("username", username);
        values.put("player", player);
        values.put("server", current.serverName());
        values.put("platform", message.sourcePlatform().displayName());
        return values;
    }

    public static String escapeMarkdown(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("`", "\\`")
                .replace("~", "\\~")
                .replace("|", "\\|")
                .replace(">", "\\>");
    }

    public static String escapeMiniMessage(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("<", "\\<");
    }
}

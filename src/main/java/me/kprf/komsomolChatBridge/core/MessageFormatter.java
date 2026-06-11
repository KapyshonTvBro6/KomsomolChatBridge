package me.kprf.komsomolChatBridge.core;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MessageFormatter {
    public record FormatSettings(
            String serverName,
            boolean escapeMarkdown,
            String telegramParseMode,
            String minecraftFromDiscord,
            String minecraftFromTelegram,
            String minecraftSystem,
            Map<String, String> minecraftSystemMessages,
            String discordFromMinecraft,
            String discordFromTelegram,
            String discordSystem,
            Map<String, String> discordSystemMessages,
            String telegramFromMinecraft,
            String telegramFromDiscord,
            String telegramSystem,
            Map<String, String> telegramSystemMessages
    ) {
        public static FormatSettings defaults() {
            return new FormatSettings(
                    "KomsomolCraft",
                    true,
                    "HTML",
                    "[<aqua>Discord</aqua>] {username} » {message}",
                    "<gray>[<aqua>TG</aqua>]</gray> <white>{username}</white>: <gray>{message}</gray>",
                    "<gold>{message}</gold>",
                    Map.of(),
                    "{player} » {message}",
                    "TG | **{username}** » {message}",
                    "{message}",
                    Map.of(
                            "server_start", ":white_check_mark: **Server has started**",
                            "server_stop", ":octagonal_sign: **Server has stopped**"
                    ),
                    "<b>[💬 {player}]</b> {message}",
                    "<b>[💬 DS | {username}]</b> {message}",
                    "{message}",
                    Map.of(
                            "server_start", "✅ <b>Сервер {server} запущен!</b>",
                            "server_stop", "❌ <b>Сервер {server} остановлен!</b>"
                    )
            );
        }
    }

    private volatile FormatSettings settings = FormatSettings.defaults();

    public void configure(FormatSettings settings) {
        this.settings = settings == null ? FormatSettings.defaults() : settings;
    }

    public String format(BridgeMessage message, BridgePlatform target) {
        FormatSettings current = settings;
        String template = templateFor(current, message, target);
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

    private String templateFor(FormatSettings current, BridgeMessage message, BridgePlatform target) {
        BridgePlatform source = message.sourcePlatform();
        if (source == BridgePlatform.SYSTEM) {
            String eventTemplate = systemTemplateFor(current, target, message.metadata().get("system_key"));
            if (eventTemplate != null && !eventTemplate.isBlank()) {
                return eventTemplate;
            }
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

    private String systemTemplateFor(FormatSettings current, BridgePlatform target, String systemKey) {
        if (systemKey == null || systemKey.isBlank()) {
            return null;
        }
        return switch (target) {
            case MINECRAFT -> current.minecraftSystemMessages().get(systemKey);
            case DISCORD -> current.discordSystemMessages().get(systemKey);
            case TELEGRAM -> current.telegramSystemMessages().get(systemKey);
            case SYSTEM -> null;
        };
    }

    private Map<String, String> placeholdersFor(BridgeMessage message, BridgePlatform target, FormatSettings current) {
        String rawMessage = escapeForTarget(message.plainText(), target, current);
        String username = escapeForTarget(message.displayName(), target, current);
        String player = message.minecraftName() == null || message.minecraftName().isBlank()
                ? username
                : escapeForTarget(message.minecraftName(), target, current);

        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : message.metadata().entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                values.put(entry.getKey(), escapeForTarget(entry.getValue(), target, current));
            }
        }
        values.put("message", rawMessage);
        values.put("username", username);
        values.put("player", player);
        values.put("server", escapeForTarget(current.serverName(), target, current));
        values.put("platform", message.sourcePlatform().displayName());
        return values;
    }

    private String escapeForTarget(String value, BridgePlatform target, FormatSettings current) {
        if (target == BridgePlatform.MINECRAFT) {
            return escapeMiniMessage(value);
        }
        if (target == BridgePlatform.DISCORD && current.escapeMarkdown()) {
            return escapeMarkdown(value);
        }
        if (target == BridgePlatform.TELEGRAM && "HTML".equalsIgnoreCase(current.telegramParseMode())) {
            return escapeHtml(value);
        }
        return value == null ? "" : value;
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

    public static String escapeHtml(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}

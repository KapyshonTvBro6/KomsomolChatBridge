package me.kprf.komsomolChatBridge.config;

import me.kprf.komsomolChatBridge.core.BridgePlatform;
import me.kprf.komsomolChatBridge.core.MessageFilter;
import me.kprf.komsomolChatBridge.core.MessageFormatter;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.List;

public final class BridgeConfig {
    private final GeneralSettings general;
    private final MinecraftSettings minecraft;
    private final DiscordSettings discord;
    private final TelegramSettings telegram;
    private final AntiSpamSettings antiSpam;
    private final LoopProtectionSettings loopProtection;
    private final FilterSettings filters;
    private final SystemEventsSettings systemEvents;
    private final StorageSettings storage;

    private BridgeConfig(
            GeneralSettings general,
            MinecraftSettings minecraft,
            DiscordSettings discord,
            TelegramSettings telegram,
            AntiSpamSettings antiSpam,
            LoopProtectionSettings loopProtection,
            FilterSettings filters,
            SystemEventsSettings systemEvents,
            StorageSettings storage
    ) {
        this.general = general;
        this.minecraft = minecraft;
        this.discord = discord;
        this.telegram = telegram;
        this.antiSpam = antiSpam;
        this.loopProtection = loopProtection;
        this.filters = filters;
        this.systemEvents = systemEvents;
        this.storage = storage;
    }

    public static BridgeConfig load(JavaPlugin plugin) {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        GeneralSettings general = new GeneralSettings(
                config.getBoolean("general.debug", false),
                config.getString("general.language", "ru"),
                config.getString("general.server_name", "КомсомолКрафт"),
                config.getBoolean("general.ignore_empty_messages", true),
                config.getInt("general.max_message_length", 500),
                config.getBoolean("general.strip_colors_from_external", true),
                config.getBoolean("general.allow_links", true)
        );

        MinecraftSettings minecraft = new MinecraftSettings(
                config.getBoolean("minecraft.enabled", true),
                config.getBoolean("minecraft.relay_chat", true),
                config.getBoolean("minecraft.cancel_original_chat", false),
                config.getBoolean("minecraft.relay_join_leave", true),
                config.getBoolean("minecraft.relay_death", true),
                config.getBoolean("minecraft.relay_advancements", false),
                config.getBoolean("minecraft.private_receive_mode", false),
                config.getString("minecraft.format_from_discord", MessageFormatter.FormatSettings.defaults().minecraftFromDiscord()),
                config.getString("minecraft.format_from_telegram", MessageFormatter.FormatSettings.defaults().minecraftFromTelegram()),
                config.getString("minecraft.format_system", MessageFormatter.FormatSettings.defaults().minecraftSystem()),
                config.getString("minecraft.permission_receive_external", "komsomolbridge.receive"),
                config.getString("minecraft.permission_send_to_bridge", "komsomolbridge.send"),
                config.getString("minecraft.permission_antispam_bypass", "komsomolbridge.admin")
        );

        DiscordSettings discord = new DiscordSettings(
                config.getBoolean("discord.enabled", true),
                config.getString("discord.bot_token", ""),
                config.getString("discord.guild_id", ""),
                config.getString("discord.channel_id", ""),
                config.getBoolean("discord.ignore_bots", true),
                config.getBoolean("discord.use_webhook_for_minecraft_messages", true),
                config.getString("discord.webhook_url", ""),
                config.getString("discord.webhook_avatar_url", "https://mc-heads.net/avatar/{player}/128"),
                config.getString("discord.format_from_minecraft", MessageFormatter.FormatSettings.defaults().discordFromMinecraft()),
                config.getString("discord.format_from_telegram", MessageFormatter.FormatSettings.defaults().discordFromTelegram()),
                config.getString("discord.format_system", MessageFormatter.FormatSettings.defaults().discordSystem()),
                config.getBoolean("discord.relay_attachments", false)
        );

        TelegramWebhookSettings webhook = new TelegramWebhookSettings(
                config.getBoolean("telegram.webhook.enabled", false),
                config.getString("telegram.webhook.public_url", "https://example.com/tg/webhook"),
                config.getString("telegram.webhook.listen_host", "0.0.0.0"),
                config.getInt("telegram.webhook.listen_port", 8090)
        );
        TelegramSettings telegram = new TelegramSettings(
                config.getBoolean("telegram.enabled", true),
                config.getString("telegram.bot_token", ""),
                config.getString("telegram.chat_id", ""),
                config.getString("telegram.mode", "LONG_POLLING"),
                webhook,
                config.getBoolean("telegram.ignore_bots", true),
                config.getBoolean("telegram.relay_commands", false),
                config.getString("telegram.format_from_minecraft", MessageFormatter.FormatSettings.defaults().telegramFromMinecraft()),
                config.getString("telegram.format_from_discord", MessageFormatter.FormatSettings.defaults().telegramFromDiscord()),
                config.getString("telegram.format_system", MessageFormatter.FormatSettings.defaults().telegramSystem())
        );

        AntiSpamSettings antiSpam = new AntiSpamSettings(
                config.getBoolean("anti_spam.enabled", true),
                config.getInt("anti_spam.max_messages_per_10_seconds", 5),
                config.getInt("anti_spam.cooldown_seconds", 3),
                config.getInt("anti_spam.duplicate_window_seconds", 15)
        );

        LoopProtectionSettings loopProtection = new LoopProtectionSettings(
                config.getInt("loop_protection.cache_size", 1000),
                config.getInt("loop_protection.cache_ttl_minutes", 30)
        );

        FilterSettings filters = new FilterSettings(
                config.getStringList("filters.blacklist_words"),
                config.getStringList("filters.blacklist_regex"),
                config.getBoolean("filters.allow_links", general.allowLinks()),
                config.getBoolean("filters.escape_markdown", true)
        );

        SystemEventsSettings systemEvents = new SystemEventsSettings(
                config.getBoolean("system_events.join", true),
                config.getBoolean("system_events.quit", true),
                config.getBoolean("system_events.death", true),
                config.getBoolean("system_events.advancement", false),
                config.getBoolean("system_events.server_start", true),
                config.getBoolean("system_events.server_stop", true)
        );

        StorageSettings storage = new StorageSettings(
                config.getString("storage.type", "sqlite"),
                config.getString("storage.sqlite_file", "plugins/KomsomolChatBridge/chatbridge.db")
        );

        return new BridgeConfig(general, minecraft, discord, telegram, antiSpam, loopProtection, filters, systemEvents, storage);
    }

    public void applyToRouter(me.kprf.komsomolChatBridge.core.MessageRouter router) {
        router.setEnabled(BridgePlatform.MINECRAFT, minecraft.enabled());
        router.setEnabled(BridgePlatform.DISCORD, discord.enabled());
        router.setEnabled(BridgePlatform.TELEGRAM, telegram.enabled());
        router.setEnabled(BridgePlatform.SYSTEM, true);
    }

    public MessageFormatter.FormatSettings formatterSettings() {
        return new MessageFormatter.FormatSettings(
                general.serverName(),
                filters.escapeMarkdown(),
                minecraft.formatFromDiscord(),
                minecraft.formatFromTelegram(),
                minecraft.formatSystem(),
                discord.formatFromMinecraft(),
                discord.formatFromTelegram(),
                discord.formatSystem(),
                telegram.formatFromMinecraft(),
                telegram.formatFromDiscord(),
                telegram.formatSystem()
        );
    }

    public MessageFilter.Settings filterSettings() {
        return new MessageFilter.Settings(
                general.ignoreEmptyMessages(),
                general.maxMessageLength(),
                general.allowLinks() && filters.allowLinks(),
                general.stripColorsFromExternal(),
                filters.blacklistWords(),
                filters.blacklistRegex()
        );
    }

    public GeneralSettings general() {
        return general;
    }

    public MinecraftSettings minecraft() {
        return minecraft;
    }

    public DiscordSettings discord() {
        return discord;
    }

    public TelegramSettings telegram() {
        return telegram;
    }

    public AntiSpamSettings antiSpam() {
        return antiSpam;
    }

    public LoopProtectionSettings loopProtection() {
        return loopProtection;
    }

    public FilterSettings filters() {
        return filters;
    }

    public SystemEventsSettings systemEvents() {
        return systemEvents;
    }

    public StorageSettings storage() {
        return storage;
    }

    public record GeneralSettings(
            boolean debug,
            String language,
            String serverName,
            boolean ignoreEmptyMessages,
            int maxMessageLength,
            boolean stripColorsFromExternal,
            boolean allowLinks
    ) {
    }

    public record MinecraftSettings(
            boolean enabled,
            boolean relayChat,
            boolean cancelOriginalChat,
            boolean relayJoinLeave,
            boolean relayDeath,
            boolean relayAdvancements,
            boolean privateReceiveMode,
            String formatFromDiscord,
            String formatFromTelegram,
            String formatSystem,
            String permissionReceiveExternal,
            String permissionSendToBridge,
            String permissionAntispamBypass
    ) {
    }

    public record DiscordSettings(
            boolean enabled,
            String botToken,
            String guildId,
            String channelId,
            boolean ignoreBots,
            boolean useWebhookForMinecraftMessages,
            String webhookUrl,
            String webhookAvatarUrl,
            String formatFromMinecraft,
            String formatFromTelegram,
            String formatSystem,
            boolean relayAttachments
    ) {
    }

    public record TelegramWebhookSettings(
            boolean enabled,
            String publicUrl,
            String listenHost,
            int listenPort
    ) {
    }

    public record TelegramSettings(
            boolean enabled,
            String botToken,
            String chatId,
            String mode,
            TelegramWebhookSettings webhook,
            boolean ignoreBots,
            boolean relayCommands,
            String formatFromMinecraft,
            String formatFromDiscord,
            String formatSystem
    ) {
        public boolean useWebhook() {
            return webhook.enabled() || "WEBHOOK".equalsIgnoreCase(mode);
        }
    }

    public record AntiSpamSettings(
            boolean enabled,
            int maxMessagesPer10Seconds,
            int cooldownSeconds,
            int duplicateWindowSeconds
    ) {
        public Duration window() {
            return Duration.ofSeconds(10);
        }

        public Duration duplicateWindow() {
            return Duration.ofSeconds(Math.max(1, duplicateWindowSeconds));
        }
    }

    public record LoopProtectionSettings(
            int cacheSize,
            int cacheTtlMinutes
    ) {
        public Duration ttl() {
            return Duration.ofMinutes(Math.max(1, cacheTtlMinutes));
        }
    }

    public record FilterSettings(
            List<String> blacklistWords,
            List<String> blacklistRegex,
            boolean allowLinks,
            boolean escapeMarkdown
    ) {
    }

    public record SystemEventsSettings(
            boolean join,
            boolean quit,
            boolean death,
            boolean advancement,
            boolean serverStart,
            boolean serverStop
    ) {
    }

    public record StorageSettings(
            String type,
            String sqliteFile
    ) {
    }
}

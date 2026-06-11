package me.kprf.komsomolChatBridge.discord;

import me.kprf.komsomolChatBridge.config.BridgeConfig;
import me.kprf.komsomolChatBridge.core.ChatBridgeService;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class DiscordBridgeClient {
    private final Supplier<BridgeConfig> configSupplier;
    private final ChatBridgeService chatBridgeService;
    private final Consumer<String> infoLogger;
    private final BiConsumer<String, Throwable> errorLogger;
    private volatile JDA jda;

    public DiscordBridgeClient(
            Supplier<BridgeConfig> configSupplier,
            ChatBridgeService chatBridgeService,
            Consumer<String> infoLogger,
            BiConsumer<String, Throwable> errorLogger
    ) {
        this.configSupplier = configSupplier;
        this.chatBridgeService = chatBridgeService;
        this.infoLogger = infoLogger == null ? ignored -> { } : infoLogger;
        this.errorLogger = errorLogger == null ? (message, throwable) -> { } : errorLogger;
    }

    public synchronized void start() {
        BridgeConfig.DiscordSettings settings = configSupplier.get().discord();
        if (!settings.enabled()) {
            infoLogger.accept("Discord отключён в config.yml.");
            return;
        }
        if (settings.botToken() == null || settings.botToken().isBlank()) {
            infoLogger.accept("Discord bot_token пустой, Discord-клиент не запускается.");
            return;
        }
        if (settings.channelId() == null || settings.channelId().isBlank()) {
            infoLogger.accept("Discord channel_id пустой, Discord-клиент не запускается.");
            return;
        }

        shutdown();
        try {
            jda = JDABuilder.createDefault(settings.botToken())
                    .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                    .setAutoReconnect(true)
                    .addEventListeners(new DiscordEventListener(configSupplier, chatBridgeService))
                    .build();
            infoLogger.accept("Discord-клиент запускается.");
        } catch (RuntimeException exception) {
            errorLogger.accept("Не удалось запустить Discord-клиент.", exception);
            jda = null;
        }
    }

    public synchronized void shutdown() {
        JDA current = jda;
        jda = null;
        if (current != null) {
            current.shutdownNow();
        }
    }

    public boolean isConfigured() {
        BridgeConfig.DiscordSettings settings = configSupplier.get().discord();
        return settings.enabled()
                && settings.botToken() != null
                && !settings.botToken().isBlank()
                && settings.channelId() != null
                && !settings.channelId().isBlank();
    }

    public boolean isConnected() {
        JDA current = jda;
        return current != null && current.getStatus() == JDA.Status.CONNECTED;
    }

    public CompletableFuture<String> sendBotMessage(String text) {
        CompletableFuture<String> future = new CompletableFuture<>();
        JDA current = jda;
        if (current == null) {
            future.completeExceptionally(new IllegalStateException("Discord JDA не запущен."));
            return future;
        }

        TextChannel channel = current.getTextChannelById(configSupplier.get().discord().channelId());
        if (channel == null) {
            future.completeExceptionally(new IllegalStateException("Discord channel_id не найден или недоступен."));
            return future;
        }

        channel.sendMessage(text).queue(
                message -> future.complete(message.getId()),
                future::completeExceptionally
        );
        return future;
    }
}

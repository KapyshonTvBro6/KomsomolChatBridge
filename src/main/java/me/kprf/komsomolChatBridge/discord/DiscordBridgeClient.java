package me.kprf.komsomolChatBridge.discord;

import me.kprf.komsomolChatBridge.config.BridgeConfig;
import me.kprf.komsomolChatBridge.core.ChatBridgeService;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class DiscordBridgeClient {
    private final JavaPlugin plugin;
    private final Supplier<BridgeConfig> configSupplier;
    private final ChatBridgeService chatBridgeService;
    private final Consumer<String> infoLogger;
    private final BiConsumer<String, Throwable> errorLogger;
    private volatile JDA jda;
    private volatile CompletableFuture<Void> readyFuture = CompletableFuture.completedFuture(null);

    public DiscordBridgeClient(
            JavaPlugin plugin,
            Supplier<BridgeConfig> configSupplier,
            ChatBridgeService chatBridgeService,
            Consumer<String> infoLogger,
            BiConsumer<String, Throwable> errorLogger
    ) {
        this.plugin = plugin;
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
        if (!hasChatChannel(settings) && !hasConsoleChannel(settings)) {
            infoLogger.accept("Discord channel_id и console_channel_id пустые, Discord-клиент не запускается.");
            return;
        }

        shutdown();
        CompletableFuture<Void> nextReadyFuture = new CompletableFuture<>();
        readyFuture = nextReadyFuture;
        try {
            jda = JDABuilder.createDefault(settings.botToken())
                    .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                    .setAutoReconnect(true)
                    .addEventListeners(new DiscordEventListener(plugin, configSupplier, chatBridgeService))
                    .addEventListeners(new ListenerAdapter() {
                        @Override
                        public void onReady(ReadyEvent event) {
                            nextReadyFuture.complete(null);
                        }
                    })
                    .build();
            infoLogger.accept("Discord-клиент запускается.");
        } catch (RuntimeException exception) {
            nextReadyFuture.completeExceptionally(exception);
            errorLogger.accept("Не удалось запустить Discord-клиент.", exception);
            jda = null;
        }
    }

    public synchronized void shutdown() {
        JDA current = jda;
        CompletableFuture<Void> currentReady = readyFuture;
        jda = null;
        readyFuture = CompletableFuture.completedFuture(null);
        if (currentReady != null && !currentReady.isDone()) {
            currentReady.completeExceptionally(new IllegalStateException("Discord JDA остановлен."));
        }
        if (current != null) {
            current.shutdownNow();
        }
    }

    public boolean isConfigured() {
        BridgeConfig.DiscordSettings settings = configSupplier.get().discord();
        return settings.enabled() && hasToken(settings) && hasChatChannel(settings);
    }

    public boolean isConsoleConfigured() {
        BridgeConfig.DiscordSettings settings = configSupplier.get().discord();
        return settings.enabled() && hasToken(settings) && hasConsoleChannel(settings);
    }

    public boolean isConnected() {
        JDA current = jda;
        return current != null && current.getStatus() == JDA.Status.CONNECTED;
    }

    public CompletableFuture<String> sendBotMessage(String text) {
        return sendToChatChannel(settings -> settings.channelId(), "Discord channel_id пустой.", channel -> channel.sendMessage(text));
    }

    public CompletableFuture<String> sendConsoleMessage(String text) {
        return sendToChatChannel(settings -> settings.consoleChannelId(), "Discord console_channel_id пустой.", channel -> channel.sendMessage(text));
    }

    public CompletableFuture<String> sendEmbed(MessageEmbed embed) {
        if (embed == null) {
            CompletableFuture<String> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("Discord embed пустой."));
            return future;
        }
        return sendToChatChannel(settings -> settings.channelId(), "Discord channel_id пустой.", channel -> channel.sendMessageEmbeds(embed));
    }

    private CompletableFuture<String> sendToChatChannel(
            java.util.function.Function<BridgeConfig.DiscordSettings, String> channelIdResolver,
            String emptyChannelMessage,
            java.util.function.Function<TextChannel, net.dv8tion.jda.api.requests.restaction.MessageCreateAction> actionFactory
    ) {
        CompletableFuture<String> future = new CompletableFuture<>();
        JDA current = jda;
        if (current == null) {
            future.completeExceptionally(new IllegalStateException("Discord JDA не запущен."));
            return future;
        }

        BridgeConfig.DiscordSettings settings = configSupplier.get().discord();
        String channelId = channelIdResolver.apply(settings);
        if (channelId == null || channelId.isBlank()) {
            future.completeExceptionally(new IllegalStateException(emptyChannelMessage));
            return future;
        }

        CompletableFuture<Void> waitForReady = current.getStatus() == JDA.Status.CONNECTED
                ? CompletableFuture.completedFuture(null)
                : readyFuture.thenRun(() -> { }).orTimeout(30, TimeUnit.SECONDS);
        return waitForReady.thenCompose(ignored -> sendBotMessageNow(current, channelId, actionFactory));
    }

    private CompletableFuture<String> sendBotMessageNow(
            JDA current,
            String channelId,
            java.util.function.Function<TextChannel, net.dv8tion.jda.api.requests.restaction.MessageCreateAction> actionFactory
    ) {
        CompletableFuture<String> future = new CompletableFuture<>();
        TextChannel channel = current.getTextChannelById(channelId);
        if (channel == null) {
            future.completeExceptionally(new IllegalStateException("Discord channel_id не найден или недоступен."));
            return future;
        }

        actionFactory.apply(channel).queue(
                message -> future.complete(message.getId()),
                future::completeExceptionally
        );
        return future;
    }

    private boolean hasToken(BridgeConfig.DiscordSettings settings) {
        return settings.botToken() != null && !settings.botToken().isBlank();
    }

    private boolean hasChatChannel(BridgeConfig.DiscordSettings settings) {
        return settings.channelId() != null && !settings.channelId().isBlank();
    }

    private boolean hasConsoleChannel(BridgeConfig.DiscordSettings settings) {
        return settings.consoleChannelId() != null && !settings.consoleChannelId().isBlank();
    }
}

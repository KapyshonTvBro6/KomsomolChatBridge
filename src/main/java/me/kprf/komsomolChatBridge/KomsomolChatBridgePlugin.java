package me.kprf.komsomolChatBridge;

import me.kprf.komsomolChatBridge.api.SimpleChatBridgeApi;
import me.kprf.komsomolChatBridge.commands.BridgeCommand;
import me.kprf.komsomolChatBridge.config.BridgeConfig;
import me.kprf.komsomolChatBridge.config.MessagesConfig;
import me.kprf.komsomolChatBridge.core.BridgeEventSink;
import me.kprf.komsomolChatBridge.core.BridgeMessage;
import me.kprf.komsomolChatBridge.core.BridgePlatform;
import me.kprf.komsomolChatBridge.core.ChatBridgeService;
import me.kprf.komsomolChatBridge.core.LoopProtectionService;
import me.kprf.komsomolChatBridge.core.MentionService;
import me.kprf.komsomolChatBridge.core.MessageFilter;
import me.kprf.komsomolChatBridge.core.MessageFormatter;
import me.kprf.komsomolChatBridge.core.MessageRouter;
import me.kprf.komsomolChatBridge.core.RateLimitService;
import me.kprf.komsomolChatBridge.discord.DiscordBridgeClient;
import me.kprf.komsomolChatBridge.discord.DiscordMessageSender;
import me.kprf.komsomolChatBridge.discord.DiscordWebhookSender;
import me.kprf.komsomolChatBridge.events.BridgeMessageBlockedEvent;
import me.kprf.komsomolChatBridge.events.BridgeMessageReceivedEvent;
import me.kprf.komsomolChatBridge.events.BridgeMessageSentEvent;
import me.kprf.komsomolChatBridge.minecraft.MinecraftChatListener;
import me.kprf.komsomolChatBridge.minecraft.MinecraftMessageSender;
import me.kprf.komsomolChatBridge.minecraft.MinecraftSystemEventsListener;
import me.kprf.komsomolChatBridge.storage.SQLiteStorageService;
import me.kprf.komsomolChatBridge.storage.StorageService;
import me.kprf.komsomolChatBridge.telegram.TelegramBridgeClient;
import me.kprf.komsomolChatBridge.telegram.TelegramMessageSender;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.Event;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public final class KomsomolChatBridgePlugin extends JavaPlugin {
    private ExecutorService executorService;
    private BridgeConfig bridgeConfig;
    private MessagesConfig messagesConfig;
    private StorageService storageService;
    private ChatBridgeService chatBridgeService;
    private MessageRouter messageRouter;
    private RateLimitService rateLimitService;
    private MessageFilter messageFilter;
    private MessageFormatter messageFormatter;
    private DiscordBridgeClient discordBridgeClient;
    private TelegramBridgeClient telegramBridgeClient;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        executorService = createExecutor();
        messagesConfig = new MessagesConfig(this);
        bridgeConfig = BridgeConfig.load(this);

        try {
            storageService = createStorageService(bridgeConfig);
            storageService.init();
        } catch (RuntimeException exception) {
            getLogger().log(Level.SEVERE, "SQLite недоступен, bridge будет безопасно отключён.", exception);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        createCoreServices();
        registerMinecraft();
        registerCommand();
        startExternalClients();
        KomsomolChatBridge.setApi(new SimpleChatBridgeApi(chatBridgeService));

        if (bridgeConfig.systemEvents().serverStart()) {
            publishExternalSystem("server_start");
        }
        getLogger().info("KomsomolChatBridge включён.");
    }

    @Override
    public void onDisable() {
        if (chatBridgeService != null && messagesConfig != null && bridgeConfig != null && bridgeConfig.systemEvents().serverStop()) {
            try {
                publishExternalSystem("server_stop").get(2, TimeUnit.SECONDS);
            } catch (TimeoutException exception) {
                getLogger().warning("Не удалось дождаться отправки server_stop за 2 секунды.");
            } catch (Exception exception) {
                getLogger().log(Level.WARNING, "Ошибка отправки server_stop.", exception);
            }
        }

        KomsomolChatBridge.clearApi();
        if (discordBridgeClient != null) {
            discordBridgeClient.shutdown();
        }
        if (telegramBridgeClient != null) {
            telegramBridgeClient.shutdown();
        }
        if (storageService != null) {
            storageService.close();
        }
        shutdownExecutor();
        getLogger().info("KomsomolChatBridge выключен.");
    }

    public void reloadBridge() {
        reloadConfig();
        messagesConfig.reload();
        bridgeConfig = BridgeConfig.load(this);
        bridgeConfig.applyToRouter(messageRouter);
        messageFormatter.configure(bridgeConfig.formatterSettings());
        messageFilter.configure(bridgeConfig.filterSettings());
        rateLimitService.configure(
                bridgeConfig.antiSpam().enabled(),
                bridgeConfig.antiSpam().maxMessagesPer10Seconds(),
                bridgeConfig.antiSpam().window(),
                bridgeConfig.antiSpam().duplicateWindow()
        );

        discordBridgeClient.shutdown();
        telegramBridgeClient.shutdown();
        startExternalClients();
    }

    public BridgeConfig bridgeConfig() {
        return bridgeConfig;
    }

    public MessagesConfig messagesConfig() {
        return messagesConfig;
    }

    public ChatBridgeService chatBridgeService() {
        return chatBridgeService;
    }

    public DiscordBridgeClient discordBridgeClient() {
        return discordBridgeClient;
    }

    public TelegramBridgeClient telegramBridgeClient() {
        return telegramBridgeClient;
    }

    private void createCoreServices() {
        messageRouter = new MessageRouter();
        bridgeConfig.applyToRouter(messageRouter);

        LoopProtectionService loopProtectionService = new LoopProtectionService(
                bridgeConfig.loopProtection().cacheSize(),
                bridgeConfig.loopProtection().ttl()
        );
        rateLimitService = new RateLimitService(
                bridgeConfig.antiSpam().enabled(),
                bridgeConfig.antiSpam().maxMessagesPer10Seconds(),
                bridgeConfig.antiSpam().window(),
                bridgeConfig.antiSpam().duplicateWindow()
        );
        messageFilter = new MessageFilter();
        messageFilter.configure(bridgeConfig.filterSettings());
        messageFormatter = new MessageFormatter();
        messageFormatter.configure(bridgeConfig.formatterSettings());

        chatBridgeService = new ChatBridgeService(
                executorService,
                messageRouter,
                loopProtectionService,
                rateLimitService,
                messageFilter,
                messageFormatter,
                new MentionService(),
                storageService,
                createEventSink(),
                this::debug,
                this::logError
        );

        MinecraftMessageSender minecraftSender = new MinecraftMessageSender(this, this::bridgeConfig);
        DiscordWebhookSender webhookSender = new DiscordWebhookSender(this::bridgeConfig);
        discordBridgeClient = new DiscordBridgeClient(this, this::bridgeConfig, chatBridgeService, getLogger()::info, this::logError);
        telegramBridgeClient = new TelegramBridgeClient(this::bridgeConfig, chatBridgeService, executorService, getLogger()::info, this::logError);

        chatBridgeService.registerSender(BridgePlatform.MINECRAFT, minecraftSender);
        chatBridgeService.registerSender(BridgePlatform.DISCORD, new DiscordMessageSender(this::bridgeConfig, discordBridgeClient, webhookSender));
        chatBridgeService.registerSender(BridgePlatform.TELEGRAM, new TelegramMessageSender(this::bridgeConfig, telegramBridgeClient));
    }

    private void registerMinecraft() {
        Bukkit.getPluginManager().registerEvents(new MinecraftChatListener(this::bridgeConfig, chatBridgeService), this);
        Bukkit.getPluginManager().registerEvents(new MinecraftSystemEventsListener(this::bridgeConfig, messagesConfig, chatBridgeService), this);
    }

    private void registerCommand() {
        BridgeCommand bridgeCommand = new BridgeCommand(this);
        PluginCommand command = getCommand("bridge");
        if (command != null) {
            command.setExecutor(bridgeCommand);
            command.setTabCompleter(bridgeCommand);
        }
    }

    private void startExternalClients() {
        discordBridgeClient.start();
        telegramBridgeClient.start();
    }

    private java.util.concurrent.CompletableFuture<Void> publishExternalSystem(String messageKey) {
        String serverName = bridgeConfig.general().serverName();
        String text = messagesConfig.systemMessage(messageKey, Map.of("server", serverName));
        return chatBridgeService.publish(BridgeMessage.builder(BridgePlatform.SYSTEM)
                .sourceUserName("KomsomolBridge")
                .plainText(text)
                .formattedText(text)
                .system(true)
                .metadata("external_only", "true")
                .metadata("system_key", messageKey)
                .metadata("server", serverName)
                .metadata("source_message_id", "system:" + messageKey + ':' + System.nanoTime())
                .build());
    }

    private BridgeEventSink createEventSink() {
        return new BridgeEventSink() {
            @Override
            public void received(BridgeMessage message) {
                callEvent(new BridgeMessageReceivedEvent(message));
            }

            @Override
            public void sent(BridgeMessage message, BridgePlatform targetPlatform, String externalMessageId) {
                callEvent(new BridgeMessageSentEvent(message, targetPlatform, externalMessageId));
            }

            @Override
            public void blocked(BridgeMessage message, String reason) {
                callEvent(new BridgeMessageBlockedEvent(message, reason));
                debug("Сообщение " + message.internalId() + " заблокировано: " + messagesConfig.blockedReason(reason));
            }
        };
    }

    private void callEvent(Event event) {
        Runnable task = () -> Bukkit.getPluginManager().callEvent(event);
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else if (isEnabled()) {
            Bukkit.getScheduler().runTask(this, task);
        }
    }

    private StorageService createStorageService(BridgeConfig config) {
        if (!"sqlite".equalsIgnoreCase(config.storage().type())) {
            throw new IllegalStateException("Сейчас поддерживается только storage.type=sqlite. MySQL оставлен как расширение.");
        }
        return new SQLiteStorageService(resolveStoragePath(config.storage().sqliteFile()), this::logError);
    }

    private Path resolveStoragePath(String configuredPath) {
        Path path = Path.of(configuredPath == null || configuredPath.isBlank() ? "chatbridge.db" : configuredPath);
        if (path.isAbsolute()) {
            return path;
        }
        String normalized = configuredPath == null ? "" : configuredPath.replace('\\', '/');
        if (normalized.startsWith("plugins/")) {
            return path;
        }
        return getDataFolder().toPath().resolve(path);
    }

    private ExecutorService createExecutor() {
        AtomicInteger counter = new AtomicInteger();
        return Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "KomsomolChatBridge-async-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    private void shutdownExecutor() {
        if (executorService == null) {
            return;
        }
        executorService.shutdownNow();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                getLogger().warning("Не все async-задачи bridge завершились за 5 секунд.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void debug(String message) {
        if (bridgeConfig != null && bridgeConfig.general().debug()) {
            getLogger().info("[debug] " + message);
        }
    }

    private void logError(String message, Throwable throwable) {
        getLogger().log(Level.WARNING, message, throwable);
    }
}

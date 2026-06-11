package me.kprf.komsomolChatBridge.core;

import me.kprf.komsomolChatBridge.storage.MessageRecord;
import me.kprf.komsomolChatBridge.storage.StorageService;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class ChatBridgeService {
    private final Executor executor;
    private final MessageRouter router;
    private final LoopProtectionService loopProtectionService;
    private final RateLimitService rateLimitService;
    private final MessageFilter messageFilter;
    private final MessageFormatter messageFormatter;
    private final MentionService mentionService;
    private final StorageService storageService;
    private final BridgeEventSink eventSink;
    private final BridgeStats stats = new BridgeStats();
    private final Map<BridgePlatform, BridgeOutboundSender> senders = new EnumMap<>(BridgePlatform.class);
    private final Consumer<String> debugLogger;
    private final BiConsumer<String, Throwable> errorLogger;

    public ChatBridgeService(
            Executor executor,
            MessageRouter router,
            LoopProtectionService loopProtectionService,
            RateLimitService rateLimitService,
            MessageFilter messageFilter,
            MessageFormatter messageFormatter,
            MentionService mentionService,
            StorageService storageService,
            BridgeEventSink eventSink,
            Consumer<String> debugLogger,
            BiConsumer<String, Throwable> errorLogger
    ) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.router = Objects.requireNonNull(router, "router");
        this.loopProtectionService = Objects.requireNonNull(loopProtectionService, "loopProtectionService");
        this.rateLimitService = Objects.requireNonNull(rateLimitService, "rateLimitService");
        this.messageFilter = Objects.requireNonNull(messageFilter, "messageFilter");
        this.messageFormatter = Objects.requireNonNull(messageFormatter, "messageFormatter");
        this.mentionService = Objects.requireNonNull(mentionService, "mentionService");
        this.storageService = Objects.requireNonNull(storageService, "storageService");
        this.eventSink = eventSink == null ? BridgeEventSink.NOOP : eventSink;
        this.debugLogger = debugLogger == null ? ignored -> { } : debugLogger;
        this.errorLogger = errorLogger == null ? (message, throwable) -> { } : errorLogger;
    }

    public void registerSender(BridgePlatform platform, BridgeOutboundSender sender) {
        if (platform != null && sender != null) {
            senders.put(platform, sender);
        }
    }

    public CompletableFuture<Void> publish(BridgeMessage message) {
        return CompletableFuture.runAsync(() -> processIncoming(message), executor);
    }

    public CompletableFuture<Void> sendSystemMessage(String message) {
        return publish(BridgeMessage.builder(BridgePlatform.SYSTEM)
                .sourceUserName("KomsomolBridge")
                .plainText(message)
                .formattedText(message)
                .system(true)
                .build());
    }

    public CompletableFuture<Void> sendDirect(BridgePlatform target, String message) {
        BridgeMessage bridgeMessage = BridgeMessage.builder(BridgePlatform.SYSTEM)
                .sourceUserName("KomsomolBridge")
                .plainText(message)
                .formattedText(message)
                .system(true)
                .build();
        return CompletableFuture
                .supplyAsync(() -> dispatchToTarget(target, bridgeMessage), executor)
                .thenCompose(future -> future)
                .thenApply(ignored -> null);
    }

    public BridgeStats stats() {
        return stats;
    }

    public MessageRouter router() {
        return router;
    }

    private void processIncoming(BridgeMessage originalMessage) {
        BridgeMessage message = originalMessage;
        if (!loopProtectionService.markIfNew(message)) {
            stats.incrementBlockedByLoopProtection();
            eventSink.blocked(message, "loop");
            storageService.incrementStat("blocked_loop");
            return;
        }

        MessageFilter.Result filterResult = messageFilter.filter(message);
        if (!filterResult.allowed()) {
            stats.incrementBlockedByFilter();
            eventSink.blocked(message, filterResult.reason());
            storageService.incrementStat("blocked_filter");
            return;
        }
        message = message.toBuilder()
                .plainText(filterResult.sanitizedText())
                .formattedText(filterResult.sanitizedText())
                .build();

        boolean bypassRateLimit = Boolean.parseBoolean(message.metadata().getOrDefault("rate_limit_bypass", "false"));
        RateLimitService.Result rateLimitResult = rateLimitService.check(message, bypassRateLimit);
        if (!rateLimitResult.allowed()) {
            stats.incrementBlockedByAntispam();
            eventSink.blocked(message, rateLimitResult.reason());
            storageService.incrementStat("blocked_antispam");
            return;
        }

        stats.incrementProcessed();
        storageService.incrementStat("processed");
        eventSink.received(message);

        EnumSet<BridgePlatform> targets = router.targetsFor(message);
        BridgeMessage routedMessage = message;
        CompletableFuture<?>[] futures = targets.stream()
                .map(target -> dispatchToTarget(target, routedMessage))
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(futures).join();
    }

    private CompletableFuture<String> dispatchToTarget(BridgePlatform target, BridgeMessage sourceMessage) {
        BridgeOutboundSender sender = senders.get(target);
        if (sender == null || !sender.isAvailable()) {
            debugLogger.accept("Отправитель для " + target.displayName() + " недоступен, сообщение " + sourceMessage.internalId() + " пропущено.");
            return CompletableFuture.completedFuture(null);
        }

        BridgeMessage outgoingMessage = prepareForTarget(sourceMessage, target);
        return sender.send(outgoingMessage)
                .handle((externalMessageId, throwable) -> {
                    if (throwable != null) {
                        errorLogger.accept("Не удалось отправить bridge-сообщение в " + target.displayName(), throwable);
                        return null;
                    }
                    stats.incrementSent();
                    storageService.incrementStat("sent");
                    storageService.saveMessageRecord(new MessageRecord(
                            outgoingMessage.internalId().toString(),
                            sourceMessage.sourcePlatform().name(),
                            sourceMessage.metadata().getOrDefault("original_message_id", ""),
                            target == BridgePlatform.DISCORD ? nullToEmpty(externalMessageId) : "",
                            target == BridgePlatform.TELEGRAM ? nullToEmpty(externalMessageId) : "",
                            Instant.now()
                    ));
                    eventSink.sent(outgoingMessage, target, externalMessageId);
                    return externalMessageId;
                });
    }

    private BridgeMessage prepareForTarget(BridgeMessage sourceMessage, BridgePlatform target) {
        String formattedText = messageFormatter.format(sourceMessage, target);
        if (target == BridgePlatform.DISCORD) {
            formattedText = mentionService.sanitizeDiscordMentions(formattedText);
        } else if (target == BridgePlatform.TELEGRAM) {
            formattedText = mentionService.sanitizeTelegramMentions(formattedText);
        }

        Map<String, String> metadata = new LinkedHashMap<>(sourceMessage.metadata());
        metadata.put("original_platform", sourceMessage.sourcePlatform().name());
        metadata.put("bridge_internal_id", sourceMessage.internalId().toString());
        metadata.putIfAbsent("original_message_id", sourceMessage.metadata().getOrDefault("source_message_id", ""));
        metadata.put("target_platform", target.name());

        return sourceMessage.toBuilder()
                .formattedText(formattedText)
                .metadata(metadata)
                .build();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

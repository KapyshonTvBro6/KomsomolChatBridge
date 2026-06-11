package me.kprf.komsomolChatBridge.telegram;

import me.kprf.komsomolChatBridge.config.BridgeConfig;
import me.kprf.komsomolChatBridge.core.BridgeMessage;
import me.kprf.komsomolChatBridge.core.BridgeOutboundSender;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class TelegramMessageSender implements BridgeOutboundSender {
    private final Supplier<BridgeConfig> configSupplier;
    private final TelegramBridgeClient telegramBridgeClient;

    public TelegramMessageSender(Supplier<BridgeConfig> configSupplier, TelegramBridgeClient telegramBridgeClient) {
        this.configSupplier = configSupplier;
        this.telegramBridgeClient = telegramBridgeClient;
    }

    @Override
    public CompletableFuture<String> send(BridgeMessage message) {
        return telegramBridgeClient.sendMessage(message.formattedText(), message.replyToMessageId());
    }

    @Override
    public boolean isAvailable() {
        return configSupplier.get().telegram().enabled() && telegramBridgeClient.isConfigured();
    }
}

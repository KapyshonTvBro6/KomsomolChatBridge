package me.kprf.komsomolChatBridge.api;

import me.kprf.komsomolChatBridge.core.BridgeMessage;
import me.kprf.komsomolChatBridge.core.BridgePlatform;
import me.kprf.komsomolChatBridge.core.ChatBridgeService;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class SimpleChatBridgeApi implements ChatBridgeApi {
    private final ChatBridgeService chatBridgeService;

    public SimpleChatBridgeApi(ChatBridgeService chatBridgeService) {
        this.chatBridgeService = Objects.requireNonNull(chatBridgeService, "chatBridgeService");
    }

    @Override
    public CompletableFuture<Void> sendSystemMessage(String message) {
        return chatBridgeService.sendSystemMessage(message);
    }

    @Override
    public CompletableFuture<Void> sendSystemMessage(Component component) {
        String plainText = PlainTextComponentSerializer.plainText().serialize(component);
        return chatBridgeService.sendSystemMessage(plainText);
    }

    @Override
    public CompletableFuture<Void> sendToDiscord(String message) {
        return chatBridgeService.sendDirect(BridgePlatform.DISCORD, message);
    }

    @Override
    public CompletableFuture<Void> sendToTelegram(String message) {
        return chatBridgeService.sendDirect(BridgePlatform.TELEGRAM, message);
    }

    @Override
    public CompletableFuture<Void> broadcastBridgeMessage(BridgeMessage message) {
        return chatBridgeService.publish(message);
    }
}

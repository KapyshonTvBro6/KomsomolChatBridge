package me.kprf.komsomolChatBridge.api;

import me.kprf.komsomolChatBridge.core.BridgeMessage;

import net.kyori.adventure.text.Component;

import java.util.concurrent.CompletableFuture;

public interface ChatBridgeApi {
    CompletableFuture<Void> sendSystemMessage(String message);

    CompletableFuture<Void> sendSystemMessage(Component component);

    CompletableFuture<Void> sendToDiscord(String message);

    CompletableFuture<Void> sendToTelegram(String message);

    CompletableFuture<Void> broadcastBridgeMessage(BridgeMessage message);
}

package me.kprf.komsomolChatBridge.core;

import java.util.concurrent.CompletableFuture;

public interface BridgeOutboundSender {
    CompletableFuture<String> send(BridgeMessage message);

    boolean isAvailable();
}

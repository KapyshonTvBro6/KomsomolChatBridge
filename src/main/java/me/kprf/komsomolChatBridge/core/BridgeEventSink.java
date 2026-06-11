package me.kprf.komsomolChatBridge.core;

public interface BridgeEventSink {
    BridgeEventSink NOOP = new BridgeEventSink() {
    };

    default void received(BridgeMessage message) {
    }

    default void sent(BridgeMessage message, BridgePlatform targetPlatform, String externalMessageId) {
    }

    default void blocked(BridgeMessage message, String reason) {
    }
}

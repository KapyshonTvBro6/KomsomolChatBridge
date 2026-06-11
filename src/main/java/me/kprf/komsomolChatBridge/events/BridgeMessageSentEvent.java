package me.kprf.komsomolChatBridge.events;

import me.kprf.komsomolChatBridge.core.BridgeMessage;
import me.kprf.komsomolChatBridge.core.BridgePlatform;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class BridgeMessageSentEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final BridgeMessage message;
    private final BridgePlatform targetPlatform;
    private final String externalMessageId;

    public BridgeMessageSentEvent(BridgeMessage message, BridgePlatform targetPlatform, String externalMessageId) {
        this.message = message;
        this.targetPlatform = targetPlatform;
        this.externalMessageId = externalMessageId;
    }

    public BridgeMessage message() {
        return message;
    }

    public BridgePlatform targetPlatform() {
        return targetPlatform;
    }

    public String externalMessageId() {
        return externalMessageId;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

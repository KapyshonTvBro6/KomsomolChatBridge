package me.kprf.komsomolChatBridge.events;

import me.kprf.komsomolChatBridge.core.BridgeMessage;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class BridgeMessageBlockedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final BridgeMessage message;
    private final String reason;

    public BridgeMessageBlockedEvent(BridgeMessage message, String reason) {
        this.message = message;
        this.reason = reason;
    }

    public BridgeMessage message() {
        return message;
    }

    public String reason() {
        return reason;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

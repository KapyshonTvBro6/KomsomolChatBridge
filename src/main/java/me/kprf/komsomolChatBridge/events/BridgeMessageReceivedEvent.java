package me.kprf.komsomolChatBridge.events;

import me.kprf.komsomolChatBridge.core.BridgeMessage;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class BridgeMessageReceivedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final BridgeMessage message;

    public BridgeMessageReceivedEvent(BridgeMessage message) {
        this.message = message;
    }

    public BridgeMessage message() {
        return message;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

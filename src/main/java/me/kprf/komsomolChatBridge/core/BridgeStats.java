package me.kprf.komsomolChatBridge.core;

import java.util.concurrent.atomic.LongAdder;

public final class BridgeStats {
    private final LongAdder processedMessages = new LongAdder();
    private final LongAdder sentMessages = new LongAdder();
    private final LongAdder blockedByAntispam = new LongAdder();
    private final LongAdder blockedByLoopProtection = new LongAdder();
    private final LongAdder blockedByFilter = new LongAdder();

    public void incrementProcessed() {
        processedMessages.increment();
    }

    public void incrementSent() {
        sentMessages.increment();
    }

    public void incrementBlockedByAntispam() {
        blockedByAntispam.increment();
    }

    public void incrementBlockedByLoopProtection() {
        blockedByLoopProtection.increment();
    }

    public void incrementBlockedByFilter() {
        blockedByFilter.increment();
    }

    public long processedMessages() {
        return processedMessages.sum();
    }

    public long sentMessages() {
        return sentMessages.sum();
    }

    public long blockedByAntispam() {
        return blockedByAntispam.sum();
    }

    public long blockedByLoopProtection() {
        return blockedByLoopProtection.sum();
    }

    public long blockedByFilter() {
        return blockedByFilter.sum();
    }
}

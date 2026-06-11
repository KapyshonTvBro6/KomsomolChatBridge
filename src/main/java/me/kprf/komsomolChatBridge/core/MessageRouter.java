package me.kprf.komsomolChatBridge.core;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

public final class MessageRouter {
    private final Map<BridgePlatform, Boolean> enabled = new EnumMap<>(BridgePlatform.class);
    private final EnumSet<BridgePlatform> muted = EnumSet.noneOf(BridgePlatform.class);

    public MessageRouter() {
        for (BridgePlatform platform : BridgePlatform.values()) {
            enabled.put(platform, true);
        }
    }

    public synchronized void setEnabled(BridgePlatform platform, boolean value) {
        enabled.put(platform, value);
    }

    public synchronized boolean isEnabled(BridgePlatform platform) {
        return enabled.getOrDefault(platform, false);
    }

    public synchronized void mute(BridgePlatform platform) {
        if (platform == BridgePlatform.DISCORD || platform == BridgePlatform.TELEGRAM) {
            muted.add(platform);
        }
    }

    public synchronized void unmute(BridgePlatform platform) {
        muted.remove(platform);
    }

    public synchronized boolean isMuted(BridgePlatform platform) {
        return muted.contains(platform);
    }

    public synchronized EnumSet<BridgePlatform> targetsFor(BridgeMessage message) {
        EnumSet<BridgePlatform> targets = switch (message.sourcePlatform()) {
            case MINECRAFT -> EnumSet.of(BridgePlatform.DISCORD, BridgePlatform.TELEGRAM);
            case DISCORD -> EnumSet.of(BridgePlatform.MINECRAFT, BridgePlatform.TELEGRAM);
            case TELEGRAM -> EnumSet.of(BridgePlatform.MINECRAFT, BridgePlatform.DISCORD);
            case SYSTEM -> Boolean.parseBoolean(message.metadata().getOrDefault("external_only", "false"))
                    ? EnumSet.of(BridgePlatform.DISCORD, BridgePlatform.TELEGRAM)
                    : EnumSet.of(BridgePlatform.MINECRAFT, BridgePlatform.DISCORD, BridgePlatform.TELEGRAM);
        };

        targets.removeIf(platform -> !enabled.getOrDefault(platform, false) || muted.contains(platform));
        return targets;
    }
}

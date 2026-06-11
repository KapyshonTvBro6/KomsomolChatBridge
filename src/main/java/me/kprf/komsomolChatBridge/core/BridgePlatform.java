package me.kprf.komsomolChatBridge.core;

import java.util.Locale;
import java.util.Optional;

public enum BridgePlatform {
    MINECRAFT("Minecraft"),
    DISCORD("Discord"),
    TELEGRAM("Telegram"),
    SYSTEM("Система");

    private final String displayName;

    BridgePlatform(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static Optional<BridgePlatform> externalFromConfigName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "discord", "ds" -> Optional.of(DISCORD);
            case "telegram", "tg" -> Optional.of(TELEGRAM);
            default -> Optional.empty();
        };
    }
}

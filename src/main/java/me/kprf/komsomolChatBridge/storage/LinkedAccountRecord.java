package me.kprf.komsomolChatBridge.storage;

import java.time.Instant;

public record LinkedAccountRecord(
        String minecraftUuid,
        String minecraftName,
        String discordUserId,
        String telegramUserId,
        Instant createdAt,
        Instant updatedAt
) {
}

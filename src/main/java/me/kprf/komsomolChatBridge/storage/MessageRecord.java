package me.kprf.komsomolChatBridge.storage;

import java.time.Instant;

public record MessageRecord(
        String internalId,
        String sourcePlatform,
        String sourceMessageId,
        String discordMessageId,
        String telegramMessageId,
        Instant createdAt
) {
}

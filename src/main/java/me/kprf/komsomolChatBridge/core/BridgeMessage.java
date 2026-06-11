package me.kprf.komsomolChatBridge.core;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record BridgeMessage(
        UUID internalId,
        BridgePlatform sourcePlatform,
        String sourceChannelId,
        String sourceUserId,
        String sourceUserName,
        UUID minecraftUuid,
        String minecraftName,
        String plainText,
        String formattedText,
        Instant timestamp,
        boolean system,
        String replyToMessageId,
        Map<String, String> metadata
) {
    public BridgeMessage {
        internalId = internalId == null ? UUID.randomUUID() : internalId;
        sourcePlatform = Objects.requireNonNull(sourcePlatform, "sourcePlatform");
        plainText = plainText == null ? "" : plainText;
        formattedText = formattedText == null ? plainText : formattedText;
        timestamp = timestamp == null ? Instant.now() : timestamp;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static Builder builder(BridgePlatform sourcePlatform) {
        return new Builder(sourcePlatform);
    }

    public Builder toBuilder() {
        return new Builder(sourcePlatform)
                .internalId(internalId)
                .sourceChannelId(sourceChannelId)
                .sourceUserId(sourceUserId)
                .sourceUserName(sourceUserName)
                .minecraftUuid(minecraftUuid)
                .minecraftName(minecraftName)
                .plainText(plainText)
                .formattedText(formattedText)
                .timestamp(timestamp)
                .system(system)
                .replyToMessageId(replyToMessageId)
                .metadata(metadata);
    }

    public String actorKey() {
        if (minecraftUuid != null) {
            return BridgePlatform.MINECRAFT.name() + ':' + minecraftUuid;
        }
        if (sourceUserId != null && !sourceUserId.isBlank()) {
            return sourcePlatform.name() + ':' + sourceUserId;
        }
        if (sourceUserName != null && !sourceUserName.isBlank()) {
            return sourcePlatform.name() + ':' + sourceUserName.toLowerCase();
        }
        return sourcePlatform.name() + ":unknown";
    }

    public String displayName() {
        if (minecraftName != null && !minecraftName.isBlank()) {
            return minecraftName;
        }
        if (sourceUserName != null && !sourceUserName.isBlank()) {
            return sourceUserName;
        }
        return sourcePlatform.displayName();
    }

    public static final class Builder {
        private UUID internalId = UUID.randomUUID();
        private final BridgePlatform sourcePlatform;
        private String sourceChannelId;
        private String sourceUserId;
        private String sourceUserName;
        private UUID minecraftUuid;
        private String minecraftName;
        private String plainText = "";
        private String formattedText;
        private Instant timestamp = Instant.now();
        private boolean system;
        private String replyToMessageId;
        private final Map<String, String> metadata = new LinkedHashMap<>();

        private Builder(BridgePlatform sourcePlatform) {
            this.sourcePlatform = Objects.requireNonNull(sourcePlatform, "sourcePlatform");
        }

        public Builder internalId(UUID internalId) {
            this.internalId = internalId;
            return this;
        }

        public Builder sourceChannelId(String sourceChannelId) {
            this.sourceChannelId = sourceChannelId;
            return this;
        }

        public Builder sourceUserId(String sourceUserId) {
            this.sourceUserId = sourceUserId;
            return this;
        }

        public Builder sourceUserName(String sourceUserName) {
            this.sourceUserName = sourceUserName;
            return this;
        }

        public Builder minecraftUuid(UUID minecraftUuid) {
            this.minecraftUuid = minecraftUuid;
            return this;
        }

        public Builder minecraftName(String minecraftName) {
            this.minecraftName = minecraftName;
            return this;
        }

        public Builder plainText(String plainText) {
            this.plainText = plainText;
            return this;
        }

        public Builder formattedText(String formattedText) {
            this.formattedText = formattedText;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder system(boolean system) {
            this.system = system;
            return this;
        }

        public Builder replyToMessageId(String replyToMessageId) {
            this.replyToMessageId = replyToMessageId;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata.clear();
            if (metadata != null) {
                this.metadata.putAll(metadata);
            }
            return this;
        }

        public Builder metadata(String key, String value) {
            if (key != null && value != null) {
                this.metadata.put(key, value);
            }
            return this;
        }

        public BridgeMessage build() {
            return new BridgeMessage(
                    internalId,
                    sourcePlatform,
                    sourceChannelId,
                    sourceUserId,
                    sourceUserName,
                    minecraftUuid,
                    minecraftName,
                    plainText,
                    formattedText,
                    timestamp,
                    system,
                    replyToMessageId,
                    metadata
            );
        }
    }
}

package me.kprf.komsomolChatBridge.core;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class LoopProtectionService {
    private final int cacheSize;
    private final Duration ttl;
    private final LinkedHashMap<UUID, Instant> processedIds = new LinkedHashMap<>(128, 0.75f, true);

    public LoopProtectionService(int cacheSize, Duration ttl) {
        this.cacheSize = Math.max(1, cacheSize);
        this.ttl = ttl == null ? Duration.ofMinutes(30) : ttl;
    }

    public synchronized boolean markIfNew(BridgeMessage message) {
        Instant now = Instant.now();
        cleanup(now);

        UUID metadataId = parseUuid(message.metadata().get("bridge_internal_id"));
        if (processedIds.containsKey(message.internalId()) || (metadataId != null && processedIds.containsKey(metadataId))) {
            return false;
        }

        processedIds.put(message.internalId(), now);
        if (metadataId != null) {
            processedIds.put(metadataId, now);
        }
        trimToSize();
        return true;
    }

    public synchronized boolean contains(UUID internalId) {
        cleanup(Instant.now());
        return processedIds.containsKey(internalId);
    }

    public synchronized int size() {
        cleanup(Instant.now());
        return processedIds.size();
    }

    private void cleanup(Instant now) {
        Iterator<Map.Entry<UUID, Instant>> iterator = processedIds.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Instant> entry = iterator.next();
            if (Duration.between(entry.getValue(), now).compareTo(ttl) > 0) {
                iterator.remove();
            }
        }
    }

    private void trimToSize() {
        Iterator<UUID> iterator = processedIds.keySet().iterator();
        while (processedIds.size() > cacheSize && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}

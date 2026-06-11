package me.kprf.komsomolChatBridge.storage;

import java.util.Optional;

public interface StorageService extends AutoCloseable {
    void init();

    void saveLinkedAccount(LinkedAccountRecord record);

    Optional<LinkedAccountRecord> findByMinecraftUuid(String minecraftUuid);

    void saveMessageRecord(MessageRecord record);

    void incrementStat(String key);

    long getStat(String key);

    @Override
    void close();
}

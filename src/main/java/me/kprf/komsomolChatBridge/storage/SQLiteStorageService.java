package me.kprf.komsomolChatBridge.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Optional;
import java.util.function.BiConsumer;

public final class SQLiteStorageService implements StorageService {
    private final Path databaseFile;
    private final BiConsumer<String, Throwable> errorLogger;
    private Connection connection;

    public SQLiteStorageService(Path databaseFile, BiConsumer<String, Throwable> errorLogger) {
        this.databaseFile = databaseFile;
        this.errorLogger = errorLogger == null ? (message, throwable) -> { } : errorLogger;
    }

    @Override
    public synchronized void init() {
        try {
            Path parent = databaseFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.toAbsolutePath());
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS linked_accounts (
                          minecraft_uuid TEXT PRIMARY KEY,
                          minecraft_name TEXT,
                          discord_user_id TEXT,
                          telegram_user_id TEXT,
                          created_at TEXT NOT NULL,
                          updated_at TEXT NOT NULL
                        )
                        """);
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS message_map (
                          internal_id TEXT PRIMARY KEY,
                          source_platform TEXT NOT NULL,
                          source_message_id TEXT,
                          discord_message_id TEXT,
                          telegram_message_id TEXT,
                          created_at TEXT NOT NULL
                        )
                        """);
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS stats (
                          key TEXT PRIMARY KEY,
                          value INTEGER NOT NULL
                        )
                        """);
            }
        } catch (SQLException | IOException exception) {
            throw new IllegalStateException("Не удалось открыть SQLite-хранилище " + databaseFile, exception);
        }
    }

    @Override
    public synchronized void saveLinkedAccount(LinkedAccountRecord record) {
        if (record == null || connection == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO linked_accounts(minecraft_uuid, minecraft_name, discord_user_id, telegram_user_id, created_at, updated_at)
                VALUES(?, ?, ?, ?, ?, ?)
                ON CONFLICT(minecraft_uuid) DO UPDATE SET
                  minecraft_name = excluded.minecraft_name,
                  discord_user_id = excluded.discord_user_id,
                  telegram_user_id = excluded.telegram_user_id,
                  updated_at = excluded.updated_at
                """)) {
            statement.setString(1, record.minecraftUuid());
            statement.setString(2, record.minecraftName());
            statement.setString(3, record.discordUserId());
            statement.setString(4, record.telegramUserId());
            statement.setString(5, record.createdAt().toString());
            statement.setString(6, record.updatedAt().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            errorLogger.accept("Не удалось сохранить привязку аккаунта.", exception);
        }
    }

    @Override
    public synchronized Optional<LinkedAccountRecord> findByMinecraftUuid(String minecraftUuid) {
        if (minecraftUuid == null || minecraftUuid.isBlank() || connection == null) {
            return Optional.empty();
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT minecraft_uuid, minecraft_name, discord_user_id, telegram_user_id, created_at, updated_at
                FROM linked_accounts
                WHERE minecraft_uuid = ?
                """)) {
            statement.setString(1, minecraftUuid);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new LinkedAccountRecord(
                        resultSet.getString("minecraft_uuid"),
                        resultSet.getString("minecraft_name"),
                        resultSet.getString("discord_user_id"),
                        resultSet.getString("telegram_user_id"),
                        Instant.parse(resultSet.getString("created_at")),
                        Instant.parse(resultSet.getString("updated_at"))
                ));
            }
        } catch (SQLException exception) {
            errorLogger.accept("Не удалось прочитать привязку аккаунта.", exception);
            return Optional.empty();
        }
    }

    @Override
    public synchronized void saveMessageRecord(MessageRecord record) {
        if (record == null || connection == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO message_map(internal_id, source_platform, source_message_id, discord_message_id, telegram_message_id, created_at)
                VALUES(?, ?, ?, ?, ?, ?)
                ON CONFLICT(internal_id) DO UPDATE SET
                  discord_message_id = CASE
                    WHEN excluded.discord_message_id <> '' THEN excluded.discord_message_id
                    ELSE message_map.discord_message_id
                  END,
                  telegram_message_id = CASE
                    WHEN excluded.telegram_message_id <> '' THEN excluded.telegram_message_id
                    ELSE message_map.telegram_message_id
                  END
                """)) {
            statement.setString(1, record.internalId());
            statement.setString(2, record.sourcePlatform());
            statement.setString(3, record.sourceMessageId());
            statement.setString(4, record.discordMessageId());
            statement.setString(5, record.telegramMessageId());
            statement.setString(6, record.createdAt().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            errorLogger.accept("Не удалось сохранить mapping сообщения.", exception);
        }
    }

    @Override
    public synchronized void incrementStat(String key) {
        if (key == null || key.isBlank() || connection == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO stats(key, value)
                VALUES(?, 1)
                ON CONFLICT(key) DO UPDATE SET value = value + 1
                """)) {
            statement.setString(1, key);
            statement.executeUpdate();
        } catch (SQLException exception) {
            errorLogger.accept("Не удалось обновить статистику " + key + '.', exception);
        }
    }

    @Override
    public synchronized long getStat(String key) {
        if (key == null || key.isBlank() || connection == null) {
            return 0;
        }
        try (PreparedStatement statement = connection.prepareStatement("SELECT value FROM stats WHERE key = ?")) {
            statement.setString(1, key);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong("value") : 0;
            }
        } catch (SQLException exception) {
            errorLogger.accept("Не удалось прочитать статистику " + key + '.', exception);
            return 0;
        }
    }

    @Override
    public synchronized void close() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException exception) {
            errorLogger.accept("Не удалось закрыть SQLite-хранилище.", exception);
        } finally {
            connection = null;
        }
    }
}

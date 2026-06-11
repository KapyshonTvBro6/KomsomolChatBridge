package me.kprf.komsomolChatBridge.discord;

import me.kprf.komsomolChatBridge.config.BridgeConfig;
import me.kprf.komsomolChatBridge.core.MentionService;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class DiscordConsoleLogForwarder {
    private static final int DISCORD_MESSAGE_LIMIT = 1900;
    private static final int MAX_QUEUE_SIZE = 500;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Set<String> DEFAULT_LEVELS = Set.of("INFO", "WARNING", "SEVERE");

    private final JavaPlugin plugin;
    private final Supplier<BridgeConfig> configSupplier;
    private final DiscordBridgeClient discordBridgeClient;
    private final BiConsumer<String, Throwable> errorLogger;
    private final MentionService mentionService = new MentionService();
    private final Queue<String> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queuedLines = new AtomicInteger();
    private volatile ForwardingHandler handler;
    private volatile BukkitTask flushTask;

    public DiscordConsoleLogForwarder(
            JavaPlugin plugin,
            Supplier<BridgeConfig> configSupplier,
            DiscordBridgeClient discordBridgeClient,
            BiConsumer<String, Throwable> errorLogger
    ) {
        this.plugin = plugin;
        this.configSupplier = configSupplier;
        this.discordBridgeClient = discordBridgeClient;
        this.errorLogger = errorLogger == null ? (message, throwable) -> { } : errorLogger;
    }

    public synchronized void start() {
        stop();
        BridgeConfig.DiscordSettings settings = configSupplier.get().discord();
        if (!settings.consoleLogEnabled() || !discordBridgeClient.isConsoleConfigured()) {
            return;
        }

        handler = new ForwardingHandler();
        handler.setLevel(Level.ALL);
        Logger.getLogger("").addHandler(handler);

        long periodTicks = Math.max(1, settings.consoleLogRefreshSeconds()) * 20L;
        flushTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::flush, periodTicks, periodTicks);
    }

    public synchronized void stop() {
        BukkitTask currentTask = flushTask;
        flushTask = null;
        if (currentTask != null) {
            currentTask.cancel();
        }

        ForwardingHandler currentHandler = handler;
        handler = null;
        if (currentHandler != null) {
            Logger.getLogger("").removeHandler(currentHandler);
        }
        queue.clear();
        queuedLines.set(0);
    }

    private void enqueue(String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        if (queuedLines.get() >= MAX_QUEUE_SIZE) {
            queue.poll();
        } else {
            queuedLines.incrementAndGet();
        }
        queue.offer(line);
    }

    private void flush() {
        if (!discordBridgeClient.isConsoleConfigured()) {
            return;
        }

        List<String> lines = new ArrayList<>();
        String line;
        while ((line = queue.poll()) != null) {
            queuedLines.updateAndGet(value -> Math.max(0, value - 1));
            lines.add(line);
            if (lines.size() >= 60) {
                send(lines);
                lines.clear();
            }
        }
        if (!lines.isEmpty()) {
            send(lines);
        }
    }

    private void send(List<String> lines) {
        BridgeConfig.DiscordSettings settings = configSupplier.get().discord();
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            String safeLine = line.replace("```", "'''");
            if (builder.length() + safeLine.length() + 1 > DISCORD_MESSAGE_LIMIT) {
                sendChunk(builder.toString(), settings.consoleLogUseCodeBlock());
                builder.setLength(0);
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(safeLine);
        }
        if (builder.length() > 0) {
            sendChunk(builder.toString(), settings.consoleLogUseCodeBlock());
        }
    }

    private void sendChunk(String text, boolean codeBlock) {
        String payload = codeBlock ? "```log\n" + text + "\n```" : text;
        discordBridgeClient.sendConsoleMessage(payload)
                .exceptionally(throwable -> {
                    errorLogger.accept("Не удалось отправить строку консоли в Discord.", throwable);
                    return null;
                });
    }

    private final class ForwardingHandler extends Handler {
        @Override
        public void publish(LogRecord record) {
            if (record == null || !isLoggable(record) || shouldSkip(record)) {
                return;
            }
            BridgeConfig.DiscordSettings settings = configSupplier.get().discord();
            if (!settings.consoleLogEnabled() || !discordBridgeClient.isConsoleConfigured() || !levelAllowed(record, settings)) {
                return;
            }
            enqueue(format(record, settings));
        }

        @Override
        public void flush() {
            DiscordConsoleLogForwarder.this.flush();
        }

        @Override
        public void close() {
            flush();
        }
    }

    private boolean shouldSkip(LogRecord record) {
        String loggerName = record.getLoggerName();
        if (loggerName == null) {
            loggerName = "";
        }
        return loggerName.startsWith("me.kprf.komsomolChatBridge")
                || loggerName.startsWith("net.dv8tion")
                || loggerName.startsWith("okhttp3")
                || loggerName.startsWith("org.slf4j");
    }

    private boolean levelAllowed(LogRecord record, BridgeConfig.DiscordSettings settings) {
        List<String> configuredLevels = settings.consoleLogLevels();
        Set<String> allowed = configuredLevels == null || configuredLevels.isEmpty()
                ? DEFAULT_LEVELS
                : configuredLevels.stream()
                        .map(DiscordConsoleLogForwarder::normalizeLevel)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return allowed.contains(normalizeLevel(record.getLevel().getName()));
    }

    private static String normalizeLevel(String level) {
        String normalized = level == null ? "" : level.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "WARN" -> "WARNING";
            case "ERROR" -> "SEVERE";
            default -> normalized;
        };
    }

    private String format(LogRecord record, BridgeConfig.DiscordSettings settings) {
        String loggerName = record.getLoggerName() == null ? "Server" : record.getLoggerName();
        int lastDot = loggerName.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < loggerName.length() - 1) {
            loggerName = loggerName.substring(lastDot + 1);
        }

        String message = record.getMessage();
        if (record.getThrown() != null) {
            Throwable thrown = record.getThrown();
            message = message + " (" + thrown.getClass().getSimpleName() + ": " + thrown.getMessage() + ')';
        }
        message = mentionService.sanitizeDiscordMentions(message == null ? "" : message);

        String template = settings.consoleLogFormat() == null || settings.consoleLogFormat().isBlank()
                ? "[{time} {level}] {message}"
                : settings.consoleLogFormat();
        return template
                .replace("{time}", LocalTime.now().format(TIME_FORMAT))
                .replace("{level}", normalizeLevel(record.getLevel().getName()))
                .replace("{logger}", loggerName)
                .replace("{message}", message);
    }
}

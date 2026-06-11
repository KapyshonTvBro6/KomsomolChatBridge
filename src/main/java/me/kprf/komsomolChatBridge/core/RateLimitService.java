package me.kprf.komsomolChatBridge.core;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RateLimitService {
    public record Result(boolean allowed, String reason) {
        public static Result allow() {
            return new Result(true, "");
        }

        public static Result blocked(String reason) {
            return new Result(false, reason);
        }
    }

    private record LastMessage(String normalizedText, Instant timestamp) {
    }

    private volatile boolean enabled;
    private volatile int maxMessages;
    private volatile Duration window;
    private volatile Duration duplicateWindow;
    private final Map<String, Deque<Instant>> messageWindows = new ConcurrentHashMap<>();
    private final Map<String, LastMessage> lastMessages = new ConcurrentHashMap<>();

    public RateLimitService(boolean enabled, int maxMessages, Duration window, Duration duplicateWindow) {
        configure(enabled, maxMessages, window, duplicateWindow);
    }

    public void configure(boolean enabled, int maxMessages, Duration window, Duration duplicateWindow) {
        this.enabled = enabled;
        this.maxMessages = Math.max(1, maxMessages);
        this.window = window == null ? Duration.ofSeconds(10) : window;
        this.duplicateWindow = duplicateWindow == null ? Duration.ofSeconds(15) : duplicateWindow;
    }

    public Result check(BridgeMessage message, boolean bypass) {
        if (!enabled || bypass || message.system()) {
            return Result.allow();
        }

        Instant now = Instant.now();
        String actorKey = message.actorKey();
        String normalizedText = normalize(message.plainText());

        LastMessage last = lastMessages.get(actorKey);
        if (last != null
                && last.normalizedText().equals(normalizedText)
                && Duration.between(last.timestamp(), now).compareTo(duplicateWindow) <= 0) {
            return Result.blocked("duplicate");
        }

        Deque<Instant> deque = messageWindows.computeIfAbsent(actorKey, ignored -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && Duration.between(deque.peekFirst(), now).compareTo(window) > 0) {
                deque.removeFirst();
            }
            if (deque.size() >= maxMessages) {
                return Result.blocked("rate_limit");
            }
            deque.addLast(now);
        }

        lastMessages.put(actorKey, new LastMessage(normalizedText, now));
        return Result.allow();
    }

    private String normalize(String text) {
        return text == null ? "" : text.strip().replaceAll("\\s+", " ").toLowerCase();
    }
}

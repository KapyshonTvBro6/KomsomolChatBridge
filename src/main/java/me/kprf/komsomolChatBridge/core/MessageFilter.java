package me.kprf.komsomolChatBridge.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class MessageFilter {
    private static final Pattern URL_PATTERN = Pattern.compile("(?i)\\b(?:https?://|www\\.)\\S+");
    private static final Pattern LEGACY_COLOR_PATTERN = Pattern.compile("(?i)[§&][0-9A-FK-ORX]");

    public record Settings(
            boolean ignoreEmpty,
            int maxMessageLength,
            boolean allowLinks,
            boolean stripColorsFromExternal,
            List<String> blacklistWords,
            List<String> blacklistRegex
    ) {
        public static Settings defaults() {
            return new Settings(true, 500, true, true, List.of(), List.of());
        }
    }

    public record Result(boolean allowed, String reason, String sanitizedText) {
        public static Result allowed(String sanitizedText) {
            return new Result(true, "", sanitizedText);
        }

        public static Result blocked(String reason, String sanitizedText) {
            return new Result(false, reason, sanitizedText);
        }
    }

    private volatile Settings settings = Settings.defaults();
    private volatile List<Pattern> regexBlacklist = List.of();

    public void configure(Settings settings) {
        this.settings = settings == null ? Settings.defaults() : settings;
        this.regexBlacklist = compileRegex(this.settings.blacklistRegex());
    }

    public Result filter(BridgeMessage message) {
        Settings current = settings;
        String text = message.plainText() == null ? "" : message.plainText();
        if (current.stripColorsFromExternal()) {
            text = stripLegacyColors(text);
        }

        String trimmed = text.strip();
        if (current.ignoreEmpty() && trimmed.isEmpty()) {
            return Result.blocked("empty", trimmed);
        }
        if (current.maxMessageLength() > 0 && trimmed.length() > current.maxMessageLength()) {
            return Result.blocked("too_long", trimmed.substring(0, current.maxMessageLength()));
        }
        if (!current.allowLinks() && URL_PATTERN.matcher(trimmed).find()) {
            return Result.blocked("links_denied", trimmed);
        }

        String lower = trimmed.toLowerCase(Locale.ROOT);
        for (String word : current.blacklistWords()) {
            if (word != null && !word.isBlank() && lower.contains(word.toLowerCase(Locale.ROOT))) {
                return Result.blocked("blacklist_word", trimmed);
            }
        }
        for (Pattern pattern : regexBlacklist) {
            if (pattern.matcher(trimmed).find()) {
                return Result.blocked("blacklist_regex", trimmed);
            }
        }

        return Result.allowed(trimmed);
    }

    public static String stripLegacyColors(String text) {
        return text == null ? "" : LEGACY_COLOR_PATTERN.matcher(text).replaceAll("");
    }

    private List<Pattern> compileRegex(List<String> values) {
        List<Pattern> patterns = new ArrayList<>();
        if (values == null) {
            return List.of();
        }
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            try {
                patterns.add(Pattern.compile(value, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
            } catch (PatternSyntaxException ignored) {
                // Invalid administrator regex must not disable the whole bridge.
            }
        }
        return List.copyOf(patterns);
    }
}

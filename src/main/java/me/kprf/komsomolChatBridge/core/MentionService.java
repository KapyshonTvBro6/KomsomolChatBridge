package me.kprf.komsomolChatBridge.core;

public final class MentionService {
    public String sanitizeDiscordMentions(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text
                .replace("@everyone", "@\u200Beveryone")
                .replace("@here", "@\u200Bhere");
    }

    public String sanitizeTelegramMentions(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.replaceAll("(?<!\\w)@([A-Za-z0-9_]{5,32})", "@\u200B$1");
    }
}

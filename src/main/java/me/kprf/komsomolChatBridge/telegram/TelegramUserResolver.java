package me.kprf.komsomolChatBridge.telegram;

import com.google.gson.JsonObject;

public final class TelegramUserResolver {
    public String displayName(JsonObject from) {
        String username = string(from, "username");
        if (username != null && !username.isBlank()) {
            return '@' + username;
        }

        String firstName = string(from, "first_name");
        String lastName = string(from, "last_name");
        String fullName = ((firstName == null ? "" : firstName) + ' ' + (lastName == null ? "" : lastName)).strip();
        if (!fullName.isBlank()) {
            return fullName;
        }

        return string(from, "id");
    }

    private String string(JsonObject source, String key) {
        return source.has(key) && !source.get(key).isJsonNull() ? source.get(key).getAsString() : null;
    }
}

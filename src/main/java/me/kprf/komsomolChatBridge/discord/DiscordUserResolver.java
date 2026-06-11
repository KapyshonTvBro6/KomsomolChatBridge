package me.kprf.komsomolChatBridge.discord;

import java.util.Optional;

public final class DiscordUserResolver {
    public Optional<String> displayName(String username, String globalName) {
        if (globalName != null && !globalName.isBlank()) {
            return Optional.of(globalName);
        }
        if (username != null && !username.isBlank()) {
            return Optional.of(username);
        }
        return Optional.empty();
    }
}

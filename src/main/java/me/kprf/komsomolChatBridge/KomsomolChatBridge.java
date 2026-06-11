package me.kprf.komsomolChatBridge;

import me.kprf.komsomolChatBridge.api.ChatBridgeApi;

public final class KomsomolChatBridge {
    private static volatile ChatBridgeApi api;

    private KomsomolChatBridge() {
    }

    public static ChatBridgeApi getApi() {
        ChatBridgeApi current = api;
        if (current == null) {
            throw new IllegalStateException("KomsomolChatBridge API ещё не инициализирован.");
        }
        return current;
    }

    static void setApi(ChatBridgeApi api) {
        KomsomolChatBridge.api = api;
    }

    static void clearApi() {
        KomsomolChatBridge.api = null;
    }
}

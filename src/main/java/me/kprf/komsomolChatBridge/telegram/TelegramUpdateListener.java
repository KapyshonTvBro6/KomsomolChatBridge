package me.kprf.komsomolChatBridge.telegram;

import me.kprf.komsomolChatBridge.config.BridgeConfig;
import me.kprf.komsomolChatBridge.core.BridgeMessage;
import me.kprf.komsomolChatBridge.core.BridgePlatform;
import me.kprf.komsomolChatBridge.core.ChatBridgeService;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.function.Supplier;

public final class TelegramUpdateListener {
    private final Supplier<BridgeConfig> configSupplier;
    private final ChatBridgeService chatBridgeService;
    private final TelegramUserResolver userResolver = new TelegramUserResolver();

    public TelegramUpdateListener(Supplier<BridgeConfig> configSupplier, ChatBridgeService chatBridgeService) {
        this.configSupplier = configSupplier;
        this.chatBridgeService = chatBridgeService;
    }

    public void handleUpdate(JsonObject update) {
        if (update == null || !update.has("message") || !update.get("message").isJsonObject()) {
            return;
        }

        BridgeConfig.TelegramSettings settings = configSupplier.get().telegram();
        if (!settings.enabled()) {
            return;
        }

        JsonObject message = update.getAsJsonObject("message");
        JsonObject chat = object(message, "chat");
        if (chat == null || !settings.chatId().equals(valueAsString(chat.get("id")))) {
            return;
        }

        JsonObject from = object(message, "from");
        if (from == null) {
            return;
        }
        if (settings.ignoreBots() && bool(from, "is_bot")) {
            return;
        }

        String text = string(message, "text");
        if ((text == null || text.isBlank()) && message.has("caption")) {
            text = string(message, "caption");
        }
        if (text == null || text.isBlank()) {
            return;
        }
        if (!settings.relayCommands() && text.startsWith("/")) {
            return;
        }

        String userId = valueAsString(from.get("id"));
        String username = userResolver.displayName(from);
        String replyTo = null;
        JsonObject reply = object(message, "reply_to_message");
        if (reply != null && reply.has("message_id")) {
            replyTo = reply.get("message_id").getAsString();
        }

        BridgeMessage bridgeMessage = BridgeMessage.builder(BridgePlatform.TELEGRAM)
                .sourceChannelId(valueAsString(chat.get("id")))
                .sourceUserId(userId)
                .sourceUserName(username)
                .plainText(text)
                .formattedText(text)
                .replyToMessageId(replyTo)
                .metadata("source_message_id", string(message, "message_id"))
                .metadata("telegram_message_id", string(message, "message_id"))
                .build();

        chatBridgeService.publish(bridgeMessage);
    }

    private JsonObject object(JsonObject source, String key) {
        JsonElement element = source.get(key);
        return element == null || !element.isJsonObject() ? null : element.getAsJsonObject();
    }

    private String string(JsonObject source, String key) {
        JsonElement element = source.get(key);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    private boolean bool(JsonObject source, String key) {
        JsonElement element = source.get(key);
        return element != null && !element.isJsonNull() && element.getAsBoolean();
    }

    private String valueAsString(JsonElement element) {
        return element == null || element.isJsonNull() ? "" : element.getAsString();
    }
}

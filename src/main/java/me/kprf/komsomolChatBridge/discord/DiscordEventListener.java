package me.kprf.komsomolChatBridge.discord;

import me.kprf.komsomolChatBridge.config.BridgeConfig;
import me.kprf.komsomolChatBridge.core.BridgeMessage;
import me.kprf.komsomolChatBridge.core.BridgePlatform;
import me.kprf.komsomolChatBridge.core.ChatBridgeService;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.function.Supplier;

public final class DiscordEventListener extends ListenerAdapter {
    private final Supplier<BridgeConfig> configSupplier;
    private final ChatBridgeService chatBridgeService;

    public DiscordEventListener(Supplier<BridgeConfig> configSupplier, ChatBridgeService chatBridgeService) {
        this.configSupplier = configSupplier;
        this.chatBridgeService = chatBridgeService;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        BridgeConfig.DiscordSettings settings = configSupplier.get().discord();
        if (!settings.enabled()) {
            return;
        }
        if (!event.getChannel().getId().equals(settings.channelId())) {
            return;
        }
        Message discordMessage = event.getMessage();
        if (settings.ignoreBots() && event.getAuthor().isBot()) {
            return;
        }
        if (discordMessage.isWebhookMessage()) {
            return;
        }

        String text = discordMessage.getContentDisplay();
        if (settings.relayAttachments() && !discordMessage.getAttachments().isEmpty()) {
            StringBuilder builder = new StringBuilder(text == null ? "" : text);
            for (Message.Attachment attachment : discordMessage.getAttachments()) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(attachment.getUrl());
            }
            text = builder.toString();
        }
        if (text == null || text.isBlank()) {
            return;
        }

        String displayName = event.getMember() == null
                ? event.getAuthor().getName()
                : event.getMember().getEffectiveName();
        Message referenced = discordMessage.getReferencedMessage();

        BridgeMessage bridgeMessage = BridgeMessage.builder(BridgePlatform.DISCORD)
                .sourceChannelId(event.getChannel().getId())
                .sourceUserId(event.getAuthor().getId())
                .sourceUserName(displayName)
                .plainText(text)
                .formattedText(text)
                .replyToMessageId(referenced == null ? null : referenced.getId())
                .metadata("source_message_id", discordMessage.getId())
                .metadata("discord_message_id", discordMessage.getId())
                .build();

        chatBridgeService.publish(bridgeMessage);
    }
}

package me.kprf.komsomolChatBridge.discord;

import me.kprf.komsomolChatBridge.config.BridgeConfig;
import me.kprf.komsomolChatBridge.core.BridgeMessage;
import me.kprf.komsomolChatBridge.core.BridgePlatform;
import me.kprf.komsomolChatBridge.core.ChatBridgeService;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Supplier;

public final class DiscordEventListener extends ListenerAdapter {
    private final JavaPlugin plugin;
    private final Supplier<BridgeConfig> configSupplier;
    private final ChatBridgeService chatBridgeService;

    public DiscordEventListener(JavaPlugin plugin, Supplier<BridgeConfig> configSupplier, ChatBridgeService chatBridgeService) {
        this.plugin = plugin;
        this.configSupplier = configSupplier;
        this.chatBridgeService = chatBridgeService;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        BridgeConfig.DiscordSettings settings = configSupplier.get().discord();
        if (!settings.enabled()) {
            return;
        }
        Message discordMessage = event.getMessage();
        if (settings.ignoreBots() && event.getAuthor().isBot()) {
            return;
        }
        if (discordMessage.isWebhookMessage()) {
            return;
        }

        String eventChannelId = event.getChannel().getId();
        if (isSameChannel(eventChannelId, settings.consoleChannelId())) {
            handleConsoleChannelMessage(settings, discordMessage);
            return;
        }
        if (!isSameChannel(eventChannelId, settings.channelId())) {
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

    private void handleConsoleChannelMessage(BridgeConfig.DiscordSettings settings, Message discordMessage) {
        if (!settings.consoleExecuteCommands()) {
            return;
        }

        String commandLine = discordMessage.getContentRaw();
        if (commandLine == null || commandLine.isBlank()) {
            return;
        }

        String prefix = settings.consoleCommandPrefix();
        if (prefix != null && !prefix.isBlank()) {
            if (!commandLine.startsWith(prefix)) {
                return;
            }
            commandLine = commandLine.substring(prefix.length()).trim();
        }

        if (commandLine.startsWith("/")) {
            commandLine = commandLine.substring(1).trim();
        }
        if (commandLine.isBlank()) {
            return;
        }

        String finalCommandLine = commandLine;
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommandLine));
    }

    private boolean isSameChannel(String eventChannelId, String configuredChannelId) {
        return configuredChannelId != null
                && !configuredChannelId.isBlank()
                && eventChannelId.equals(configuredChannelId);
    }
}

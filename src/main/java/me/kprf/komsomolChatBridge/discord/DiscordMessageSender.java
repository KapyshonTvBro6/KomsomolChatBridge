package me.kprf.komsomolChatBridge.discord;

import me.kprf.komsomolChatBridge.config.BridgeConfig;
import me.kprf.komsomolChatBridge.core.BridgeMessage;
import me.kprf.komsomolChatBridge.core.BridgeOutboundSender;
import me.kprf.komsomolChatBridge.core.BridgePlatform;

import net.dv8tion.jda.api.entities.MessageEmbed;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class DiscordMessageSender implements BridgeOutboundSender {
    private final Supplier<BridgeConfig> configSupplier;
    private final DiscordBridgeClient discordBridgeClient;
    private final DiscordWebhookSender webhookSender;

    public DiscordMessageSender(
            Supplier<BridgeConfig> configSupplier,
            DiscordBridgeClient discordBridgeClient,
            DiscordWebhookSender webhookSender
    ) {
        this.configSupplier = configSupplier;
        this.discordBridgeClient = discordBridgeClient;
        this.webhookSender = webhookSender;
    }

    @Override
    public CompletableFuture<String> send(BridgeMessage message) {
        BridgeConfig.DiscordSettings settings = configSupplier.get().discord();
        if (message.sourcePlatform() == BridgePlatform.SYSTEM) {
            MessageEmbed embed = DiscordSystemEmbedFactory.create(message, settings);
            if (embed != null) {
                return discordBridgeClient.sendEmbed(embed);
            }
        }
        if (message.sourcePlatform() == BridgePlatform.MINECRAFT
                && settings.useWebhookForMinecraftMessages()
                && webhookSender.isConfigured()) {
            return webhookSender.send(message);
        }
        return discordBridgeClient.sendBotMessage(message.formattedText());
    }

    @Override
    public boolean isAvailable() {
        BridgeConfig.DiscordSettings settings = configSupplier.get().discord();
        return settings.enabled() && (discordBridgeClient.isConfigured() || webhookSender.isConfigured());
    }
}

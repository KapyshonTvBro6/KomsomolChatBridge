package me.kprf.komsomolChatBridge.minecraft;

import me.kprf.komsomolChatBridge.config.BridgeConfig;
import me.kprf.komsomolChatBridge.core.BridgeMessage;
import me.kprf.komsomolChatBridge.core.BridgePlatform;
import me.kprf.komsomolChatBridge.core.ChatBridgeService;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.function.Supplier;

public final class MinecraftChatListener implements Listener {
    private final Supplier<BridgeConfig> configSupplier;
    private final ChatBridgeService chatBridgeService;

    public MinecraftChatListener(Supplier<BridgeConfig> configSupplier, ChatBridgeService chatBridgeService) {
        this.configSupplier = configSupplier;
        this.chatBridgeService = chatBridgeService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        BridgeConfig config = configSupplier.get();
        BridgeConfig.MinecraftSettings settings = config.minecraft();
        if (!settings.enabled() || !settings.relayChat()) {
            return;
        }

        Player player = event.getPlayer();
        String sendPermission = settings.permissionSendToBridge();
        if (sendPermission != null && !sendPermission.isBlank() && !player.hasPermission(sendPermission)) {
            return;
        }

        if (settings.cancelOriginalChat()) {
            event.setCancelled(true);
        }

        String plainText = PlainTextComponentSerializer.plainText().serialize(event.message());
        boolean bypassRateLimit = settings.permissionAntispamBypass() != null
                && !settings.permissionAntispamBypass().isBlank()
                && player.hasPermission(settings.permissionAntispamBypass());

        BridgeMessage message = BridgeMessage.builder(BridgePlatform.MINECRAFT)
                .sourceChannelId("minecraft")
                .sourceUserId(player.getUniqueId().toString())
                .sourceUserName(player.getName())
                .minecraftUuid(player.getUniqueId())
                .minecraftName(player.getName())
                .plainText(plainText)
                .formattedText(plainText)
                .metadata("rate_limit_bypass", Boolean.toString(bypassRateLimit))
                .metadata("source_message_id", player.getUniqueId() + ":" + System.nanoTime())
                .build();

        chatBridgeService.publish(message);
    }
}

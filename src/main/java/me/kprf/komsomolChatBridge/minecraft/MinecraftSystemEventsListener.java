package me.kprf.komsomolChatBridge.minecraft;

import me.kprf.komsomolChatBridge.config.BridgeConfig;
import me.kprf.komsomolChatBridge.config.MessagesConfig;
import me.kprf.komsomolChatBridge.core.BridgeMessage;
import me.kprf.komsomolChatBridge.core.BridgePlatform;
import me.kprf.komsomolChatBridge.core.ChatBridgeService;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.function.Supplier;

public final class MinecraftSystemEventsListener implements Listener {
    private final Supplier<BridgeConfig> configSupplier;
    private final MessagesConfig messagesConfig;
    private final ChatBridgeService chatBridgeService;

    public MinecraftSystemEventsListener(
            Supplier<BridgeConfig> configSupplier,
            MessagesConfig messagesConfig,
            ChatBridgeService chatBridgeService
    ) {
        this.configSupplier = configSupplier;
        this.messagesConfig = messagesConfig;
        this.chatBridgeService = chatBridgeService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        BridgeConfig config = configSupplier.get();
        if (!config.minecraft().relayJoinLeave() || !config.systemEvents().join()) {
            return;
        }
        sendExternalSystem("join", Map.of("player", event.getPlayer().getName()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        BridgeConfig config = configSupplier.get();
        if (!config.minecraft().relayJoinLeave() || !config.systemEvents().quit()) {
            return;
        }
        sendExternalSystem("quit", Map.of("player", event.getPlayer().getName()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        BridgeConfig config = configSupplier.get();
        if (!config.minecraft().relayDeath() || !config.systemEvents().death()) {
            return;
        }
        Component deathComponent = event.deathMessage();
        String deathMessage = deathComponent == null
                ? event.getEntity().getName() + " погиб."
                : PlainTextComponentSerializer.plainText().serialize(deathComponent);
        sendExternalSystem("death", Map.of(
                "player", event.getEntity().getName(),
                "death_message", deathMessage
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        BridgeConfig config = configSupplier.get();
        if (!config.minecraft().relayAdvancements() || !config.systemEvents().advancement()) {
            return;
        }
        Player player = event.getPlayer();
        Advancement advancement = event.getAdvancement();
        sendExternalSystem("advancement", Map.of(
                "player", player.getName(),
                "advancement", advancement.getKey().toString()
        ));
    }

    private void sendExternalSystem(String key, Map<String, String> placeholders) {
        Map<String, String> values = addServer(placeholders);
        String message = messagesConfig.systemMessage(key, values);
        BridgeMessage.Builder builder = BridgeMessage.builder(BridgePlatform.SYSTEM)
                .sourceUserName("Minecraft")
                .plainText(message)
                .formattedText(message)
                .system(true)
                .metadata("external_only", "true")
                .metadata("system_key", key)
                .metadata("source_message_id", "system:" + key + ':' + System.nanoTime());
        values.forEach(builder::metadata);
        chatBridgeService.publish(builder.build());
    }

    private Map<String, String> addServer(Map<String, String> placeholders) {
        java.util.LinkedHashMap<String, String> values = new java.util.LinkedHashMap<>(placeholders);
        values.put("server", configSupplier.get().general().serverName());
        return values;
    }
}

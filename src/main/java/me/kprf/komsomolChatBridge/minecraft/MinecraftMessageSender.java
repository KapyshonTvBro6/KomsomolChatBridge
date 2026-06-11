package me.kprf.komsomolChatBridge.minecraft;

import me.kprf.komsomolChatBridge.config.BridgeConfig;
import me.kprf.komsomolChatBridge.core.BridgeMessage;
import me.kprf.komsomolChatBridge.core.BridgeOutboundSender;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class MinecraftMessageSender implements BridgeOutboundSender {
    private final JavaPlugin plugin;
    private final Supplier<BridgeConfig> configSupplier;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public MinecraftMessageSender(JavaPlugin plugin, Supplier<BridgeConfig> configSupplier) {
        this.plugin = plugin;
        this.configSupplier = configSupplier;
    }

    @Override
    public CompletableFuture<String> send(BridgeMessage message) {
        CompletableFuture<String> future = new CompletableFuture<>();
        Runnable task = () -> {
            try {
                Component component = miniMessage.deserialize(message.formattedText());
                BridgeConfig.MinecraftSettings settings = configSupplier.get().minecraft();
                String receivePermission = settings.permissionReceiveExternal();

                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!settings.privateReceiveMode()
                            || receivePermission == null
                            || receivePermission.isBlank()
                            || player.hasPermission(receivePermission)) {
                        player.sendMessage(component);
                    }
                }

                String plain = PlainTextComponentSerializer.plainText().serialize(component);
                Bukkit.getConsoleSender().sendMessage(plain);
                future.complete("");
            } catch (RuntimeException exception) {
                future.completeExceptionally(exception);
            }
        };

        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
        return future;
    }

    @Override
    public boolean isAvailable() {
        return configSupplier.get().minecraft().enabled();
    }
}

package me.kprf.komsomolChatBridge.minecraft;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

public final class MinecraftPlayerResolver {
    public Optional<String> nameByUuid(UUID uuid) {
        if (uuid == null) {
            return Optional.empty();
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        return Optional.ofNullable(player.getName());
    }

    public String applyPlaceholderApi(Player player, String text) {
        Plugin placeholderApi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
        if (placeholderApi == null || !placeholderApi.isEnabled() || player == null || text == null) {
            return text;
        }
        try {
            Class<?> apiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Method method = apiClass.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
            Object result = method.invoke(null, player, text);
            return result instanceof String string ? string : text;
        } catch (ReflectiveOperationException exception) {
            return text;
        }
    }
}

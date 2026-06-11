package me.kprf.komsomolChatBridge.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.Map;

public final class MessagesConfig {
    private final JavaPlugin plugin;
    private final File file;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private FileConfiguration configuration;

    public MessagesConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        reload();
    }

    public void reload() {
        this.configuration = YamlConfiguration.loadConfiguration(file);
    }

    public String raw(String path) {
        return configuration.getString(path, path);
    }

    public String format(String path, Map<String, String> placeholders) {
        return applyPlaceholders(raw(path), placeholders);
    }

    public Component component(String path) {
        return component(path, Map.of());
    }

    public Component component(String path, Map<String, String> placeholders) {
        String prefix = raw("prefix");
        String message = format(path, placeholders);
        return miniMessage.deserialize(prefix + message);
    }

    public Component rawComponent(String value) {
        return miniMessage.deserialize(value == null ? "" : value);
    }

    public List<String> stringList(String path) {
        return configuration.getStringList(path);
    }

    public void send(CommandSender sender, String path) {
        send(sender, path, Map.of());
    }

    public void send(CommandSender sender, String path, Map<String, String> placeholders) {
        sender.sendMessage(component(path, placeholders));
    }

    public String systemMessage(String path, Map<String, String> placeholders) {
        return format("system." + path, placeholders);
    }

    public String blockedReason(String reason) {
        return raw("blocked." + reason);
    }

    private String applyPlaceholders(String value, Map<String, String> placeholders) {
        String result = value == null ? "" : value;
        if (placeholders == null) {
            return result;
        }
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace('{' + entry.getKey() + '}', entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }
}

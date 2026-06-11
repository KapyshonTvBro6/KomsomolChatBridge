package me.kprf.komsomolChatBridge.commands;

import me.kprf.komsomolChatBridge.KomsomolChatBridgePlugin;
import me.kprf.komsomolChatBridge.config.MessagesConfig;
import me.kprf.komsomolChatBridge.core.BridgePlatform;
import me.kprf.komsomolChatBridge.core.BridgeStats;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BridgeCommand implements CommandExecutor, TabCompleter {
    private final KomsomolChatBridgePlugin plugin;

    public BridgeCommand(KomsomolChatBridgePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            sendHelp(sender);
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> status(sender);
            case "reload" -> reload(sender);
            case "test" -> test(sender, args);
            case "mute" -> mute(sender, args, true);
            case "unmute" -> mute(sender, args, false);
            case "link" -> link(sender, args);
            case "unlink" -> unlink(sender);
            default -> {
                sendHelp(sender);
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("status", "reload", "test", "mute", "unmute", "link", "unlink", "help"), args[0]);
        }
        if (args.length == 2 && List.of("test", "mute", "unmute", "link").contains(args[0].toLowerCase(Locale.ROOT))) {
            return filter(List.of("discord", "telegram"), args[1]);
        }
        return List.of();
    }

    private boolean status(CommandSender sender) {
        if (!has(sender, "komsomolbridge.status")) {
            return true;
        }
        MessagesConfig messages = plugin.messagesConfig();
        BridgeStats stats = plugin.chatBridgeService().stats();

        sender.sendMessage(messages.component("status.header"));
        sender.sendMessage(messages.component("status.discord", Map.of(
                "enabled", Boolean.toString(plugin.bridgeConfig().discord().enabled()),
                "connected", Boolean.toString(plugin.discordBridgeClient().isConnected()),
                "muted", Boolean.toString(plugin.chatBridgeService().router().isMuted(BridgePlatform.DISCORD))
        )));
        sender.sendMessage(messages.component("status.telegram", Map.of(
                "enabled", Boolean.toString(plugin.bridgeConfig().telegram().enabled()),
                "connected", Boolean.toString(plugin.telegramBridgeClient().isConnected()),
                "muted", Boolean.toString(plugin.chatBridgeService().router().isMuted(BridgePlatform.TELEGRAM))
        )));
        sender.sendMessage(messages.component("status.counters", Map.of(
                "processed", Long.toString(stats.processedMessages()),
                "sent", Long.toString(stats.sentMessages()),
                "spam", Long.toString(stats.blockedByAntispam()),
                "loop", Long.toString(stats.blockedByLoopProtection()),
                "filter", Long.toString(stats.blockedByFilter())
        )));
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!has(sender, "komsomolbridge.reload")) {
            return true;
        }
        plugin.reloadBridge();
        plugin.messagesConfig().send(sender, "commands.reloaded");
        return true;
    }

    private boolean test(CommandSender sender, String[] args) {
        if (!has(sender, "komsomolbridge.admin")) {
            return true;
        }
        BridgePlatform platform = platformArg(sender, args);
        if (platform == null) {
            return true;
        }

        if (platform == BridgePlatform.DISCORD) {
            plugin.chatBridgeService().sendDirect(platform, plugin.messagesConfig().raw("commands.test_discord"));
        } else {
            plugin.chatBridgeService().sendDirect(platform, plugin.messagesConfig().raw("commands.test_telegram"));
        }
        plugin.messagesConfig().send(sender, "commands.test_sent", Map.of("platform", platform.displayName()));
        return true;
    }

    private boolean mute(CommandSender sender, String[] args, boolean mute) {
        if (!has(sender, "komsomolbridge.mute")) {
            return true;
        }
        BridgePlatform platform = platformArg(sender, args);
        if (platform == null) {
            return true;
        }

        if (mute) {
            plugin.chatBridgeService().router().mute(platform);
            plugin.messagesConfig().send(sender, "commands.muted", Map.of("platform", platform.displayName()));
        } else {
            plugin.chatBridgeService().router().unmute(platform);
            plugin.messagesConfig().send(sender, "commands.unmuted", Map.of("platform", platform.displayName()));
        }
        return true;
    }

    private boolean link(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.messagesConfig().send(sender, "commands.player_only");
            return true;
        }
        BridgePlatform platform = platformArg(sender, args);
        if (platform == null) {
            return true;
        }
        plugin.messagesConfig().send(sender, "commands.link_stub", Map.of("platform", platform.displayName()));
        return true;
    }

    private boolean unlink(CommandSender sender) {
        if (!(sender instanceof Player)) {
            plugin.messagesConfig().send(sender, "commands.player_only");
            return true;
        }
        plugin.messagesConfig().send(sender, "commands.unlink_stub");
        return true;
    }

    private BridgePlatform platformArg(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.messagesConfig().send(sender, "commands.unknown_platform");
            return null;
        }
        return BridgePlatform.externalFromConfigName(args[1]).orElseGet(() -> {
            plugin.messagesConfig().send(sender, "commands.unknown_platform");
            return null;
        });
    }

    private boolean has(CommandSender sender, String permission) {
        if (sender.hasPermission(permission) || sender.hasPermission("komsomolbridge.admin")) {
            return true;
        }
        plugin.messagesConfig().send(sender, "commands.no_permission");
        return false;
    }

    private void sendHelp(CommandSender sender) {
        MessagesConfig messages = plugin.messagesConfig();
        for (String line : messages.stringList("commands.help")) {
            sender.sendMessage(messages.rawComponent(messages.raw("prefix") + line));
        }
    }

    private List<String> filter(List<String> values, String prefix) {
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value.startsWith(lowerPrefix)) {
                result.add(value);
            }
        }
        return result;
    }
}

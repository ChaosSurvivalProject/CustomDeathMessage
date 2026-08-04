package com.customdeath.command;

import com.customdeath.CustomDeathMessages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.List;

/**
 * /customdeath 命令处理器。
 * <p>
 * 子命令：
 * <ul>
 *   <li>reload  — 重新加载配置</li>
 *   <li>version — 查看插件版本</li>
 * </ul>
 */
public class ReloadCommand implements TabExecutor {

    private final CustomDeathMessages plugin;

    public ReloadCommand(CustomDeathMessages plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                if (!sender.hasPermission("customdeath.reload")) {
                    sender.sendMessage(Component.text("你没有权限执行该命令", NamedTextColor.RED));
                    return true;
                }
                plugin.getConfigManager().loadConfig();
                sender.sendMessage(Component.text("✔ 配置已重新加载", NamedTextColor.GREEN));
            }
            case "version" -> {
                sender.sendMessage(Component.text(
                        "CustomDeathMessages v" + plugin.getPluginMeta().getVersion(),
                        NamedTextColor.AQUA));
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return List.of("reload", "version").stream()
                    .filter(s -> s.startsWith(input))
                    .toList();
        }
        return List.of();
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("===== CustomDeathMessages =====", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/customdeath reload", NamedTextColor.YELLOW)
                .append(Component.text(" - 重载配置", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/customdeath version", NamedTextColor.YELLOW)
                .append(Component.text(" - 查看版本", NamedTextColor.GRAY)));
    }
}

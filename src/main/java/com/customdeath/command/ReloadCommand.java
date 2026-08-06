package com.customdeath.command;

import com.customdeath.CustomDeathMessages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * /customdeath 命令处理器。
 * <p>
 * 子命令：
 * <ul>
 *   <li>reload  — 重新加载配置</li>
 *   <li>version — 查看插件版本</li>
 *   <li>scroll  — 发放护身/回城/回溯卷轴（需权限 customdeath.scroll）</li>
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
            case "scroll" -> handleScroll(sender, args);
            default -> sendHelp(sender);
        }
        return true;
    }

    /**
     * /customdeath scroll <protect|town|recall> <玩家> [数量] — 发放护身/回城/回溯卷轴。
     */
    private void handleScroll(CommandSender sender, String[] args) {
        if (!sender.hasPermission("customdeath.scroll")) {
            sender.sendMessage(Component.text("你没有权限执行该命令", NamedTextColor.RED));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text("用法：/customdeath scroll <protect|town|recall> <玩家> [数量]", NamedTextColor.YELLOW));
            return;
        }

        String type = args[1].toLowerCase();
        if (!"protect".equals(type) && !"town".equals(type) && !"recall".equals(type)) {
            sender.sendMessage(Component.text("卷轴类型只能是 protect / town / recall", NamedTextColor.RED));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(Component.text("玩家 " + args[2] + " 不在线", NamedTextColor.RED));
            return;
        }

        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("数量必须是整数", NamedTextColor.RED));
                return;
            }
            if (amount <= 0 || amount > 64) {
                sender.sendMessage(Component.text("数量需在 1~64 之间", NamedTextColor.RED));
                return;
            }
        }

        ItemStack scroll;
        if ("protect".equals(type)) {
            scroll = plugin.getConfigManager().buildScrollProtect(amount);
        } else if ("town".equals(type)) {
            scroll = plugin.getConfigManager().buildScrollTown(amount);
        } else {
            scroll = plugin.getConfigManager().buildScrollRecall(amount);
        }
        target.getInventory().addItem(scroll);
        String scrollName = switch (type) {
            case "protect" -> "护身卷轴";
            case "town" -> "回城卷轴";
            default -> "回溯卷轴";
        };
        target.sendMessage(Component.text("你获得了" + scrollName + " ×" + amount, NamedTextColor.GREEN));
        sender.sendMessage(Component.text("已发放" + scrollName + " ×" + amount + " 给 " + target.getName(), NamedTextColor.GREEN));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return List.of("reload", "version", "scroll").stream()
                    .filter(s -> s.startsWith(input))
                    .toList();
        }
        if (args.length == 2) {
            String input = args[1].toLowerCase();
            if ("scroll".equalsIgnoreCase(args[0])) {
                return List.of("protect", "town", "recall").stream()
                        .filter(s -> s.startsWith(input))
                        .toList();
            }
        }
        if (args.length == 3 && "scroll".equalsIgnoreCase(args[0])) {
            return playerNames(args[2].toLowerCase());
        }
        return List.of();
    }

    /** 按前缀补全在线玩家名。 */
    private List<String> playerNames(String prefix) {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(prefix))
                .toList();
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("===== CustomDeathMessages =====", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/customdeath reload", NamedTextColor.YELLOW)
                .append(Component.text(" - 重载配置", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/customdeath version", NamedTextColor.YELLOW)
                .append(Component.text(" - 查看版本", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/customdeath scroll <protect|town|recall> <玩家> [数量]", NamedTextColor.YELLOW)
                .append(Component.text(" - 发放护身/回城/回溯卷轴", NamedTextColor.GRAY)));
    }
}

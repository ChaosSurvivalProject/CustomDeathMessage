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
 *   <li>ticket  — 发放死亡保护券（需权限 customdeath.ticket）</li>
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
            case "ticket" -> handleTicket(sender, args);
            case "scroll" -> handleScroll(sender, args);
            default -> sendHelp(sender);
        }
        return true;
    }

    /**
     * /customdeath ticket <玩家> [数量] — 发放死亡保护券。
     */
    private void handleTicket(CommandSender sender, String[] args) {
        if (!sender.hasPermission("customdeath.ticket")) {
            sender.sendMessage(Component.text("你没有权限执行该命令", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("用法：/customdeath ticket <玩家> [数量]", NamedTextColor.YELLOW));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("玩家 " + args[1] + " 不在线", NamedTextColor.RED));
            return;
        }

        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("数量必须是整数", NamedTextColor.RED));
                return;
            }
            if (amount <= 0 || amount > 64) {
                sender.sendMessage(Component.text("数量需在 1~64 之间", NamedTextColor.RED));
                return;
            }
        }

        ItemStack ticket = plugin.getConfigManager().buildDeathTicket(amount);
        target.getInventory().addItem(ticket);
        target.sendMessage(Component.text("你获得了死亡保护券 ×" + amount, NamedTextColor.GREEN));
        sender.sendMessage(Component.text("已发放死亡保护券 ×" + amount + " 给 " + target.getName(), NamedTextColor.GREEN));
    }

    /**
     * /customdeath scroll <town|recall> <玩家> [数量] — 发放回城/回溯卷轴。
     */
    private void handleScroll(CommandSender sender, String[] args) {
        if (!sender.hasPermission("customdeath.scroll")) {
            sender.sendMessage(Component.text("你没有权限执行该命令", NamedTextColor.RED));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text("用法：/customdeath scroll <town|recall> <玩家> [数量]", NamedTextColor.YELLOW));
            return;
        }

        String type = args[1].toLowerCase();
        if (!"town".equals(type) && !"recall".equals(type)) {
            sender.sendMessage(Component.text("卷轴类型只能是 town 或 recall", NamedTextColor.RED));
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

        ItemStack scroll = "town".equals(type)
                ? plugin.getConfigManager().buildScrollTown(amount)
                : plugin.getConfigManager().buildScrollRecall(amount);
        target.getInventory().addItem(scroll);
        String scrollName = "town".equals(type) ? "回城卷轴" : "回溯卷轴";
        target.sendMessage(Component.text("你获得了" + scrollName + " ×" + amount, NamedTextColor.GREEN));
        sender.sendMessage(Component.text("已发放" + scrollName + " ×" + amount + " 给 " + target.getName(), NamedTextColor.GREEN));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return List.of("reload", "version", "ticket", "scroll").stream()
                    .filter(s -> s.startsWith(input))
                    .toList();
        }
        if (args.length == 2) {
            String input = args[1].toLowerCase();
            if ("ticket".equalsIgnoreCase(args[0])) {
                // 补全在线玩家名
                return playerNames(input);
            }
            if ("scroll".equalsIgnoreCase(args[0])) {
                return List.of("town", "recall").stream()
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
        sender.sendMessage(Component.text("/customdeath ticket <玩家> [数量]", NamedTextColor.YELLOW)
                .append(Component.text(" - 发放死亡保护券", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/customdeath scroll <town|recall> <玩家> [数量]", NamedTextColor.YELLOW)
                .append(Component.text(" - 发放回城/回溯卷轴", NamedTextColor.GRAY)));
    }
}

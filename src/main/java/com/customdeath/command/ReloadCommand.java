package com.customdeath.command;

import com.customdeath.CustomDeathMessages;
import com.customdeath.config.ConfigManager;
import com.customdeath.data.CheckinManager;
import com.customdeath.recipe.RecipeManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /customdeath 命令处理器。
 * <p>
 * 子命令：
 * <ul>
 *   <li>reload  — 重新加载配置（需权限 customdeath.reload）</li>
 *   <li>version — 查看插件版本</li>
 *   <li>scroll  — 发放护身/回城/回溯卷轴（需权限 customdeath.scroll）</li>
 *   <li>recipe  — 查看卷轴合成配方（所有玩家可用，配合基岩版配方书不显示问题）</li>
 *   <li>checkin — 每日签到领取卷轴奖励（所有玩家可用，每天一次，0 点重置）</li>
 * </ul>
 */
public class ReloadCommand implements TabExecutor {

    private final CustomDeathMessages plugin;
    private final RecipeManager recipeManager;
    private final CheckinManager checkinManager;

    public ReloadCommand(CustomDeathMessages plugin, RecipeManager recipeManager, CheckinManager checkinManager) {
        this.plugin = plugin;
        this.recipeManager = recipeManager;
        this.checkinManager = checkinManager;
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
            case "recipe" -> {
                // 配方展示需要玩家上下文（聊天栏展示），控制台无意义
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("该命令只能由玩家执行", NamedTextColor.RED));
                    return true;
                }
                recipeManager.sendRecipeOverview(player);
            }
            case "checkin" -> {
                // 签到需要玩家上下文（发物品），控制台无意义
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("该命令只能由玩家执行", NamedTextColor.RED));
                    return true;
                }
                handleCheckin(player);
            }
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
            sender.sendMessage(Component.text("用法：/cdm scroll <protect|town|recall> <玩家> [数量]", NamedTextColor.YELLOW));
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

    /**
     * /customdeath checkin — 每日签到领取卷轴奖励。
     * <p>
     * 同一天不可重复签到（{@link CheckinManager} 按自然日判断，跨天 0 点自动重置）；
     * 奖励数量取自 {@code settings.checkin.rewards}。
     * 发放采用「先发放、放不下则整体回滚」策略：addItem 存在剩余时，
     * 将已放入背包的部分按原始数量移除，保证要么全发要么全不发，
     * 并向玩家提示腾出背包空间（不标记签到，可稍后重试）。
     */
    private void handleCheckin(Player player) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isCheckinEnabled()) {
            player.sendMessage(Component.text("签到功能未启用", NamedTextColor.RED));
            return;
        }

        UUID uuid = player.getUniqueId();
        if (checkinManager.hasCheckedInToday(uuid)) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(cfg.getCheckinAlreadyMessage()));
            return;
        }

        // 构建奖励列表（数量 > 0 的卷轴）与奖励描述文本
        List<ItemStack> rewards = new ArrayList<>();
        List<String> rewardNames = new ArrayList<>();
        addReward(rewards, rewardNames, cfg, "protect", "护身卷轴");
        addReward(rewards, rewardNames, cfg, "town", "回城卷轴");
        addReward(rewards, rewardNames, cfg, "recall", "回溯卷轴");
        if (rewards.isEmpty()) {
            player.sendMessage(Component.text("签到奖励未配置任何卷轴（settings.checkin.rewards）", NamedTextColor.RED));
            return;
        }

        // 先发放；addItem 会修改传入 ItemStack 的数量（剩余未放入量），故提前克隆一份用于回滚
        ItemStack[] toGive = rewards.toArray(new ItemStack[0]);
        ItemStack[] rollbackBackup = new ItemStack[toGive.length];
        for (int i = 0; i < toGive.length; i++) {
            rollbackBackup[i] = toGive[i].clone();
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(toGive);

        // 存在放不下的部分：回滚已放入的物品（按原始数量移除），整体拒绝签到
        if (!leftover.isEmpty()) {
            for (ItemStack backup : rollbackBackup) {
                player.getInventory().removeItem(backup);
            }
            player.sendMessage(Component.text("背包空间不足，请腾出空间后再签到", NamedTextColor.RED));
            return;
        }

        checkinManager.markCheckedIn(uuid);
        String desc = String.join("、", rewardNames);
        player.sendMessage(MiniMessage.miniMessage()
                .deserialize(cfg.getCheckinSuccessMessage().replace("{rewards}", desc)));
    }

    /**
     * 将一种卷轴奖励加入列表（数量 > 0 时）。
     *
     * @param rewards   奖励物品列表（out 参数）
     * @param rewardNames 奖励描述文本列表（out 参数）
     * @param cfg       配置管理器
     * @param type      卷轴类型：protect / town / recall
     * @param cnName    卷轴中文名（用于描述文本）
     */
    private void addReward(List<ItemStack> rewards, List<String> rewardNames,
                           ConfigManager cfg, String type, String cnName) {
        int amount = cfg.getCheckinRewardAmount(type);
        if (amount <= 0) {
            return;
        }
        ItemStack scroll = switch (type) {
            case "protect" -> cfg.buildScrollProtect(amount);
            case "town" -> cfg.buildScrollTown(amount);
            default -> cfg.buildScrollRecall(amount);
        };
        rewards.add(scroll);
        rewardNames.add(cnName + "×" + amount);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return List.of("reload", "version", "scroll", "recipe", "checkin").stream()
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
        sender.sendMessage(Component.text("/cdm reload", NamedTextColor.YELLOW)
                .append(Component.text(" - 重载配置", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/cdm version", NamedTextColor.YELLOW)
                .append(Component.text(" - 查看版本", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/cdm recipe", NamedTextColor.YELLOW)
                .append(Component.text(" - 查看卷轴合成配方", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/cdm checkin", NamedTextColor.YELLOW)
                .append(Component.text(" - 每日签到领取卷轴奖励（每天一次）", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/cdm scroll <protect|town|recall> <玩家> [数量]", NamedTextColor.YELLOW)
                .append(Component.text(" - 发放护身/回城/回溯卷轴", NamedTextColor.GRAY)));
    }
}

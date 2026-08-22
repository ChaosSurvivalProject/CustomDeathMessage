package com.customdeath.command;

import com.customdeath.CustomDeathMessages;
import com.customdeath.config.ConfigManager;
import com.customdeath.data.CheckinManager;
import com.customdeath.data.SinManager;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * /customdeath 命令处理器。
 * <p>
 * 子命令：
 * <ul>
 *   <li>reload  — 重新加载配置（需权限 customdeath.reload）</li>
 *   <li>version — 查看插件版本</li>
 *   <li>scroll  — 发放护身/回城/回溯卷轴（需权限 customdeath.scroll）</li>
 *   <li>recipe  — 查看卷轴合成配方（所有玩家可用，配合基岩版配方书不显示问题）</li>
 *   <li>checkin — 每日签到随机领取一种卷轴（所有玩家可用，每天一次，0 点重置）；
 *       {@code checkin chance} 查看各卷轴抽取概率（所有玩家可用）；
 *       {@code checkin reset <玩家>} 重置指定玩家签到状态（需权限 customdeath.checkin-reset）</li>
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
                // /cdm checkin chance：查看各卷轴抽取概率（所有玩家可用）
                if (args.length >= 2 && "chance".equalsIgnoreCase(args[1])) {
                    handleCheckinChance(sender);
                    return true;
                }
                // /cdm checkin reset <玩家>：重置签到状态（需权限，控制台可执行）
                if (args.length >= 2 && "reset".equalsIgnoreCase(args[1])) {
                    handleCheckinReset(sender, args);
                    return true;
                }
                // 签到需要玩家上下文（发物品），控制台无意义
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("该命令只能由玩家执行", NamedTextColor.RED));
                    return true;
                }
                handleCheckin(player);
            }
            case "sin" -> handleSin(sender, args);
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
     * 每次签到按 {@code settings.checkin.chance} 配置的概率随机抽中一种卷轴发放一张。
     * 发放采用「先发放、放不下则回滚」策略：addItem 存在剩余时，
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

        // 按配置概率随机抽中一种卷轴作为今日奖励
        CheckinDraw draw = rollCheckinScroll(cfg);
        if (draw == null) {
            player.sendMessage(Component.text("签到奖励未配置任何概率（settings.checkin.chance）", NamedTextColor.RED));
            return;
        }
        String type = draw.type();
        ItemStack scroll = switch (type) {
            case "protect" -> cfg.buildScrollProtect(1);
            case "town" -> cfg.buildScrollTown(1);
            default -> cfg.buildScrollRecall(1);
        };
        String scrollName = switch (type) {
            case "protect" -> "护身卷轴";
            case "town" -> "回城卷轴";
            default -> "回溯卷轴";
        };

        // 先发放；addItem 会修改传入 ItemStack 的数量（剩余未放入量），故提前克隆一份用于回滚
        ItemStack rollbackBackup = scroll.clone();
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(scroll);

        // 放不下：回滚已放入的部分，整体拒绝签到
        if (!leftover.isEmpty()) {
            player.getInventory().removeItem(rollbackBackup);
            player.sendMessage(Component.text("背包空间不足，请腾出空间后再签到", NamedTextColor.RED));
            return;
        }

        checkinManager.markCheckedIn(uuid);
        player.sendMessage(MiniMessage.miniMessage()
                .deserialize(cfg.getCheckinSuccessMessage()
                        .replace("{rewards}", scrollName + "×1")
                        .replace("{chance}", formatChance(draw.percent()))));
    }

    /**
     * /customdeath checkin chance — 查看各卷轴的签到抽取概率（所有玩家可用）。
     * <p>
     * 逐行展示三种卷轴的实际中奖概率与配置权重；签到成功文案末尾的可点击文本
     * 即执行本命令。三项概率之和不必为 100，实际概率 = 该项权重 ÷ 总权重。
     */
    private void handleCheckinChance(CommandSender sender) {
        ConfigManager cfg = plugin.getConfigManager();
        double protect = cfg.getCheckinScrollChance("protect");
        double town = cfg.getCheckinScrollChance("town");
        double recall = cfg.getCheckinScrollChance("recall");
        double total = protect + town + recall;
        sender.sendMessage(Component.text("===== 签到卷轴概率 =====", NamedTextColor.GOLD));
        if (total <= 0) {
            sender.sendMessage(Component.text(
                    "签到奖励未配置任何概率（settings.checkin.chance）", NamedTextColor.RED));
            return;
        }
        sender.sendMessage(chanceLine("护身卷轴", protect, total, NamedTextColor.YELLOW));
        sender.sendMessage(chanceLine("回溯卷轴", recall, total, NamedTextColor.AQUA));
        sender.sendMessage(chanceLine("回城卷轴", town, total, NamedTextColor.GREEN));
    }

    /**
     * 构造一行概率展示：「名称 xx.x%（权重 yy）」。
     *
     * @param name  卷轴中文名
     * @param value 配置权重
     * @param total 总权重
     * @param color 名称颜色
     */
    private Component chanceLine(String name, double value, double total, NamedTextColor color) {
        return Component.text(name, color)
                .append(Component.text("：" + formatChance(value * 100 / total)
                        + "%", NamedTextColor.WHITE))
                .append(Component.text("（权重 " + trimNumber(value) + "）", NamedTextColor.GRAY));
    }

    /**
     * 将百分比数值格式化为不带多余小数位的字符串：整数省略小数，
     * 非整数保留 1 位小数（如 5 → 「5」，1/3 → 「33.3」）。
     */
    private String formatChance(double percent) {
        String s = String.format(java.util.Locale.ROOT, "%.1f", percent);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    /** 同 {@link #formatChance}，用于展示原始配置权重的整数/一位小数形式。 */
    private String trimNumber(double value) {
        return formatChance(value);
    }

    /**
     * 签到抽取结果：抽中的卷轴类型与其中奖概率（已换算为百分比数值）。
     *
     * @param type    卷轴类型 protect / town / recall
     * @param percent 实际中奖概率（如 5、16.7）
     */
    private record CheckinDraw(String type, double percent) {
    }

    /**
     * 按 {@code settings.checkin.chance} 配置的概率加权随机抽取一种签到卷轴。
     * <p>
     * 实际中奖概率 = 该项概率 ÷ 所有正概率之和（三项之和不要求为 100）；
     * 概率为 0 的卷轴不会抽中。
     *
     * @param cfg 配置管理器
     * @return 抽中结果（类型 + 实际中奖概率%）；三项概率均非正时返回 null
     */
    private CheckinDraw rollCheckinScroll(ConfigManager cfg) {
        // LinkedHashMap 保持 protect → town → recall 的固定遍历顺序，保证抽取结果稳定
        Map<String, Double> chances = new LinkedHashMap<>();
        chances.put("protect", cfg.getCheckinScrollChance("protect"));
        chances.put("town", cfg.getCheckinScrollChance("town"));
        chances.put("recall", cfg.getCheckinScrollChance("recall"));

        double total = 0;
        for (double chance : chances.values()) {
            total += chance;
        }
        if (total <= 0) {
            return null;
        }

        double roll = ThreadLocalRandom.current().nextDouble(total);
        for (Map.Entry<String, Double> entry : chances.entrySet()) {
            roll -= entry.getValue();
            if (roll < 0) {
                return new CheckinDraw(entry.getKey(), entry.getValue() * 100 / total);
            }
        }
        // 浮点误差兜底：理论上不可达，返回最后一项
        return new CheckinDraw("recall", chances.get("recall") * 100 / total);
    }

    /**
     * /customdeath checkin reset &lt;玩家&gt; — 重置指定玩家的每日签到状态。
     * <p>
     * 清除其今日签到记录，允许当天重新签到（适用于补签、误操作等场景）；
     * 需权限 {@code customdeath.checkin-reset}（默认 op），控制台可执行，
     * 目标须在线（与 scroll / sin 子命令一致）。目标今天尚未签到时提示无需重置。
     */
    private void handleCheckinReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("customdeath.checkin-reset")) {
            sender.sendMessage(Component.text("你没有权限执行该命令", NamedTextColor.RED));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text("用法：/cdm checkin reset <玩家>", NamedTextColor.YELLOW));
            return;
        }
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isCheckinEnabled()) {
            sender.sendMessage(Component.text("签到功能未启用", NamedTextColor.RED));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(Component.text("玩家 " + args[2] + " 不在线", NamedTextColor.RED));
            return;
        }
        if (checkinManager.resetCheckedIn(target.getUniqueId())) {
            sender.sendMessage(Component.text(
                    "已重置 " + target.getName() + " 的今日签到状态，可重新签到", NamedTextColor.GREEN));
            target.sendMessage(Component.text(
                    "你的每日签到状态已被重置，现在可以重新输入 /cdm checkin 签到", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text(target.getName() + " 今天尚未签到，无需重置", NamedTextColor.YELLOW));
        }
    }

    /**
     * /customdeath sin [get|set|add <玩家> <数值>] — 查看/管理罪恶值（红名机制）。
     * <p>
     * 无参数时玩家查看自己的罪恶值与红名状态；{@code get/set/add} 需权限
     * {@code customdeath.sin}（默认 op），目标须在线（与 scroll 子命令一致）。
     */
    private void handleSin(CommandSender sender, String[] args) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isSinEnabled()) {
            sender.sendMessage(Component.text("红名机制未启用", NamedTextColor.RED));
            return;
        }
        SinManager sinManager = plugin.getSinManager();

        // 无参数：查看自己的罪恶值与状态
        if (args.length == 1) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("用法：/cdm sin [get|set|add <玩家> <数值>]", NamedTextColor.YELLOW));
                return;
            }
            sendSinInfo(sender, player);
            return;
        }

        switch (args[1].toLowerCase()) {
            case "get" -> {
                if (!sender.hasPermission("customdeath.sin")) {
                    sender.sendMessage(Component.text("你没有权限执行该命令", NamedTextColor.RED));
                    return;
                }
                if (args.length < 3) {
                    sender.sendMessage(Component.text("用法：/cdm sin get <玩家>", NamedTextColor.YELLOW));
                    return;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    sender.sendMessage(Component.text("玩家 " + args[2] + " 不在线", NamedTextColor.RED));
                    return;
                }
                sendSinInfo(sender, target);
            }
            case "set", "add" -> {
                if (!sender.hasPermission("customdeath.sin")) {
                    sender.sendMessage(Component.text("你没有权限执行该命令", NamedTextColor.RED));
                    return;
                }
                if (args.length < 4) {
                    sender.sendMessage(Component.text("用法：/cdm sin " + args[1].toLowerCase() + " <玩家> <数值>", NamedTextColor.YELLOW));
                    return;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    sender.sendMessage(Component.text("玩家 " + args[2] + " 不在线", NamedTextColor.RED));
                    return;
                }
                int value;
                try {
                    value = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("数值必须是整数", NamedTextColor.RED));
                    return;
                }
                if ("set".equalsIgnoreCase(args[1])) {
                    sinManager.setSin(target, value);
                    sender.sendMessage(Component.text(
                            "已将 " + target.getName() + " 的罪恶值设为 " + sinManager.getSin(target.getUniqueId()),
                            NamedTextColor.GREEN));
                } else {
                    sinManager.addSin(target, value);
                    sender.sendMessage(Component.text(
                            "已调整 " + target.getName() + " 罪恶值 " + (value >= 0 ? "+" : "") + value
                                    + "，当前 " + sinManager.getSin(target.getUniqueId()),
                            NamedTextColor.GREEN));
                }
            }
            default -> sender.sendMessage(Component.text(
                    "用法：/cdm sin [get|set|add <玩家> <数值>]", NamedTextColor.YELLOW));
        }
    }

    /**
     * 向命令执行者展示指定玩家的罪恶值与红名状态。
     */
    private void sendSinInfo(CommandSender sender, Player target) {
        SinManager sinManager = plugin.getSinManager();
        int sin = sinManager.getSin(target.getUniqueId());
        int threshold = plugin.getConfigManager().getSinRedThreshold();
        SinManager.SinState state = sinManager.getState(target.getUniqueId());
        String stateName = switch (state) {
            case RED -> "红名";
            case WARNING -> "红名临界";
            default -> "正常";
        };
        NamedTextColor stateColor = switch (state) {
            case RED -> NamedTextColor.DARK_RED;
            case WARNING -> NamedTextColor.RED;
            default -> NamedTextColor.GREEN;
        };
        sender.sendMessage(Component.text("===== 罪恶值 =====", NamedTextColor.GOLD));
        sender.sendMessage(Component.text(target.getName(), NamedTextColor.YELLOW)
                .append(Component.text("：罪恶值 " + sin + "/" + threshold, NamedTextColor.GRAY))
                .append(Component.text("  状态：", NamedTextColor.GRAY))
                .append(Component.text(stateName, stateColor)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return List.of("reload", "version", "scroll", "recipe", "checkin", "sin").stream()
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
            if ("sin".equalsIgnoreCase(args[0])) {
                return List.of("get", "set", "add").stream()
                        .filter(s -> s.startsWith(input))
                        .toList();
            }
            if ("checkin".equalsIgnoreCase(args[0])) {
                return List.of("chance", "reset").stream()
                        .filter(s -> s.startsWith(input))
                        .toList();
            }
        }
        if (args.length == 3
                && ("scroll".equalsIgnoreCase(args[0]) || "sin".equalsIgnoreCase(args[0]))) {
            return playerNames(args[2].toLowerCase());
        }
        if (args.length == 3 && "checkin".equalsIgnoreCase(args[0])) {
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
                .append(Component.text(" - 每日签到随机领取一种卷轴（每天一次）", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/cdm checkin reset <玩家>", NamedTextColor.YELLOW)
                .append(Component.text(" - 重置玩家今日签到状态（需 customdeath.checkin-reset）", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/cdm scroll <protect|town|recall> <玩家> [数量]", NamedTextColor.YELLOW)
                .append(Component.text(" - 发放护身/回城/回溯卷轴", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/cdm sin", NamedTextColor.YELLOW)
                .append(Component.text(" - 查看罪恶值/红名状态", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/cdm sin get|set|add <玩家> <数值>", NamedTextColor.YELLOW)
                .append(Component.text(" - 管理罪恶值（需 customdeath.sin）", NamedTextColor.GRAY)));
    }
}

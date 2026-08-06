package com.customdeath.listener;

import com.customdeath.CustomDeathMessages;
import com.customdeath.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * 卷轴使用监听器（统一处理护身 / 回城 / 回溯三类卷轴）。
 * <p>
 * - 护身卷轴：玩家死亡时若背包（含装备栏与副手）携带，则免于物品与经验掉落
 *   （保留背包 + 清空掉落列表双保险），并消耗一张；
 *   监听优先级 HIGH，早于主监听器（HIGHEST）的播报逻辑执行。
 * - 回城 / 回溯卷轴：手持右键点击触发对应传送效果并消耗一张（监听 PlayerInteractEvent）。
 * 卷轴物品均通过 PDC 标识识别（{@link ConfigManager#SCROLL_PROTECT_KEY} /
 * {@link ConfigManager#SCROLL_TOWN_KEY} / {@link ConfigManager#SCROLL_RECALL_KEY}），防重命名伪造。
 */
public class ScrollListener implements Listener {

    private final CustomDeathMessages plugin;

    /** 未设置重生点 / 无死亡点记录时的提示。 */
    private static final Component MSG_NO_BED = deserialize("<red>你没有设置重生点，卷轴失效</red>");
    private static final Component MSG_NO_DEATH_POINT = deserialize("<red>没有死亡点记录，卷轴失效</red>");
    private static final Component MSG_UNREACHABLE = deserialize("<red>目标位置已不可达</red>");

    public ScrollListener(CustomDeathMessages plugin) {
        this.plugin = plugin;
    }

    // ============ 护身卷轴：死亡免掉落 ============

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isScrollProtectEnabled()) {
            return;
        }

        Player player = event.getEntity();
        int slot = findProtectSlot(player);
        if (slot < 0) {
            return;
        }

        // 免于物品与经验掉落：采用「保留标志 + 显式清空」双保险。
        // 仅设置 keep 标志在部分 Paper 版本/配置下不足以彻底阻止掉落
        // （表现为玩家虽保留背包与经验，但地上仍生成掉落物与经验球），
        // 因此额外清空掉落列表并把掉落经验归零，确保不会生成可拾取物。
        event.setKeepInventory(true);
        if (cfg.isScrollProtectKeepLevel()) {
            event.setKeepLevel(true);
        }
        event.getDrops().clear();
        event.setDroppedExp(0);

        // 消耗一张护身卷轴（amount > 1 时减一，否则整格清除）
        ItemStack scroll = player.getInventory().getItem(slot);
        if (scroll != null && scroll.getAmount() > 1) {
            scroll.setAmount(scroll.getAmount() - 1);
        } else {
            player.getInventory().setItem(slot, null);
        }

        // 提示玩家
        String message = cfg.getScrollProtectMessage();
        if (message != null && !message.isBlank()) {
            player.sendMessage(deserialize(message));
        }
    }

    /**
     * 查找背包（含装备栏与副手）中第一张护身卷轴所在的格子。
     *
     * @return 格子索引（0-40），未找到返回 -1
     */
    private int findProtectSlot(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isProtectScroll(contents[i])) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 判断物品是否为护身卷轴（通过 PDC 标识识别，防重命名误判）。
     */
    private boolean isProtectScroll(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(
                ConfigManager.SCROLL_PROTECT_KEY, PersistentDataType.INTEGER);
    }

    // ============ 回城 / 回溯卷轴：右键传送 ============

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }

        ConfigManager cfg = plugin.getConfigManager();
        Player player = event.getPlayer();

        // 识别卷轴类型（PDC 标识）
        PersistentDataContainer pdc = item.getItemMeta() == null ? null : item.getItemMeta().getPersistentDataContainer();
        boolean isTown = pdc != null && pdc.has(ConfigManager.SCROLL_TOWN_KEY, PersistentDataType.INTEGER);
        boolean isRecall = pdc != null && pdc.has(ConfigManager.SCROLL_RECALL_KEY, PersistentDataType.INTEGER);
        if (!isTown && !isRecall) {
            return;
        }

        // 阻止默认交互（避免对右键方块产生额外行为）
        event.setCancelled(true);

        // 回城卷轴：传送到床（重生点）
        if (isTown && cfg.isScrollTownEnabled()) {
            useTownScroll(player);
            return;
        }
        // 回溯卷轴：传送到最近死亡点
        if (isRecall && cfg.isScrollRecallEnabled()) {
            useRecallScroll(player);
        }
    }

    /**
     * 回城卷轴：传送到玩家床（重生点）位置；未设置重生点时提示且不消耗卷轴。
     */
    private void useTownScroll(Player player) {
        Location bed = player.getBedSpawnLocation();
        if (bed == null) {
            player.sendMessage(MSG_NO_BED);
            return;
        }
        // 传送到床位置（跨世界安全，床位置由服务器保证可站）
        player.teleport(bed.clone().add(0.5, 0.1, 0.5));
        consumeScroll(player);
        sendMessage(player, plugin.getConfigManager().getScrollTownMessage());
    }

    /**
     * 回溯卷轴：传送到最近死亡点；无记录或位置不可达时提示且不消耗卷轴。
     */
    private void useRecallScroll(Player player) {
        Location deathPoint = plugin.getDeathPointManager().getRecentDeathPoint(player.getUniqueId());
        if (deathPoint == null) {
            player.sendMessage(MSG_NO_DEATH_POINT);
            return;
        }
        Location safe = findSafeLocation(deathPoint);
        if (safe == null) {
            player.sendMessage(MSG_UNREACHABLE);
            return;
        }
        player.teleport(safe);
        consumeScroll(player);
        sendMessage(player, plugin.getConfigManager().getScrollRecallMessage());
    }

    /**
     * 从死亡点向上寻找可站立的传送位置（最多 6 格）。
     * 虚空死亡等无法找到安全位置时返回 null。
     */
    private Location findSafeLocation(Location deathPoint) {
        for (int i = 0; i <= 6; i++) {
            Location check = deathPoint.clone().add(0, i, 0);
            Block feet = check.getBlock();
            Block below = check.clone().add(0, -1, 0).getBlock();
            if (feet.getType() != Material.AIR && !feet.isLiquid()) {
                continue;
            }
            if (below.getType() == Material.AIR || below.isLiquid()) {
                continue;
            }
            return check.add(0.5, 0, 0.5);
        }
        return null;
    }

    /**
     * 消耗玩家主手的一张卷轴（数量 > 1 时减一，否则整格清除）。
     */
    private void consumeScroll(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }

    private void sendMessage(Player player, String message) {
        if (message != null && !message.isBlank()) {
            player.sendMessage(deserialize(message));
        }
    }

    /** 解析 MiniMessage 文本，失败时降级为纯文本。 */
    private static Component deserialize(String text) {
        try {
            return MiniMessage.miniMessage().deserialize(text);
        } catch (Exception e) {
            return Component.text(text);
        }
    }
}

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
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * 卷轴使用监听器。
 * <p>
 * 手持回城 / 回溯卷轴右键点击时触发对应传送效果并消耗一张卷轴。
 * 卷轴物品通过 PDC 标识识别（{@link ConfigManager#SCROLL_TOWN_KEY}
 * 与 {@link ConfigManager#SCROLL_RECALL_KEY}），防重命名伪造。
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

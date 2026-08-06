package com.customdeath.listener;

import com.customdeath.CustomDeathMessages;
import com.customdeath.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * 死亡保护券监听器。
 * <p>
 * 玩家死亡时，若背包（含装备栏与副手）携带保护券，
 * 则免于物品掉落（可选同时保留经验等级），并消耗一张券。
 * 优先级 HIGH，早于主监听器（HIGHEST）的播报逻辑执行。
 */
public class DeathTicketListener implements Listener {

    private final CustomDeathMessages plugin;

    public DeathTicketListener(CustomDeathMessages plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isDeathTicketEnabled()) {
            return;
        }

        Player player = event.getEntity();
        int slot = findTicketSlot(player);
        if (slot < 0) {
            return;
        }

        // 免于物品与经验掉落：采用「保留标志 + 显式清空」双保险。
        // 仅设置 keep 标志在部分 Paper 版本/配置下不足以彻底阻止掉落
        // （表现为玩家虽保留背包与经验，但地上仍生成掉落物与经验球），
        // 因此额外清空掉落列表并把掉落经验归零，确保不会生成可拾取物。
        event.setKeepInventory(true);
        if (cfg.isDeathTicketKeepLevel()) {
            event.setKeepLevel(true);
        }
        event.getDrops().clear();
        event.setDroppedExp(0);

        // 消耗一张券（amount > 1 时减一，否则整格清除）
        ItemStack ticket = player.getInventory().getItem(slot);
        if (ticket != null && ticket.getAmount() > 1) {
            ticket.setAmount(ticket.getAmount() - 1);
        } else {
            player.getInventory().setItem(slot, null);
        }

        // 提示玩家
        String message = cfg.getDeathTicketMessage();
        if (message != null && !message.isBlank()) {
            player.sendMessage(deserialize(message));
        }
    }

    /**
     * 查找背包（含装备栏与副手）中第一张保护券所在的格子。
     *
     * @return 格子索引（0-40），未找到返回 -1
     */
    private int findTicketSlot(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isTicket(contents[i])) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 判断物品是否为保护券（通过 PDC 标识识别，防重命名误判）。
     */
    private boolean isTicket(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(
                ConfigManager.DEATH_TICKET_KEY, PersistentDataType.INTEGER);
    }

    /** 解析 MiniMessage 文本，失败时降级为纯文本。 */
    private Component deserialize(String text) {
        try {
            return MiniMessage.miniMessage().deserialize(text);
        } catch (Exception e) {
            return Component.text(text);
        }
    }
}

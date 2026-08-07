package com.customdeath.listener;

import com.customdeath.CustomDeathMessages;
import com.customdeath.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 卷轴使用监听器（统一处理护身 / 回城 / 回溯三类卷轴）。
 * <p>
 * - 护身卷轴：玩家死亡时若背包（含装备栏与副手）携带，则免于物品与经验掉落
 *   （保留背包 + 清空掉落列表双保险），并消耗一张；
 *   监听优先级 HIGH，早于主监听器（HIGHEST）的播报逻辑执行。
 * - 回城 / 回溯卷轴：手持右键点击后进入施法倒计时（读条），期间每秒以 actionbar
 *   提示剩余秒数、物品栏显示冷却条（类似末影珍珠）、周身播放传送粒子、
 *   按配置间隔播放节拍音效；倒计时结束校验主手仍持有对应卷轴后传送（并播放
 *   完成音效）与消耗一张。
 *   施法可被以下情况打断（打断不消耗卷轴）：移动（受 cancel-on-move 控制）、
 *   死亡、退出服务器、被传送；同一玩家施法期间重复右键会收到提示。
 *   配置 cast-time 为 0 时跳过读条，恢复"立即传送"的旧行为。
 * 卷轴物品均通过 PDC 标识识别（{@link ConfigManager#SCROLL_PROTECT_KEY} /
 * {@link ConfigManager#SCROLL_TOWN_KEY} / {@link ConfigManager#SCROLL_RECALL_KEY}），防重命名伪造。
 */
public class ScrollListener implements Listener {

    private final CustomDeathMessages plugin;

    /** 未设置重生点 / 无死亡点记录时的提示。 */
    private static final Component MSG_NO_BED = deserialize("<red>你没有设置重生点，卷轴失效</red>");
    private static final Component MSG_NO_DEATH_POINT = deserialize("<red>没有死亡点记录，卷轴失效</red>");
    private static final Component MSG_UNREACHABLE = deserialize("<red>目标位置已不可达</red>");
    /** 施法进行中再次右键的提示。 */
    private static final Component MSG_CASTING = deserialize("<gray>你正在施法中，请稍候...</gray>");
    /** 倒计时结束但卷轴已不在主手（被换走/收起）的提示。 */
    private static final Component MSG_SCROLL_LOST = deserialize("<red>卷轴已不在手中，施法失败</red>");

    /** 施法中的玩家：UUID → 施法上下文（记录卷轴类型、结束时刻、任务、粒子任务与卷轴材质）。 */
    private final Map<UUID, CastContext> casting = new HashMap<>();

    /** 移动打断的位移阈值（平方距离，约 0.1 格），避免方块微动/视角晃动误打断。 */
    private static final double MOVE_CANCEL_THRESHOLD_SQ = 0.01;

    /** 粒子刷新间隔（刻，5 刻 = 0.25 秒一波）。 */
    private static final long PARTICLE_INTERVAL_TICKS = 5L;

    /** 粒子环绕半径（格）。 */
    private static final double PARTICLE_RADIUS = 1.2;

    /** 粒子环绕高度（格，自脚下起）。 */
    private static final double PARTICLE_HEIGHT = 1.8;

    /** 粒子每波数量（环绕一圈）。 */
    private static final int PARTICLE_AMOUNT = 20;

    /**
     * 一次进行中的施法上下文。
     *
     * @param town         true = 回城卷轴，false = 回溯卷轴
     * @param endTime      施法结束时刻（毫秒时间戳）
     * @param task         倒计时调度任务
     * @param particleTask 粒子特效调度任务（施法打断/结束时一并取消）
     * @param material     卷轴材质（用于施法期间物品栏冷却条与打断时清除冷却）
     */
    private record CastContext(boolean town, long endTime, BukkitTask task, BukkitTask particleTask, Material material) {
    }

    public ScrollListener(CustomDeathMessages plugin) {
        this.plugin = plugin;
    }

    // ============ 护身卷轴：死亡免掉落 ============

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        // 死亡同时打断进行中的回城/回溯施法（不消耗卷轴，静默取消）
        cancelCast(event.getEntity().getUniqueId(), false);

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

        // 回城卷轴：进入施法倒计时后传送到床（重生点）
        if (isTown && cfg.isScrollTownEnabled()) {
            startCast(player, true);
            return;
        }
        // 回溯卷轴：进入施法倒计时后传送到最近死亡点
        if (isRecall && cfg.isScrollRecallEnabled()) {
            startCast(player, false);
        }
    }

    // ============ 施法倒计时（回城 / 回溯共用）============

    /**
     * 开始一次卷轴施法（回城或回溯）。
     * <p>
     * 施法倒计时由调度任务驱动：每秒向玩家发送 actionbar 剩余秒数提示，
     * 结束时执行传送并消耗卷轴。施法过程中移动/死亡/退出/传送会打断施法（不消耗卷轴）。
     * 配置 cast-time 为 0 时跳过倒计时直接传送（兼容旧行为）。
     *
     * @param player 施法玩家
     * @param town    true = 回城卷轴，false = 回溯卷轴
     */
    private void startCast(Player player, boolean town) {
        ConfigManager cfg = plugin.getConfigManager();
        int castTime = town ? cfg.getScrollTownCastTime() : cfg.getScrollRecallCastTime();

        // 无倒计时：直接按旧逻辑立即传送
        if (castTime <= 0) {
            if (town) {
                useTownScroll(player);
            } else {
                useRecallScroll(player);
            }
            return;
        }

        UUID uuid = player.getUniqueId();
        // 防止同一玩家重复施法
        if (casting.containsKey(uuid)) {
            player.sendMessage(MSG_CASTING);
            return;
        }

        long endTime = System.currentTimeMillis() + castTime * 1000L;
        String actionbar = town ? cfg.getScrollTownCountdownActionbar() : cfg.getScrollRecallCountdownActionbar();
        String startMsg = town ? cfg.getScrollTownCastStartMessage() : cfg.getScrollRecallCastStartMessage();
        sendMessage(player, startMsg.replace("{time}", String.valueOf(castTime)));

        // 施法期间显示物品栏冷却条（类似末影珍珠）：冷却时长 = cast-time（20 刻 = 1 秒）
        Material material = town ? cfg.getScrollTownMaterial() : cfg.getScrollRecallMaterial();
        if (cfg.isScrollCooldownEnabled()) {
            player.setCooldown(material, castTime * 20);
        }

        // 施法倒计时节拍音效（仅施法者本人可听），按 cast-sound-interval 秒间隔播放
        Sound castSound = cfg.isScrollCastSoundEnabled() ? cfg.getScrollCastSound() : null;
        int castSoundInterval = cfg.getScrollCastSoundInterval();
        float castVolume = cfg.getScrollCastSoundVolume();
        float castPitch = cfg.getScrollCastSoundPitch();
        long[] lastCastSoundAt = {0L};

        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            // 施法上下文已被移除（被打断）时，任务由 cancelCast/cancelTask 显式取消，此处仅停止逻辑
            CastContext ctx = casting.get(uuid);
            if (ctx == null) {
                return;
            }
            long remaining = ctx.endTime() - System.currentTimeMillis();
            if (remaining <= 0) {
                // 倒计时结束：移除上下文并停止任务，然后执行传送
                cancelTask(uuid);
                finishCast(player, ctx.town());
                return;
            }
            // 每秒更新 actionbar：向上取整剩余秒数
            int seconds = (int) Math.ceil(remaining / 1000.0);
            player.sendActionBar(deserialize(actionbar.replace("{time}", String.valueOf(seconds))));
            // 按配置间隔播放节拍音效（倒计时滴答声）
            if (castSound != null && System.currentTimeMillis() - lastCastSoundAt[0] >= castSoundInterval * 1000L) {
                player.playSound(player.getLocation(), castSound, castVolume, castPitch);
                lastCastSoundAt[0] = System.currentTimeMillis();
            }
        }, 0L, 20L);

        // 施法期间播放传送粒子特效（每 0.25 秒一波，环绕玩家周身）
        BukkitTask particleTask = null;
        if (cfg.isScrollParticleEnabled() && cfg.getScrollCastParticle() != null) {
            Particle particle = cfg.getScrollCastParticle();
            int amount = cfg.getScrollCastParticleAmount();
            particleTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                if (!casting.containsKey(uuid)) {
                    return;
                }
                spawnCastParticles(player, particle, amount);
            }, 0L, PARTICLE_INTERVAL_TICKS);
        }

        casting.put(uuid, new CastContext(town, endTime, task, particleTask, material));
    }

    /**
     * 在施法玩家周身生成传送感粒子：以玩家为中心、固定高度环绕一圈。
     * <p>
     * 形态为水平圆环（半径 {@link #PARTICLE_RADIUS}、高度 {@link #PARTICLE_HEIGHT}），
     * 配合 0.25 秒一波的刷新频率形成持续环绕的传送门效果。
     */
    private void spawnCastParticles(Player player, Particle particle, int amount) {
        Location center = player.getLocation().clone().add(0, PARTICLE_HEIGHT, 0);
        double step = Math.PI * 2 / amount;
        for (int i = 0; i < amount; i++) {
            double angle = step * i;
            double x = center.getX() + Math.cos(angle) * PARTICLE_RADIUS;
            double z = center.getZ() + Math.sin(angle) * PARTICLE_RADIUS;
            player.getWorld().spawnParticle(particle, x, center.getY(), z, 1, 0, 0, 0, 0);
        }
    }

    /**
     * 倒计时结束，校验主手仍持有对应卷轴后执行传送（并消耗卷轴）。
     * 若卷轴已被换走/收起则取消传送且不消耗。
     */
    private void finishCast(Player player, boolean town) {
        if (!isHoldingScroll(player, town)) {
            player.sendMessage(MSG_SCROLL_LOST);
            return;
        }
        if (town) {
            useTownScroll(player);
        } else {
            useRecallScroll(player);
        }
    }

    /**
     * 判断主手物品是否仍为指定类型的卷轴（防倒计时期间换物品）。
     */
    private boolean isHoldingScroll(Player player, boolean town) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return town
                ? pdc.has(ConfigManager.SCROLL_TOWN_KEY, PersistentDataType.INTEGER)
                : pdc.has(ConfigManager.SCROLL_RECALL_KEY, PersistentDataType.INTEGER);
    }

    /**
     * 打断（或完成时清理）指定玩家的施法：取消调度任务与粒子任务、移除上下文、清除物品栏冷却条。
     *
     * @param uuid   玩家 UUID
     * @param notify 是否向玩家发送"施法被打断"提示（移动打断时 true，其他静默清理）
     */
    private void cancelCast(UUID uuid, boolean notify) {
        CastContext ctx = casting.remove(uuid);
        if (ctx == null) {
            return;
        }
        ctx.task().cancel();
        if (ctx.particleTask() != null) {
            ctx.particleTask().cancel();
        }
        Player player = plugin.getServer().getPlayer(uuid);
        if (player != null && player.isOnline()) {
            // 清除施法期间设置的物品栏冷却条（与 cast-time 绑定，中断后立即恢复可用）
            player.setCooldown(ctx.material(), 0);
            if (notify) {
                ConfigManager cfg = plugin.getConfigManager();
                String cancelMsg = ctx.town() ? cfg.getScrollTownCastCancelMessage() : cfg.getScrollRecallCastCancelMessage();
                sendMessage(player, cancelMsg);
            }
        }
    }

    /** 倒计时结束时移除上下文并取消任务（无玩家提示）。 */
    private void cancelTask(UUID uuid) {
        CastContext ctx = casting.remove(uuid);
        if (ctx != null) {
            ctx.task().cancel();
            if (ctx.particleTask() != null) {
                ctx.particleTask().cancel();
            }
        }
    }

    // ============ 施法打断监听 ============

    /**
     * 施法期间玩家移动超过阈值（约 0.1 格）时打断施法（受 cancel-on-move 配置控制）。
     */
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!casting.containsKey(uuid)) {
            return;
        }
        ConfigManager cfg = plugin.getConfigManager();
        CastContext ctx = casting.get(uuid);
        boolean cancelOnMove = ctx.town() ? cfg.isScrollTownCancelOnMove() : cfg.isScrollRecallCancelOnMove();
        if (!cancelOnMove) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        // 位移超过阈值视为"移动"（视角转动不会产生位移）
        if (from.getWorld() != to.getWorld() || from.distanceSquared(to) > MOVE_CANCEL_THRESHOLD_SQ) {
            cancelCast(uuid, true);
        }
    }

    /** 退出服务器打断施法（不消耗卷轴）。 */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancelCast(event.getPlayer().getUniqueId(), false);
    }

    /** 被传送（如 /tp、跨世界）打断施法（不消耗卷轴）。 */
    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        cancelCast(event.getPlayer().getUniqueId(), false);
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
        playCompleteSound(player);
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
        playCompleteSound(player);
        sendMessage(player, plugin.getConfigManager().getScrollRecallMessage());
    }

    /**
     * 传送成功（回到目标地点）后播放完成音效（仅传送者本人可听，可配置）。
     */
    private void playCompleteSound(Player player) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isScrollCompleteSoundEnabled()) {
            return;
        }
        Sound sound = cfg.getScrollCompleteSound();
        if (sound != null) {
            player.playSound(player.getLocation(), sound, cfg.getScrollCompleteSoundVolume(), cfg.getScrollCompleteSoundPitch());
        }
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

package com.customdeath.listener;

import com.customdeath.CustomDeathMessages;
import com.customdeath.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Map;

/**
 * 玩家死亡事件监听器。
 * <p>
 * 拦截原版死亡消息，根据死亡原因匹配分类，
 * 从配置中选取消息模板并广播。
 */
public class DeathListener implements Listener {

    private final CustomDeathMessages plugin;

    /** 伤害原因 → 死亡分类 */
    private static final Map<EntityDamageEvent.DamageCause, String> CAUSE_CATEGORIES = Map.ofEntries(
            Map.entry(EntityDamageEvent.DamageCause.FALL,            "fall"),
            Map.entry(EntityDamageEvent.DamageCause.FIRE,            "fire"),
            Map.entry(EntityDamageEvent.DamageCause.FIRE_TICK,       "fire"),
            Map.entry(EntityDamageEvent.DamageCause.LAVA,            "lava"),
            Map.entry(EntityDamageEvent.DamageCause.DROWNING,        "drowning"),
            Map.entry(EntityDamageEvent.DamageCause.SUFFOCATION,     "suffocation"),
            Map.entry(EntityDamageEvent.DamageCause.VOID,            "void"),
            Map.entry(EntityDamageEvent.DamageCause.BLOCK_EXPLOSION, "explosion"),
            Map.entry(EntityDamageEvent.DamageCause.ENTITY_EXPLOSION,"explosion"),
            Map.entry(EntityDamageEvent.DamageCause.LIGHTNING,       "lightning"),
            Map.entry(EntityDamageEvent.DamageCause.MAGIC,           "magic"),
            Map.entry(EntityDamageEvent.DamageCause.DRAGON_BREATH,   "magic"),
            Map.entry(EntityDamageEvent.DamageCause.THORNS,          "magic"),
            Map.entry(EntityDamageEvent.DamageCause.POISON,          "poison"),
            Map.entry(EntityDamageEvent.DamageCause.STARVATION,      "starvation"),
            Map.entry(EntityDamageEvent.DamageCause.FALLING_BLOCK,   "fall_block")
    );

    /** 怪物类型 → 中文名（用于匹配中文分类 key） */
    private static final Map<EntityType, String> MOB_CN_NAMES = Map.ofEntries(
            Map.entry(EntityType.ZOMBIE,      "僵尸"),
            Map.entry(EntityType.SKELETON,    "骷髅"),
            Map.entry(EntityType.CREEPER,     "苦力怕"),
            Map.entry(EntityType.SPIDER,      "蜘蛛"),
            Map.entry(EntityType.ENDERMAN,    "末影人"),
            Map.entry(EntityType.WITCH,       "女巫"),
            Map.entry(EntityType.BLAZE,       "烈焰人"),
            Map.entry(EntityType.SLIME,       "史莱姆"),
            Map.entry(EntityType.GHAST,       "恶魂"),
            Map.entry(EntityType.PIGLIN,      "猪灵"),
            Map.entry(EntityType.WARDEN,      "循声守卫"),
            Map.entry(EntityType.IRON_GOLEM,  "铁傀儡")
    );

    public DeathListener(CustomDeathMessages plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isEnabled()) {
            return;
        }

        Player player = event.getEntity();
        DeathInfo info = resolveDeathInfo(player);

        // 控制台日志（可选）
        if (cfg.isConsoleLogReason()) {
            EntityDamageEvent lastDamage = player.getLastDamageCause();
            plugin.getLogger().info("[" + player.getName() + "] 死亡分类=" + info.category()
                    + " 原始原因=" + (lastDamage == null ? "UNKNOWN" : lastDamage.getCause().name()));
        }

        // 选取消息模板
        String template = cfg.pickTemplate(info.category());
        if (template == null) {
            return;
        }

        // 格式化并广播
        Component message = plugin.getMessageFormatter().format(
                template, player.getName(), info.killerName(), info.weapon(),
                info.damage(), player.getWorld().getName());

        event.deathMessage(null);
        broadcast(player, message);

        // 将死亡消息打印到控制台日志（纯文本）
        if (cfg.isConsoleLogDeath()) {
            plugin.getLogger().info(PlainTextComponentSerializer.plainText().serialize(message));
        }

        // 播放死亡音效
        playDeathSound(player);
    }

    // ==================== 死亡信息解析 ====================

    /**
     * 解析玩家的死亡信息，判断死亡分类、击杀者和武器。
     */
    private DeathInfo resolveDeathInfo(Player player) {
        EntityDamageEvent lastDamage = player.getLastDamageCause();
        double damage = lastDamage == null ? 0.0 : lastDamage.getFinalDamage();

        // 被实体击杀
        if (lastDamage instanceof EntityDamageByEntityEvent byEntity) {
            Entity damager = byEntity.getDamager();

            // 投射物（箭、雪球等）
            if (damager instanceof Projectile projectile) {
                ProjectileSource source = projectile.getShooter();
                if (source instanceof LivingEntity shooter) {
                    if (shooter instanceof Player killer) {
                        return pvpInfo(killer, projectile, damage);
                    }
                    return entityInfo(shooter, damage);
                }
            }

            // 近战玩家
            if (damager instanceof Player killer) {
                return pvpInfo(killer, null, damage);
            }

            // 近战怪物
            if (damager instanceof LivingEntity attacker) {
                return entityInfo(attacker, damage);
            }

            // 其他实体（如 TNT）
            String typeKey = damager.getType().name().toLowerCase();
            String category = plugin.getConfigManager().hasCategory(typeKey) ? typeKey : "monster";
            return new DeathInfo(category, null, null, damage);
        }

        // 环境伤害
        EntityDamageEvent.DamageCause cause = lastDamage == null ? null : lastDamage.getCause();
        String category = cause == null ? "default" : CAUSE_CATEGORIES.getOrDefault(cause, "default");
        return new DeathInfo(category, null, null, damage);
    }

    /**
     * 构建 PVP 死亡信息。
     */
    private DeathInfo pvpInfo(Player killer, Entity projectile, double damage) {
        String weapon = "拳头";
        if (projectile != null) {
            weapon = projectileName(projectile);
        } else {
            ItemStack item = killer.getInventory().getItemInMainHand();
            if (item != null && !item.getType().isAir()) {
                weapon = itemName(item);
            }
        }
        return new DeathInfo("pvp", killer.getName(), weapon, damage);
    }

    /**
     * 构建怪物击杀死亡信息。
     */
    private DeathInfo entityInfo(LivingEntity attacker, double damage) {
        ConfigManager cfg = plugin.getConfigManager();
        String typeKey = attacker.getType().name().toLowerCase();
        String cnName = MOB_CN_NAMES.get(attacker.getType());

        String category;
        if (cfg.hasCategory(typeKey)) {
            category = typeKey;
        } else if (cnName != null && cfg.hasCategory(cnName)) {
            category = cnName;
        } else {
            category = "monster";
        }

        return new DeathInfo(category, attacker.getName(), null, damage);
    }

    // ==================== 工具方法 ====================

    private String itemName(ItemStack item) {
        try {
            return PlainTextComponentSerializer.plainText().serialize(item.displayName());
        } catch (Exception e) {
            return item.getType().name().toLowerCase();
        }
    }

    private String projectileName(Entity projectile) {
        return switch (projectile.getType()) {
            case ARROW              -> "弓箭";
            case SPECTRAL_ARROW     -> "光灵箭";
            case SNOWBALL           -> "雪球";
            case EGG                -> "鸡蛋";
            case ENDER_PEARL        -> "末影珍珠";
            case TRIDENT            -> "三叉戟";
            case FIREBALL, SMALL_FIREBALL, DRAGON_FIREBALL -> "火球";
            case SHULKER_BULLET     -> "潜影贝导弹";
            case WITHER_SKULL       -> "凋灵之首";
            default                 -> projectile.getName();
        };
    }

    /**
     * 按配置的广播模式发送消息。
     */
    private void broadcast(Player player, Component message) {
        switch (plugin.getConfigManager().getBroadcastMode()) {
            case "world" -> player.getWorld().getPlayers().forEach(p -> p.sendMessage(message));
            case "nearby" -> {
                Location loc = player.getLocation();
                double radius = plugin.getConfigManager().getNearbyRadius();
                double radiusSquared = radius * radius;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getWorld().equals(player.getWorld())
                            && p.getLocation().distanceSquared(loc) <= radiusSquared) {
                        p.sendMessage(message);
                    }
                }
            }
            default -> Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(message));
        }
    }

    /**
     * 播放死亡音效。
     * <p>
     * 按配置的 target 决定播放对象：
     * <ul>
     *   <li>victim  — 仅死亡玩家本人听到</li>
     *   <li>nearby  — 死亡地点附近玩家听到（范围由音量决定）</li>
     *   <li>everyone（默认）— 全服所有在线玩家听到</li>
     * </ul>
     */
    private void playDeathSound(Player player) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isDeathSoundEnabled()) {
            return;
        }

        Sound sound = cfg.getDeathSound();
        if (sound == null) {
            // 配置的音效名无效，跳过播放
            return;
        }

        float volume = cfg.getDeathSoundVolume();
        float pitch = cfg.getDeathSoundPitch();

        switch (cfg.getDeathSoundTarget()) {
            case "victim" -> player.playSound(player.getLocation(), sound, volume, pitch);
            case "nearby" -> player.getWorld().playSound(player.getLocation(), sound, volume, pitch);
            default -> Bukkit.getOnlinePlayers().forEach(p -> p.playSound(p.getLocation(), sound, volume, pitch));
        }
    }

    /**
     * 死亡信息记录。
     *
     * @param category   死亡分类 key
     * @param killerName 击杀者名（无击杀者时为 null）
     * @param weapon     武器名（无武器时为 null）
     * @param damage     最终伤害值
     */
    private record DeathInfo(String category, String killerName, String weapon, double damage) {}
}

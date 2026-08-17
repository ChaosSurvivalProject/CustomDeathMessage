package com.customdeath.listener;

import com.customdeath.CustomDeathMessages;
import com.customdeath.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
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
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.projectiles.ProjectileSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家死亡事件监听器。
 * <p>
 * 拦截原版死亡消息，根据死亡原因匹配分类，
 * 从配置中选取消息模板并广播。
 */
public class DeathListener implements Listener {

    private final CustomDeathMessages plugin;

    /** 连杀统计：击杀者 UUID → 当前连杀数。被击杀（死亡）时清零。内存统计，重启清零。 */
    private final Map<UUID, Integer> killStreaks = new HashMap<>();

    /** 首杀标记：服务器启动后的第一起玩家死亡为 true，触发后置 false。 */
    private boolean firstBloodPending = true;

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

    /** 怪物类型 → 致死方式（武器信息），未列出的怪物不显示武器 */
    private static final Map<EntityType, String> MOB_WEAPON = Map.ofEntries(
            Map.entry(EntityType.ZOMBIE,           "利爪"),
            Map.entry(EntityType.ZOMBIE_VILLAGER,  "利爪"),
            Map.entry(EntityType.HUSK,             "利爪"),
            Map.entry(EntityType.DROWNED,          "利爪"),
            Map.entry(EntityType.SKELETON,         "弓箭"),
            Map.entry(EntityType.STRAY,            "弓箭"),
            Map.entry(EntityType.WITHER_SKELETON,  "弓箭"),
            Map.entry(EntityType.SPIDER,           "毒牙"),
            Map.entry(EntityType.CAVE_SPIDER,      "毒牙"),
            Map.entry(EntityType.ENDERMAN,         "长臂"),
            Map.entry(EntityType.WITCH,            "毒药"),
            Map.entry(EntityType.BLAZE,            "烈焰"),
            Map.entry(EntityType.PIGLIN,           "金剑"),
            Map.entry(EntityType.PIGLIN_BRUTE,     "金斧"),
            Map.entry(EntityType.ZOGLIN,           "獠牙"),
            Map.entry(EntityType.HOGLIN,           "獠牙"),
            Map.entry(EntityType.WARDEN,           "声波"),
            Map.entry(EntityType.IRON_GOLEM,       "铁拳"),
            Map.entry(EntityType.SLIME,            "黏液"),
            Map.entry(EntityType.MAGMA_CUBE,       "岩浆"),
            Map.entry(EntityType.GHAST,            "火球"),
            Map.entry(EntityType.PHANTOM,          "尖喙")
    );

    /** 附魔英文名 → 中文名（用于武器悬浮详情）。未列出的回退为可读英文。 */
    private static final Map<String, String> ENCHANT_CN = Map.ofEntries(
            Map.entry("protection",           "保护"),
            Map.entry("fire_protection",      "火焰保护"),
            Map.entry("feather_falling",      "摔落保护"),
            Map.entry("blast_protection",     "爆炸保护"),
            Map.entry("projectile_protection","弹射物保护"),
            Map.entry("respiration",          "水下呼吸"),
            Map.entry("aqua_affinity",        "水下速掘"),
            Map.entry("thorns",               "荆棘"),
            Map.entry("depth_strider",        "深海探索者"),
            Map.entry("frost_walker",         "冰霜行者"),
            Map.entry("soul_speed",           "灵魂疾行"),
            Map.entry("sharpness",            "锋利"),
            Map.entry("smite",                "亡灵杀手"),
            Map.entry("bane_of_arthropods",   "节肢杀手"),
            Map.entry("knockback",            "击退"),
            Map.entry("fire_aspect",          "火焰附加"),
            Map.entry("looting",              "抢夺"),
            Map.entry("sweeping",             "横扫之刃"),
            Map.entry("efficiency",           "效率"),
            Map.entry("silk_touch",           "精准采集"),
            Map.entry("unbreaking",           "耐久"),
            Map.entry("fortune",              "时运"),
            Map.entry("power",                "力量"),
            Map.entry("punch",                "冲击"),
            Map.entry("flame",                "火矢"),
            Map.entry("infinity",             "无限"),
            Map.entry("luck_of_the_sea",      "海之眷顾"),
            Map.entry("lure",                 "诱饵"),
            Map.entry("loyalty",              "忠诚"),
            Map.entry("impaling",             "穿刺"),
            Map.entry("riptide",              "激流"),
            Map.entry("channeling",           "引雷"),
            Map.entry("multishot",            "多重射击"),
            Map.entry("quick_charge",         "快速装填"),
            Map.entry("piercing",             "穿透"),
            Map.entry("mending",              "经验修补"),
            Map.entry("density",              "密度"),
            Map.entry("breach",               "破甲"),
            Map.entry("wind_burst",           "风爆"),
            Map.entry("curse_of_vanishing",   "消失诅咒"),
            Map.entry("curse_of_binding",     "绑定诅咒")
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

        // 快照死亡时刻的受害者红名状态（在罪恶值变化前取值，供红名死亡播报与击杀反馈判定）
        boolean victimRedAtDeath = plugin.getSinManager().isRedName(player.getUniqueId());

        // 记录最近死亡点（供回溯卷轴使用，不受 enabled 开关影响）
        plugin.getDeathPointManager().recordDeath(player);

        // 记录罪恶值变化（被击杀按 death-reduce 减少罪恶值，受 settings.sin 开关控制）
        plugin.getSinManager().recordDeath(player);

        DeathInfo info = resolveDeathInfo(player);

        // 控制台日志（可选）
        if (cfg.isConsoleLogReason()) {
            EntityDamageEvent lastDamage = player.getLastDamageCause();
            plugin.getLogger().info("[" + player.getName() + "] 死亡分类=" + info.category()
                    + " 原始原因=" + (lastDamage == null ? "UNKNOWN" : lastDamage.getCause().name()));
        }

        // 首杀：服务器启动后的第一起玩家死亡使用专属文案（一次性）
        boolean firstBlood = false;
        if (cfg.isFirstBloodEnabled() && firstBloodPending && cfg.hasCategory("first-blood")) {
            firstBloodPending = false;
            firstBlood = true;
        }

        // 统计连杀数（仅 PVP 且击杀者在线；内存统计，重启清零）
        boolean pvp = "pvp".equals(info.category());
        int streak = 0;
        UUID killerUuid = null;
        if (pvp && info.killerName() != null) {
            Player killer = Bukkit.getPlayerExact(info.killerName());
            if (killer != null) {
                killerUuid = killer.getUniqueId();
                streak = killStreaks.merge(killerUuid, 1, Integer::sum);
                // 红名机制：击杀者增加罪恶值（击杀红名玩家是否免罪由配置控制）
                plugin.getSinManager().recordKill(killer, player);
            }
        }
        // 死亡打断死者自己的连杀
        killStreaks.remove(player.getUniqueId());

        // 选取消息模板：首杀优先；红名玩家死亡优先 red-name-death（未配置则回退正常分类）
        String template;
        if (firstBlood) {
            template = cfg.pickTemplate("first-blood");
        } else if (victimRedAtDeath && cfg.hasCategory("red-name-death")) {
            template = cfg.pickTemplate("red-name-death");
        } else {
            template = cfg.pickTemplate(info.category());
        }
        if (template == null) {
            return;
        }

        // 格式化并广播
        Component message = plugin.getMessageFormatter().format(
                template, player.getName(), info.killerName(), info.weapon(), info.weaponDetail(),
                info.damage(), player.getWorld().getName());

        // 连杀称号（如"三连决胜"）以 Minecraft 标题（屏幕中央大字）展示给广播范围内玩家（仅 PVP）
        if (pvp && killerUuid != null && cfg.isKillStreakEnabled() && streak >= 2) {
            String killStreakTitle = cfg.getKillStreakTitle(streak);
            if (killStreakTitle != null && cfg.isKillStreakTitleEnabled()) {
                var formatter = plugin.getMessageFormatter();
                Component main = formatter.formatExtra(cfg.getKillStreakTitleMain(),
                        player.getName(), info.killerName(), 0, streak, killStreakTitle);
                Component sub = formatter.formatExtra(cfg.getKillStreakTitleSub(),
                        player.getName(), info.killerName(), 0, streak, killStreakTitle);
                Title title = Title.title(main, sub, Title.Times.times(
                        Duration.ofMillis(cfg.getKillStreakTitleFadeIn() * 50L),
                        Duration.ofMillis(cfg.getKillStreakTitleStay() * 50L),
                        Duration.ofMillis(cfg.getKillStreakTitleFadeOut() * 50L)));
                for (Player p : getBroadcastRecipients(player)) {
                    p.showTitle(title);
                }
            }
        }

        event.deathMessage(null);
        broadcast(player, message);

        // 击杀者专属反馈：通过 actionbar 仅向击杀者本人展示（仅 PVP，不进入聊天栏）
        // 击杀红名玩家时优先使用 red-name-killed 文案（未配置则回退 killer-feedback）
        if (pvp && killerUuid != null && cfg.isKillerFeedbackEnabled()) {
            Player killer = Bukkit.getPlayer(killerUuid);
            if (killer != null) {
                String category = victimRedAtDeath && cfg.hasCategory("red-name-killed")
                        ? "red-name-killed"
                        : (cfg.hasCategory("killer-feedback") ? "killer-feedback" : null);
                if (category != null) {
                    String feedback = cfg.pickTemplate(category);
                    if (feedback != null) {
                        killer.sendActionBar(plugin.getMessageFormatter().format(
                                feedback, player.getName(), info.killerName(), info.weapon(), null,
                                info.damage(), player.getWorld().getName()));
                    }
                }
            }
        }

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
            return new DeathInfo(category, null, null, null, damage);
        }

        // 环境伤害
        EntityDamageEvent.DamageCause cause = lastDamage == null ? null : lastDamage.getCause();
        String category = cause == null ? "default" : CAUSE_CATEGORIES.getOrDefault(cause, "default");
        return new DeathInfo(category, null, null, null, damage);
    }

    /**
     * 构建 PVP 死亡信息。
     */
    private DeathInfo pvpInfo(Player killer, Entity projectile, double damage) {
        String weapon = "拳头";
        String weaponDetail = null;
        if (projectile != null) {
            weapon = projectileName(projectile);
            weaponDetail = "<gray>远程攻击</gray><newline><gray>类型: " + weapon + "</gray>";
        } else {
            ItemStack item = killer.getInventory().getItemInMainHand();
            if (item != null && !item.getType().isAir()) {
                weapon = itemName(item);
                weaponDetail = buildItemDetail(item);
            }
        }
        return new DeathInfo("pvp", killer.getName(), weapon, weaponDetail, damage);
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

        String weapon = MOB_WEAPON.get(attacker.getType());
        String weaponDetail = null;
        if (weapon != null) {
            String mob = cnName != null ? cnName : attacker.getType().name().toLowerCase();
            weaponDetail = "<gray>致死方式</gray><newline><gray>攻击者: " + mob
                    + "</gray><newline><gray>方式: " + weapon + "</gray>";
        }
        return new DeathInfo(category, attacker.getName(), weapon, weaponDetail, damage);
    }

    // ==================== 工具方法 ====================

    /**
     * 构造武器物品的悬浮详情（MiniMessage 片段）：附魔列表 + 耐久。
     */
    private String buildItemDetail(ItemStack item) {
        StringBuilder sb = new StringBuilder();
        sb.append("<gray>武器详情</gray>");
        Map<Enchantment, Integer> enchants = item.getEnchantments();
        if (enchants != null && !enchants.isEmpty()) {
            List<String> parts = new ArrayList<>();
            for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
                NamespacedKey key = e.getKey().getKey();
                String name = ENCHANT_CN.getOrDefault(key.getKey(), prettify(key.getKey()));
                parts.add(name + " " + toRoman(e.getValue()));
            }
            sb.append("<newline><yellow>附魔: ").append(String.join(", ", parts)).append("</yellow>");
        }
        int max = item.getType().getMaxDurability();
        if (max > 0) {
            ItemMeta meta = item.getItemMeta();
            int used = (meta instanceof Damageable d) ? d.getDamage() : 0;
            int dur = max - used;
            sb.append("<newline><red>耐久: ").append(dur).append("/").append(max).append("</red>");
        }
        return sb.toString();
    }

    /** 罗马数字（附魔等级显示用）。超过 3999 回退为阿拉伯数字。 */
    private static String toRoman(int value) {
        if (value <= 0 || value > 3999) {
            return String.valueOf(value);
        }
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] symbols = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            while (value >= values[i]) {
                sb.append(symbols[i]);
                value -= values[i];
            }
        }
        return sb.toString();
    }

    /** 把 "fire_protection" 这类 key 转成 "Fire Protection" 可读形式。 */
    private static String prettify(String key) {
        if (key == null || key.isEmpty()) {
            return key;
        }
        StringBuilder sb = new StringBuilder();
        for (String word : key.split("_")) {
            if (word.isEmpty()) continue;
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }

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
     * 按配置的广播模式发送聊天消息。
     */
    private void broadcast(Player player, Component message) {
        getBroadcastRecipients(player).forEach(p -> p.sendMessage(message));
    }

    /**
     * 按配置的广播模式获取接收玩家列表。
     * <p>
     * 聊天播报与 actionbar 广播共用同一套范围规则，避免在多处重复判定逻辑。
     *
     * @param player 死亡玩家（用于确定所在世界与位置）
     * @return 应接收消息的玩家列表（含死亡玩家本人）
     */
    private List<Player> getBroadcastRecipients(Player player) {
        ConfigManager cfg = plugin.getConfigManager();
        return switch (cfg.getBroadcastMode()) {
            case "world" -> new ArrayList<>(player.getWorld().getPlayers());
            case "nearby" -> {
                Location loc = player.getLocation();
                double radiusSquared = cfg.getNearbyRadius() * cfg.getNearbyRadius();
                List<Player> list = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getWorld().equals(player.getWorld())
                            && p.getLocation().distanceSquared(loc) <= radiusSquared) {
                        list.add(p);
                    }
                }
                yield list;
            }
            default -> new ArrayList<>(Bukkit.getOnlinePlayers());
        };
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
            return;
        }

        float volume = cfg.getDeathSoundVolume();
        float pitch = cfg.getDeathSoundPitch();

        switch (cfg.getDeathSoundTarget()) {
            case "victim" ->
                    player.playSound(player.getLocation(), sound, SoundCategory.PLAYERS, volume, pitch);
            case "nearby" ->
                    player.getWorld().playSound(player.getLocation(), sound, SoundCategory.PLAYERS, volume, pitch);
            default ->
                    Bukkit.getOnlinePlayers().forEach(p -> p.playSound(p.getLocation(), sound, SoundCategory.PLAYERS, volume, pitch));
        }
    }

    /**
     * 死亡信息记录。
     *
     * @param category     死亡分类 key
     * @param killerName   击杀者名（无击杀者时为 null）
     * @param weapon       武器显示名（无武器时为 null）
     * @param weaponDetail 武器悬浮详情（MiniMessage 片段，可为 null）
     * @param damage       最终伤害值
     */
    private record DeathInfo(String category, String killerName, String weapon, String weaponDetail, double damage) {}
}

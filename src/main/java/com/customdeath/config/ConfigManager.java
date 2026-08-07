package com.customdeath.config;

import com.customdeath.CustomDeathMessages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 配置管理器。
 * <p>
 * 负责加载 config.yml，管理死亡消息模板，
 * 提供配置项的读取接口。
 */
public class ConfigManager {

    private static final String DEFAULT_CATEGORY = "default";

    private final CustomDeathMessages plugin;
    private YamlConfiguration config;

    /** 死亡分类 → 消息模板列表 */
    private final Map<String, List<String>> messageTemplates = new LinkedHashMap<>();

    /** 死亡分类 → 轮询索引（仅 message-selection=cycle 时使用） */
    private final Map<String, Integer> cycleIndex = new LinkedHashMap<>();

    public ConfigManager(CustomDeathMessages plugin) {
        this.plugin = plugin;
    }

    /**
     * 加载（或重新加载）配置文件。
     */
    public void loadConfig() {
        plugin.saveDefaultConfig();
        config = YamlConfiguration.loadConfiguration(
                new File(plugin.getDataFolder(), "config.yml"));

        messageTemplates.clear();
        cycleIndex.clear();

        ConfigurationSection section = config.getConfigurationSection("messages");
        if (section != null) {
            for (String category : section.getKeys(false)) {
                List<String> templates = section.getStringList(category);
                if (templates.isEmpty()) {
                    continue;
                }
                messageTemplates.put(category, new ArrayList<>(templates));
                cycleIndex.put(category, 0);
            }
        }

        plugin.getLogger().info("配置已加载：" + messageTemplates.size() + " 个死亡分类");
    }

    // ============ 基础设置 ============

    public boolean isEnabled() {
        return config.getBoolean("settings.enabled", true);
    }

    /**
     * @return 是否注册合成配方（回城/回溯卷轴、死亡保护券）
     */
    public boolean isRecipesEnabled() {
        return config.getBoolean("settings.recipes.enabled", true);
    }

    /**
     * @return 广播模式：global / world / nearby
     */
    public String getBroadcastMode() {
        return config.getString("settings.broadcast-mode", "global").toLowerCase();
    }

    public double getNearbyRadius() {
        return config.getDouble("settings.nearby-radius", 64.0);
    }

    /**
     * @return true=随机选择文案，false=按顺序轮询
     */
    public boolean isRandomSelection() {
        return "random".equalsIgnoreCase(config.getString("settings.message-selection", "random"));
    }

    public boolean isConsoleLogReason() {
        return config.getBoolean("settings.console-log-reason", false);
    }

    /**
     * @return 是否在控制台打印最终播报的死亡消息（纯文本）
     */
    public boolean isConsoleLogDeath() {
        return config.getBoolean("settings.console-log-death", true);
    }

    public String getPlayerNameColor() {
        return config.getString("settings.player-name-color", "<yellow>");
    }

    public String getKillerNameColor() {
        return config.getString("settings.killer-name-color", "<red>");
    }

    // ============ 死亡音效 ============

    public boolean isDeathSoundEnabled() {
        return config.getBoolean("settings.death-sound.enabled", true);
    }

    /**
     * 解析配置中的音效类型（Bukkit {@link Sound} 枚举名）。
     *
     * @return 音效枚举，配置名无效或为空时返回 null
     */
    public Sound getDeathSound() {
        String name = config.getString("settings.death-sound.sound", "ENTITY_PLAYER_DEATH");
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return Sound.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public float getDeathSoundVolume() {
        return (float) config.getDouble("settings.death-sound.volume", 1.0);
    }

    public float getDeathSoundPitch() {
        return (float) config.getDouble("settings.death-sound.pitch", 1.0);
    }

    /**
     * @return 音效播放对象：victim / nearby / everyone
     */
    public String getDeathSoundTarget() {
        return config.getString("settings.death-sound.target", "everyone").toLowerCase();
    }

    // ============ 击杀者互动 ============

    public boolean isKillerFeedbackEnabled() {
        return config.getBoolean("settings.killer-feedback.enabled", true);
    }

    public boolean isKillStreakEnabled() {
        return config.getBoolean("settings.kill-streak.enabled", true);
    }

    /**
     * @return 连杀播报模板（含 {player} {killer} {streak} {title} 占位符）
     */
    public String getKillStreakMessage() {
        return config.getString("settings.kill-streak.message",
                " <gold>{killer} <bold><aqua>{title}</aqua></bold>！</gold>");
    }

    /**
     * 是否将连杀称号额外以 Minecraft 标题（屏幕中央大字）展示给广播范围内玩家。
     */
    public boolean isKillStreakTitleEnabled() {
        return config.getBoolean("settings.kill-streak.show-title", true);
    }

    /**
     * 连杀标题的主标题（屏幕中央大字）模板，可用占位符：{player} {killer} {streak} {title}。
     */
    public String getKillStreakTitleMain() {
        return config.getString("settings.kill-streak.title-main",
                "<gold><bold>{title}</bold></gold>");
    }

    /**
     * 连杀标题的副标题（主标题下方小字）模板，可用占位符：{player} {killer} {streak} {title}。
     */
    public String getKillStreakTitleSub() {
        return config.getString("settings.kill-streak.title-sub",
                "<red>{killer}</red> <gray>达成 {streak} 连杀</gray>");
    }

    /** 连杀标题淡入时长（刻，20 刻 = 1 秒）。 */
    public int getKillStreakTitleFadeIn() {
        return config.getInt("settings.kill-streak.title-fade-in", 10);
    }

    /** 连杀标题停留时长（刻，20 刻 = 1 秒）。 */
    public int getKillStreakTitleStay() {
        return config.getInt("settings.kill-streak.title-fade-stay", 70);
    }

    /** 连杀标题淡出时长（刻，20 刻 = 1 秒）。 */
    public int getKillStreakTitleFadeOut() {
        return config.getInt("settings.kill-streak.title-fade-out", 20);
    }

    /**
     * 获取连杀等级称号表（连杀数 → 称号）。
     *
     * @return 连杀数到称号的映射，未配置时返回空 Map
     */
    public Map<Integer, String> getKillStreakTitles() {
        ConfigurationSection section = config.getConfigurationSection("settings.kill-streak.titles");
        if (section == null) {
            return Collections.emptyMap();
        }
        Map<Integer, String> titles = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            try {
                titles.put(Integer.parseInt(key), section.getString(key));
            } catch (NumberFormatException ignored) {
                // 忽略非数字的 key
            }
        }
        return titles;
    }

    /**
     * 获取指定连杀数对应的称号。
     * <p>
     * 精确匹配该连杀数；未命中时取不超过该连杀数的最高档称号
     * （如 6 连杀未单独配置时沿用 5 连杀的称号）。
     *
     * @param streak 当前连杀数
     * @return 称号文本，未配置任何称号时返回 null
     */
    public String getKillStreakTitle(int streak) {
        Map<Integer, String> titles = getKillStreakTitles();
        if (titles.isEmpty()) {
            return null;
        }
        String title = titles.get(streak);
        if (title != null) {
            return title;
        }
        int best = -1;
        for (int level : titles.keySet()) {
            if (level <= streak && level > best) {
                best = level;
            }
        }
        return best < 0 ? null : titles.get(best);
    }

    public boolean isFirstBloodEnabled() {
        return config.getBoolean("settings.first-blood.enabled", true);
    }

    // ============ 卷轴（护身 / 回城 / 回溯，统一物品体系）============

    /** 护身卷轴在物品 PDC 中的标识 key（死亡保护，防重命名误判）。 */
    public static final NamespacedKey SCROLL_PROTECT_KEY = new NamespacedKey("customdeath", "scroll-protect");

    public boolean isScrollProtectEnabled() {
        return config.getBoolean("settings.scrolls.protect.enabled", true);
    }

    public Material getScrollProtectMaterial() {
        return parseMaterial("settings.scrolls.protect.material", Material.PAPER);
    }

    public String getScrollProtectName() {
        return config.getString("settings.scrolls.protect.name", "<yellow><bold>护身卷轴</bold></yellow>");
    }

    public List<String> getScrollProtectLore() {
        return config.getStringList("settings.scrolls.protect.lore");
    }

    public boolean isScrollProtectKeepLevel() {
        return config.getBoolean("settings.scrolls.protect.keep-level", true);
    }

    public String getScrollProtectMessage() {
        return config.getString("settings.scrolls.protect.message",
                "<green><bold>✔</bold> 护身卷轴生效，你免于死亡掉落</green>");
    }

    /** 构造一张（或多张）护身卷轴物品（带 PDC 标识 {@link #SCROLL_PROTECT_KEY}）。 */
    public ItemStack buildScrollProtect(int amount) {
        return buildScrollItem(getScrollProtectMaterial(), getScrollProtectName(), getScrollProtectLore(),
                SCROLL_PROTECT_KEY, amount);
    }

    /** 回城卷轴在物品 PDC 中的标识 key。 */
    public static final NamespacedKey SCROLL_TOWN_KEY = new NamespacedKey("customdeath", "scroll-town");

    /** 回溯卷轴在物品 PDC 中的标识 key。 */
    public static final NamespacedKey SCROLL_RECALL_KEY = new NamespacedKey("customdeath", "scroll-recall");

    public boolean isScrollTownEnabled() {
        return config.getBoolean("settings.scrolls.town.enabled", true);
    }

    public Material getScrollTownMaterial() {
        return parseMaterial("settings.scrolls.town.material", Material.PAPER);
    }

    public String getScrollTownName() {
        return config.getString("settings.scrolls.town.name", "<aqua><bold>回城卷轴</bold></aqua>");
    }

    public List<String> getScrollTownLore() {
        return config.getStringList("settings.scrolls.town.lore");
    }

    public String getScrollTownMessage() {
        return config.getString("settings.scrolls.town.message", "<aqua>你回到了家中</aqua>");
    }

    /**
     * @return 回城卷轴施法倒计时秒数（0 = 立即传送，不读条）
     */
    public int getScrollTownCastTime() {
        return Math.max(0, config.getInt("settings.scrolls.town.cast-time", 5));
    }

    /**
     * @return 回城卷轴施法期间移动是否打断施法
     */
    public boolean isScrollTownCancelOnMove() {
        return config.getBoolean("settings.scrolls.town.cancel-on-move", true);
    }

    /**
     * @return 回城卷轴开始施法时的提示（支持 {time} 占位符，为总秒数）
     */
    public String getScrollTownCastStartMessage() {
        return config.getString("settings.scrolls.town.cast-start-message",
                "<aqua>开始回城，请勿移动（{time} 秒）...</aqua>");
    }

    /**
     * @return 回城卷轴施法被打断时的提示
     */
    public String getScrollTownCastCancelMessage() {
        return config.getString("settings.scrolls.town.cast-cancel-message",
                "<red>移动打断了回城，卷轴未消耗</red>");
    }

    /**
     * @return 回城卷轴倒计时期间 actionbar 提示（支持 {time} 占位符，为剩余秒数）
     */
    public String getScrollTownCountdownActionbar() {
        return config.getString("settings.scrolls.town.countdown-actionbar",
                "<aqua>回城传送中... {time} 秒</aqua>");
    }

    public boolean isScrollRecallEnabled() {
        return config.getBoolean("settings.scrolls.recall.enabled", true);
    }

    public Material getScrollRecallMaterial() {
        return parseMaterial("settings.scrolls.recall.material", Material.PAPER);
    }

    public String getScrollRecallName() {
        return config.getString("settings.scrolls.recall.name", "<light_purple><bold>回溯卷轴</bold></light_purple>");
    }

    public List<String> getScrollRecallLore() {
        return config.getStringList("settings.scrolls.recall.lore");
    }

    public String getScrollRecallMessage() {
        return config.getString("settings.scrolls.recall.message", "<light_purple>你回到了死亡地点</light_purple>");
    }

    /**
     * @return 回溯卷轴施法倒计时秒数（0 = 立即传送，不读条）
     */
    public int getScrollRecallCastTime() {
        return Math.max(0, config.getInt("settings.scrolls.recall.cast-time", 5));
    }

    /**
     * @return 回溯卷轴施法期间移动是否打断施法
     */
    public boolean isScrollRecallCancelOnMove() {
        return config.getBoolean("settings.scrolls.recall.cancel-on-move", true);
    }

    /**
     * @return 回溯卷轴开始施法时的提示（支持 {time} 占位符，为总秒数）
     */
    public String getScrollRecallCastStartMessage() {
        return config.getString("settings.scrolls.recall.cast-start-message",
                "<light_purple>开始回溯，请勿移动（{time} 秒）...</light_purple>");
    }

    /**
     * @return 回溯卷轴施法被打断时的提示
     */
    public String getScrollRecallCastCancelMessage() {
        return config.getString("settings.scrolls.recall.cast-cancel-message",
                "<red>移动打断了回溯，卷轴未消耗</red>");
    }

    /**
     * @return 回溯卷轴倒计时期间 actionbar 提示（支持 {time} 占位符，为剩余秒数）
     */
    public String getScrollRecallCountdownActionbar() {
        return config.getString("settings.scrolls.recall.countdown-actionbar",
                "<light_purple>回溯传送中... {time} 秒</light_purple>");
    }

    // ============ 卷轴施法表现（冷却条 / 粒子特效）============

    /**
     * @return 施法期间是否显示物品栏冷却条（类似末影珍珠的冷却动画，与 cast-time 一致）
     */
    public boolean isScrollCooldownEnabled() {
        return config.getBoolean("settings.scrolls.cast-effect.cooldown-enabled", true);
    }

    /**
     * @return 施法期间是否播放传送粒子特效
     */
    public boolean isScrollParticleEnabled() {
        return config.getBoolean("settings.scrolls.cast-effect.particle-enabled", true);
    }

    /**
     * 解析施法粒子类型（Bukkit {@link Particle} 枚举名）。
     *
     * @return 粒子枚举，配置名无效或为空时返回 null（不播放粒子）
     */
    public Particle getScrollCastParticle() {
        String name = config.getString("settings.scrolls.cast-effect.particle", "PORTAL");
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return Particle.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * @return 施法粒子每波数量（环绕 + 光环总数），至少 1
     */
    public int getScrollCastParticleAmount() {
        return Math.max(1, config.getInt("settings.scrolls.cast-effect.particle-amount", 20));
    }

    /**
     * 构造一张（或多张）回城卷轴物品（带 PDC 标识 {@link #SCROLL_TOWN_KEY}）。
     */
    public ItemStack buildScrollTown(int amount) {
        return buildScrollItem(getScrollTownMaterial(), getScrollTownName(), getScrollTownLore(),
                SCROLL_TOWN_KEY, amount);
    }

    /**
     * 构造一张（或多张）回溯卷轴物品（带 PDC 标识 {@link #SCROLL_RECALL_KEY}）。
     */
    public ItemStack buildScrollRecall(int amount) {
        return buildScrollItem(getScrollRecallMaterial(), getScrollRecallName(), getScrollRecallLore(),
                SCROLL_RECALL_KEY, amount);
    }

    /** 解析配置中的 Material 枚举名，无效时回退默认值。 */
    private Material parseMaterial(String path, Material fallback) {
        String name = config.getString(path);
        if (name == null || name.isBlank()) {
            return fallback;
        }
        try {
            return Material.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    /** 构造带名称、描述与 PDC 标识的物品（卷轴通用）。 */
    private ItemStack buildScrollItem(Material material, String name, List<String> lore,
                                      NamespacedKey key, int amount) {
        ItemStack item = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null && !name.isBlank()) {
                meta.displayName(MiniMessage.miniMessage().deserialize(name));
            }
            if (lore != null && !lore.isEmpty()) {
                List<Component> loreComponents = new ArrayList<>(lore.size());
                for (String line : lore) {
                    loreComponents.add(MiniMessage.miniMessage().deserialize(line));
                }
                meta.lore(loreComponents);
            }
            meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    // ============ 消息模板 ============

    public boolean hasCategory(String category) {
        return messageTemplates.containsKey(category);
    }

    /**
     * 获取指定分类的模板列表，不存在时回退到 default。
     */
    public List<String> getTemplates(String category) {
        List<String> templates = messageTemplates.get(category);
        if (templates == null || templates.isEmpty()) {
            templates = messageTemplates.get(DEFAULT_CATEGORY);
        }
        return templates == null ? Collections.emptyList() : templates;
    }

    /**
     * 按配置策略（随机/轮询）从指定分类中挑选一条模板。
     *
     * @return 选中的模板字符串，无可用模板时返回 null
     */
    public String pickTemplate(String category) {
        List<String> templates = getTemplates(category);
        if (templates.isEmpty()) {
            return null;
        }
        if (templates.size() == 1) {
            return templates.get(0);
        }
        if (isRandomSelection()) {
            return templates.get(ThreadLocalRandom.current().nextInt(templates.size()));
        }
        // 轮询模式
        int index = cycleIndex.getOrDefault(category, 0);
        String picked = templates.get(index);
        cycleIndex.put(category, (index + 1) % templates.size());
        return picked;
    }

    public YamlConfiguration getRawConfig() {
        return config;
    }
}

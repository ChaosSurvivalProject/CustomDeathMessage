package com.customdeath.config;

import com.customdeath.CustomDeathMessages;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

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

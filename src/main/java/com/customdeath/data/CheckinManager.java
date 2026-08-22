package com.customdeath.data;

import com.customdeath.CustomDeathMessages;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 每日签到记录管理器。
 * <p>
 * 记录每位玩家最近一次签到的自然日（服务器本地时区），
 * 数据持久化到 {@code plugins/CustomDeathMessages/data/checkins.yml}，
 * 服务器重启后记录不丢失。
 * 「第二天 0 点自动重置」通过日期比较天然实现——跨天后
 * {@link #hasCheckedInToday} 自然返回 false，无需任何定时任务。
 */
public class CheckinManager {

    private final CustomDeathMessages plugin;
    private final Map<UUID, LocalDate> checkins = new HashMap<>();
    private File dataFile;
    private YamlConfiguration data;

    public CheckinManager(CustomDeathMessages plugin) {
        this.plugin = plugin;
        load();
    }

    /**
     * 判断玩家今天（服务器自然日）是否已签到。
     *
     * @param playerId 玩家 UUID
     * @return true = 今天已签到，不可重复签到
     */
    public boolean hasCheckedInToday(UUID playerId) {
        LocalDate last = checkins.get(playerId);
        return last != null && last.equals(LocalDate.now());
    }

    /**
     * 将玩家标记为今天已签到，并立即落盘。
     *
     * @param playerId 玩家 UUID
     */
    public void markCheckedIn(UUID playerId) {
        LocalDate today = LocalDate.now();
        checkins.put(playerId, today);
        data.set(playerId.toString(), today.toString());
        save();
    }

    /**
     * 重置玩家的签到状态：清除其今日签到记录，允许当天重新签到，并立即落盘。
     *
     * @param playerId 玩家 UUID
     * @return true = 存在记录并已清除；false = 该玩家今天本就未签到
     */
    public boolean resetCheckedIn(UUID playerId) {
        if (checkins.remove(playerId) == null) {
            return false;
        }
        data.set(playerId.toString(), null);
        save();
        return true;
    }

    // ==================== 持久化 ====================

    /** 从磁盘加载签到记录。 */
    private void load() {
        File dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            plugin.getLogger().warning("无法创建数据目录：" + dataFolder.getAbsolutePath());
            return;
        }
        dataFile = new File(dataFolder, "checkins.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);

        checkins.clear();
        for (String key : data.getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(key);
                checkins.put(playerId, LocalDate.parse(data.getString(key, "")));
            } catch (IllegalArgumentException ignored) {
                // 跳过损坏的记录（DateTimeParseException 为其子类，一并覆盖）
            }
        }
    }

    /** 将内存中的记录写入磁盘。 */
    private void save() {
        if (dataFile == null || data == null) {
            return;
        }
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("保存签到记录失败：" + e.getMessage());
        }
    }
}

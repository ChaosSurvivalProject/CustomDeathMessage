package com.customdeath.data;

import com.customdeath.CustomDeathMessages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 死亡点记录管理器。
 * <p>
 * 记录每位玩家最近一次的死亡位置，供「回溯卷轴」使用。
 * 数据持久化到 {@code plugins/CustomDeathMessages/data/death-points.yml}，
 * 服务器重启后记录不丢失。
 */
public class DeathPointManager {

    private final CustomDeathMessages plugin;
    private final Map<UUID, DeathPoint> deathPoints = new HashMap<>();
    private File dataFile;
    private YamlConfiguration data;

    /**
     * 死亡点记录（世界名 + 坐标）。
     */
    public record DeathPoint(String world, double x, double y, double z, float yaw, float pitch) {

        /**
         * @return 还原为 Bukkit Location；世界不存在时返回 null
         */
        public Location toLocation() {
            World world = Bukkit.getWorld(this.world);
            if (world == null) {
                return null;
            }
            return new Location(world, x, y, z, yaw, pitch);
        }
    }

    public DeathPointManager(CustomDeathMessages plugin) {
        this.plugin = plugin;
        load();
    }

    /**
     * 记录玩家最近一次死亡位置，并立即落盘。
     */
    public void recordDeath(Player player) {
        Location loc = player.getLocation();
        DeathPoint point = new DeathPoint(
                loc.getWorld() == null ? "" : loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
        deathPoints.put(player.getUniqueId(), point);
        data.set(player.getUniqueId().toString(), pointToString(point));
        save();
    }

    /**
     * 获取玩家最近一次死亡点。
     *
     * @return 死亡点 Location；无记录或世界不可用时返回 null
     */
    public Location getRecentDeathPoint(UUID playerId) {
        DeathPoint point = deathPoints.get(playerId);
        return point == null ? null : point.toLocation();
    }

    // ==================== 持久化 ====================

    /** 从磁盘加载死亡点记录。 */
    private void load() {
        File dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            plugin.getLogger().warning("无法创建数据目录：" + dataFolder.getAbsolutePath());
            return;
        }
        dataFile = new File(dataFolder, "death-points.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);

        deathPoints.clear();
        for (String key : data.getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(key);
                String[] parts = data.getString(key, "").split(",");
                if (parts.length < 6) {
                    continue;
                }
                deathPoints.put(playerId, new DeathPoint(
                        parts[0],
                        Double.parseDouble(parts[1]),
                        Double.parseDouble(parts[2]),
                        Double.parseDouble(parts[3]),
                        Float.parseFloat(parts[4]),
                        Float.parseFloat(parts[5])));
            } catch (IllegalArgumentException ignored) {
                // 跳过损坏的记录（NumberFormatException 为其子类，一并覆盖）
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
            plugin.getLogger().warning("保存死亡点记录失败：" + e.getMessage());
        }
    }

    /** 把死亡点序列化为逗号分隔字符串（world,x,y,z,yaw,pitch）。 */
    private String pointToString(DeathPoint point) {
        return point.world() + "," + point.x() + "," + point.y() + "," + point.z()
                + "," + point.yaw() + "," + point.pitch();
    }
}

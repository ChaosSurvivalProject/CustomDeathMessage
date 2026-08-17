package com.customdeath.data;

import com.customdeath.CustomDeathMessages;
import com.customdeath.config.ConfigManager;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 红名机制（罪恶值）管理器。
 * <p>
 * 玩家 PVP 击杀积累罪恶值，达到阈值后进入红名状态（头顶/Tab 名字变红、可选发光）；
 * 击杀红名玩家不增加罪恶值（除魔卫道）；罪恶值随时间消退（在线消退或下线冻结），
 * 低过阈值即退出红名。状态由罪恶值与阈值纯函数决定，任何变化后立即重算并触发迁移文案。
 * <p>
 * 数据持久化到 {@code plugins/CustomDeathMessages/data/sins.yml}，重启不丢。
 * 消退按每个玩家独立的 {@code last-decay} 时间戳计算，可精确覆盖在线/离线两种模式。
 * 阈值与文案全部经 {@link ConfigManager} 实时读取，重载配置即时生效。
 */
public class SinManager {

    /** 红名计分板队伍名（名字变红用）。Bukkit 队伍名有长度上限，需保持简短。 */
    private static final String RED_TEAM_NAME = "cdm_red";

    private final CustomDeathMessages plugin;

    /** 玩家 UUID → 罪恶值记录。 */
    private final Map<UUID, SinEntry> sins = new HashMap<>();

    /** 是否有未落盘的改动（批量落盘用）。 */
    private boolean dirty;

    private File dataFile;
    private YamlConfiguration data;

    /** 时间消退调度任务（每秒检查一次）。 */
    private BukkitTask decayTask;
    /** 临界/红名 actionbar 预警调度任务。 */
    private BukkitTask warningTask;

    /**
     * 一名玩家的罪恶值记录。
     *
     * @param sin       当前罪恶值
     * @param lastDecay 上次消退应用时刻（毫秒时间戳，用于计算经过的消退间隔）
     */
    private record SinEntry(int sin, long lastDecay) {
    }

    /** 红名状态（由罪恶值与阈值纯函数决定）。 */
    public enum SinState {
        /** 正常（罪恶值低于预警线）。 */
        NORMAL,
        /** 红名临界预警区（罪恶值已达阈值的一定比例）。 */
        WARNING,
        /** 红名（罪恶值达到阈值）。 */
        RED
    }

    public SinManager(CustomDeathMessages plugin) {
        this.plugin = plugin;
        load();
    }

    // ==================== 罪恶值与状态判定 ====================

    /**
     * 获取玩家当前罪恶值。
     *
     * @param playerId 玩家 UUID
     * @return 罪恶值（无记录时为 0）
     */
    public int getSin(UUID playerId) {
        SinEntry entry = sins.get(playerId);
        return entry == null ? 0 : entry.sin();
    }

    /**
     * 判断玩家当前红名状态。
     * <p>
     * RED：罪恶值 &gt;= 红名阈值；WARNING：罪恶值已达阈值的预警比例；否则 NORMAL。
     * 红名机制关闭时恒为 NORMAL。
     */
    public SinState getState(UUID playerId) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isSinEnabled()) {
            return SinState.NORMAL;
        }
        int sin = getSin(playerId);
        int threshold = cfg.getSinRedThreshold();
        if (sin >= threshold) {
            return SinState.RED;
        }
        if (sin >= warnLine(cfg)) {
            return SinState.WARNING;
        }
        return SinState.NORMAL;
    }

    /**
     * @return 玩家是否为红名（罪恶值 &gt;= 红名阈值；机制关闭时为 false）
     */
    public boolean isRedName(UUID playerId) {
        ConfigManager cfg = plugin.getConfigManager();
        return cfg.isSinEnabled() && getSin(playerId) >= cfg.getSinRedThreshold();
    }

    /** 红名临界预警线（罪恶值达到阈值的 {@code warning-ratio} 比例）。 */
    private int warnLine(ConfigManager cfg) {
        return (int) Math.floor(cfg.getSinRedThreshold() * cfg.getSinWarningRatio());
    }

    // ==================== 罪恶值变化入口 ====================

    /**
     * 记录一次 PVP 击杀：击杀者增加罪恶值。
     * <p>
     * 若配置 {@code no-sin-kill-red} 且受害者已是红名，则不增加罪恶值（除魔卫道），
     * 并向击杀者发送专属提示。罪恶值变化后重算状态并触发迁移文案/红名外显。
     *
     * @param killer 击杀者（在线玩家）
     * @param victim 被击杀的玩家
     */
    public void recordKill(Player killer, Player victim) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isSinEnabled()) {
            return;
        }
        UUID killerId = killer.getUniqueId();

        // 击杀红名玩家不增加罪恶值（除魔卫道）
        if (cfg.isSinNoKillRed() && isRedName(victim.getUniqueId())) {
            String msg = cfg.getSinMessage("kill-red-no-sin",
                    "<green><bold>⚔</bold> 你除掉了红名玩家 <red>{victim}</red>，不增加罪恶值</green>");
            if (!msg.isBlank()) {
                sendMessage(killer, msg, placeholders(killer, victim.getName(), 0));
            }
            return;
        }

        int gain = cfg.getSinKillGain();
        int oldSin = getSin(killerId);
        int newSin = clamp(oldSin + gain);
        SinState oldState = getState(killerId);
        mutate(killerId, newSin, true);
        SinState newState = getState(killerId);

        // 击杀提示（默认留空不提示，避免刷屏）
        String killMsg = cfg.getSinMessage("kill-increase", "");
        if (!killMsg.isBlank()) {
            sendMessage(killer, killMsg, placeholders(killer, victim.getName(), gain));
        }

        fireTransitions(killer, oldState, newState, cfg);
    }

    /**
     * 记录一次死亡。
     * <p>
     * 红名玩家死亡（任意死因）时，若配置 {@code red-death-clear}（默认开）则罪恶值清零
     * （以命赎罪、直接洗白），并发送专属赎罪提示；
     * 否则按 {@code death-reduce} 减少罪恶值（0 = 不减少）。减少后重算状态并触发迁移文案/红名外显。
     *
     * @param victim 死亡的玩家
     */
    public void recordDeath(Player victim) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isSinEnabled()) {
            return;
        }
        UUID victimId = victim.getUniqueId();
        SinState oldState = getState(victimId);

        // 红名玩家死亡：罪恶值清零（以命赎罪），发送专属洗白提示并移除红名外显
        if (oldState == SinState.RED && cfg.isSinRedDeathClear()) {
            int cleared = getSin(victimId);
            mutate(victimId, 0, true);
            String msg = cfg.getSinMessage("red-death-clear",
                    "<green><bold>✔</bold> 你以生命赎清了 <red>{amount}</red> 点罪恶值，成功洗白</green>");
            if (!msg.isBlank()) {
                sendMessage(victim, msg, placeholders(victim, null, cleared));
            }
            // 只更新红名外显（移除），不再走通用迁移文案（已发赎罪提示）
            applyRedVisual(victim, false);
            return;
        }

        // 普通死亡按 death-reduce 减少罪恶值（0 = 不减少）
        int reduce = cfg.getSinDeathReduce();
        if (reduce > 0) {
            mutate(victimId, clamp(getSin(victimId) - reduce), true);
            SinState newState = getState(victimId);

            String msg = cfg.getSinMessage("death-reduce",
                    "<gray>你被击杀，罪恶值 -{amount}（当前 {sin}）</gray>");
            if (!msg.isBlank()) {
                sendMessage(victim, msg, placeholders(victim, null, reduce));
            }
            fireTransitions(victim, oldState, newState, cfg);
        }
    }

    /**
     * 直接设置玩家罪恶值（管理命令，目标须在线）。
     * 变化后重算状态并触发迁移文案/红名外显。
     */
    public void setSin(Player target, int value) {
        UUID id = target.getUniqueId();
        SinState oldState = getState(id);
        mutate(id, clamp(value), true);
        SinState newState = getState(id);
        fireTransitions(target, oldState, newState, plugin.getConfigManager());
    }

    /** 增加（或减少，amount 可为负）玩家罪恶值（管理命令）。 */
    public void addSin(Player target, int amount) {
        setSin(target, getSin(target.getUniqueId()) + amount);
    }

    /** 将罪恶值钳制到 [0, max-sin]（max-sin=0 时不设上限）。 */
    private int clamp(int value) {
        int max = plugin.getConfigManager().getSinMax();
        if (max > 0 && value > max) {
            return max;
        }
        return Math.max(0, value);
    }

    /**
     * 变更玩家的罪恶值（保留已有 last-decay，无记录时以当前时间作为起始）。
     */
    private void mutate(UUID uuid, int newSin, boolean markDirty) {
        long last = sins.containsKey(uuid) ? sins.get(uuid).lastDecay() : System.currentTimeMillis();
        sins.put(uuid, new SinEntry(clamp(newSin), last));
        if (markDirty) {
            dirty = true;
        }
    }

    // ==================== 状态迁移与文案 ====================

    /**
     * 状态迁移后的统一处理：按「旧状态 → 新状态」发送对应文案，并在红名进出时切换外显。
     *
     * @param player  玩家（离线时仅做可执行的外显清理，不发文案）
     * @param oldState 迁移前的状态
     * @param newState 迁移后的状态
     * @param cfg      配置管理器
     */
    private void fireTransitions(Player player, SinState oldState, SinState newState, ConfigManager cfg) {
        if (oldState == newState) {
            return;
        }
        boolean wasRed = oldState == SinState.RED;
        boolean nowRed = newState == SinState.RED;

        if (player != null && player.isOnline()) {
            if (!wasRed && nowRed) {
                sendMessage(player, cfg.getSinMessage("red-enter",
                        "<dark_red><bold>☠ 红名警告</bold></dark_red> 你的罪恶值已达 <red>{threshold}</red>，你已成为红名玩家！"),
                        placeholders(player, null, 0));
            } else if (wasRed && !nowRed) {
                sendMessage(player, cfg.getSinMessage("red-exit",
                        "<green><bold>✔</bold> 你的罪恶值已消退至 <yellow>{threshold}</yellow> 以下，成功洗白</green>"),
                        placeholders(player, null, 0));
            } else if (!wasRed && newState == SinState.WARNING && cfg.isSinWarningEnabled() && messageMode(cfg)) {
                sendMessage(player, cfg.getSinMessage("warning-enter",
                        "<red><bold>⚠ 预警</bold></red> 你的罪恶值已达 <yellow>{sin}/{threshold}</yellow>，再增加 <yellow>{remaining}</yellow> 点将进入红名状态！"),
                        placeholders(player, null, 0));
            } else if (oldState == SinState.WARNING && newState == SinState.NORMAL) {
                String msg = cfg.getSinMessage("warning-exit", "");
                if (!msg.isBlank()) {
                    sendMessage(player, msg, placeholders(player, null, 0));
                }
            }
        }

        // 红名进出时切换外显（计分板名字变色 / 发光）
        if (wasRed != nowRed) {
            applyRedVisual(player, nowRed);
        }
    }

    /** 预警方式是否含聊天消息。 */
    private boolean messageMode(ConfigManager cfg) {
        return "message".equals(cfg.getSinWarningMode()) || "both".equals(cfg.getSinWarningMode());
    }

    /** 预警方式是否含 actionbar。 */
    private boolean actionbarMode(ConfigManager cfg) {
        return "actionbar".equals(cfg.getSinWarningMode()) || "both".equals(cfg.getSinWarningMode());
    }

    /**
     * 组装罪恶值文案占位符映射（{sin} {threshold} {amount} {remaining} {victim}）。
     */
    private Map<String, String> placeholders(Player player, String victimName, int amount) {
        ConfigManager cfg = plugin.getConfigManager();
        int sin = getSin(player.getUniqueId());
        int threshold = cfg.getSinRedThreshold();
        Map<String, String> map = new HashMap<>();
        if (victimName != null) {
            map.put("victim", victimName);
        }
        map.put("sin", String.valueOf(sin));
        map.put("threshold", String.valueOf(threshold));
        map.put("amount", String.valueOf(amount));
        map.put("remaining", String.valueOf(Math.max(0, threshold - sin)));
        return map;
    }

    /** 发送罪恶值提示文案（MiniMessage，经 MessageFormatter 解析，失败降级纯文本）。 */
    private void sendMessage(Player player, String template, Map<String, String> placeholders) {
        if (template == null || template.isBlank()) {
            return;
        }
        player.sendMessage(plugin.getMessageFormatter().formatWith(template, placeholders));
    }

    // ==================== 红名外显（计分板名字变色 / 发光）====================

    /**
     * 设置（或清除）玩家的红名外显。
     * <p>
     * 名字变色通过主计分板的专用队伍（{@link #RED_TEAM_NAME}）实现，
     * 队伍颜色为红色，进红名加入、退出移除，覆盖头顶名牌与玩家列表(Tab)。
     * 计分板不可用（某些环境）时降级跳过外显，不影响罪恶值逻辑。
     */
    private void applyRedVisual(Player player, boolean red) {
        if (player == null) {
            return;
        }
        ConfigManager cfg = plugin.getConfigManager();

        if (cfg.isSinNameTagRed()) {
            var manager = Bukkit.getScoreboardManager();
            Scoreboard board = manager == null ? null : manager.getMainScoreboard();
            if (board != null) {
                Team team = board.getTeam(RED_TEAM_NAME);
                if (team == null) {
                    try {
                        team = board.registerNewTeam(RED_TEAM_NAME);
                    } catch (IllegalStateException e) {
                        team = board.getTeam(RED_TEAM_NAME);
                    }
                }
                if (team != null) {
                    team.color(NamedTextColor.RED);
                    if (red) {
                        team.addEntry(player.getName());
                    } else {
                        team.removeEntry(player.getName());
                    }
                }
            }
        }

        // 发光：配置开启时跟随红名状态，关闭时确保清除（避免残留发光）
        if (cfg.isSinGlow()) {
            player.setGlowing(red);
        } else {
            player.setGlowing(false);
        }
    }

    // ==================== 进服 / 退出 ====================

    /**
     * 玩家进服时调用：非在线消退模式下补算离线/停机期间的消退；
     * 同步红名外显（覆盖离线期间的状态变化）；若处于红名/临界区则发送进服状态提示。
     */
    public void onJoin(Player player) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isSinEnabled()) {
            return;
        }
        UUID id = player.getUniqueId();

        // 仅非在线消退模式需要补算离线/停机期间的消退（在线模式已在退出/启动时冻结时间戳）
        if (cfg.isSinDecayEnabled() && !cfg.isSinDecayOnlyOnline()) {
            applyElapsedDecay(id, System.currentTimeMillis());
        }

        // 进服同步红名外显（离线期间可能已退出红名）
        applyRedVisual(player, isRedName(id));

        // 进服状态提示（红名 / 临界）
        SinState state = getState(id);
        if (state != SinState.NORMAL) {
            String msg = cfg.getSinMessage("join-status",
                    "<red>你当前为红名玩家（罪恶值 {sin}/{threshold}），击杀你的玩家不增加罪恶值</red>");
            if (!msg.isBlank()) {
                sendMessage(player, msg, placeholders(player, null, 0));
            }
        }
    }

    /**
     * 玩家退出时调用：仅在线消退模式下将消退时间戳快照为当前时间（下线冻结）；
     * 移除红名外显；立即落盘。
     */
    public void onQuit(Player player) {
        UUID id = player.getUniqueId();
        if (plugin.getConfigManager().isSinDecayOnlyOnline()) {
            long now = System.currentTimeMillis();
            SinEntry entry = sins.get(id);
            if (entry != null) {
                sins.put(id, new SinEntry(entry.sin(), now));
                dirty = true;
            }
        }
        applyRedVisual(player, false);
        save();
    }

    // ==================== 时间消退 ====================

    /**
     * 按玩家上次消退时间戳计算并应用应消退的罪恶值。
     * <p>
     * 消退间隔内只计入完整经过的间隔数（保留余数到下次），避免累积误差。
     * 在线模式下离线玩家的时间戳已在退出时冻结，故离线时间不会被计入。
     */
    private void applyElapsedDecay(UUID uuid, long now) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isSinDecayEnabled() || cfg.getSinDecayAmount() <= 0) {
            return;
        }
        SinEntry entry = sins.get(uuid);
        if (entry == null) {
            return;
        }
        long intervalMs = cfg.getSinDecayIntervalSeconds() * 1000L;
        if (intervalMs <= 0) {
            return;
        }
        long elapsed = now - entry.lastDecay();
        if (elapsed <= 0) {
            return;
        }
        long intervals = elapsed / intervalMs;
        if (intervals <= 0) {
            return;
        }
        int reduction = (int) (cfg.getSinDecayAmount() * intervals);
        int newSin = Math.max(0, entry.sin() - reduction);
        long newLastDecay = entry.lastDecay() + intervals * intervalMs;
        sins.put(uuid, new SinEntry(newSin, newLastDecay));
        dirty = true;
    }

    /**
     * 时间消退 tick（每秒一次）：
     * 在线模式下仅对在线玩家消退；非在线模式下对所有有记录的玩家消退。
     */
    private void decayTick() {
        ConfigManager cfg = plugin.getConfigManager();
        long now = System.currentTimeMillis();
        if (cfg.isSinDecayOnlyOnline()) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                applyElapsedDecay(p.getUniqueId(), now);
            }
        } else {
            for (UUID id : new ArrayList<>(sins.keySet())) {
                applyElapsedDecay(id, now);
            }
        }
    }

    // ==================== 临界/红名 actionbar 预警 ====================

    /**
     * 预警 actionbar tick：给所有处于临界预警区或红名状态的在线玩家周期性推送罪恶值提示。
     */
    private void warningTick() {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isSinEnabled() || !cfg.isSinWarningEnabled() || !actionbarMode(cfg)) {
            return;
        }
        String template = cfg.getSinMessage("warning-actionbar",
                "<red>罪恶值 {sin}/{threshold}，红名临界！</red>");
        if (template.isBlank()) {
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            SinState state = getState(p.getUniqueId());
            if (state == SinState.WARNING || state == SinState.RED) {
                p.sendActionBar(plugin.getMessageFormatter().formatWith(template, placeholders(p, null, 0)));
            }
        }
    }

    // ==================== 调度任务 ====================

    /** 启动消退与预警调度任务（先取消旧任务，可安全重载调用）。 */
    public void startTasks() {
        stopTasks();
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isSinEnabled()) {
            return;
        }
        if (cfg.isSinDecayEnabled()) {
            decayTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                try {
                    decayTick();
                    saveIfDirty();
                } catch (Exception e) {
                    plugin.getLogger().warning("罪恶值消退任务异常：" + e.getMessage());
                }
            }, 20L, 20L);
        }
        if (cfg.isSinWarningEnabled() && actionbarMode(cfg)) {
            long period = 20L * cfg.getSinWarningActionbarIntervalSeconds();
            warningTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                try {
                    warningTick();
                } catch (Exception e) {
                    plugin.getLogger().warning("红名预警 actionbar 任务异常：" + e.getMessage());
                }
            }, period, period);
        }
    }

    /** 取消消退与预警调度任务。 */
    public void stopTasks() {
        if (decayTask != null) {
            decayTask.cancel();
            decayTask = null;
        }
        if (warningTask != null) {
            warningTask.cancel();
            warningTask = null;
        }
    }

    /** 插件卸载时调用：停止任务并强制落盘。 */
    public void shutdown() {
        stopTasks();
        save();
    }

    // ==================== 持久化 ====================

    /**
     * 从磁盘加载罪恶值记录。
     * <p>
     * 仅在线消退模式下，加载时将全部记录的时间戳快照为当前时间，
     * 保证服务器停机期间不计入消退。
     */
    private void load() {
        File dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            plugin.getLogger().warning("无法创建数据目录：" + dataFolder.getAbsolutePath());
            return;
        }
        dataFile = new File(dataFolder, "sins.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);

        sins.clear();
        ConfigurationSection section = data.getConfigurationSection("players");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(key);
                    String path = "players." + key;
                    int sin = Math.max(0, data.getInt(path + ".sin", 0));
                    long lastDecay = data.getLong(path + ".last-decay", System.currentTimeMillis());
                    sins.put(playerId, new SinEntry(sin, lastDecay));
                } catch (IllegalArgumentException e) {
                    // 跳过损坏的记录（UUID 解析失败等）
                    plugin.getLogger().warning("跳过损坏的罪恶值记录：" + key);
                }
            }
        }

        // 仅在线消退：启动即快照时间戳，停机时间不计入消退
        if (plugin.getConfigManager().isSinDecayOnlyOnline()) {
            long now = System.currentTimeMillis();
            for (UUID id : new ArrayList<>(sins.keySet())) {
                SinEntry entry = sins.get(id);
                sins.put(id, new SinEntry(entry.sin(), now));
            }
        }
    }

    /** 将有改动时落盘（批量落盘，避免每次击杀/消退都写文件）。 */
    private void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    /** 将内存中的罪恶值记录写入磁盘。 */
    private void save() {
        if (dataFile == null || data == null) {
            return;
        }
        data.set("players", null);
        for (Map.Entry<UUID, SinEntry> e : sins.entrySet()) {
            String path = "players." + e.getKey();
            data.set(path + ".sin", e.getValue().sin());
            data.set(path + ".last-decay", e.getValue().lastDecay());
        }
        try {
            data.save(dataFile);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().warning("保存罪恶值记录失败：" + e.getMessage());
        }
    }
}

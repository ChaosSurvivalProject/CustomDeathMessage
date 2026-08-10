package com.customdeath.listener;

import com.customdeath.CustomDeathMessages;
import com.customdeath.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * 进服签到提示监听器。
 * <p>
 * 玩家加入服务器时，若当天尚未签到，则在聊天栏发送一条提示：
 * <ul>
 *   <li>Java 玩家：可点击文本（MiniMessage {@code <click:run_command:'/customdeath checkin'>}
 *       标签，模板见 {@code settings.checkin.join-hint-message}），点击即执行签到命令；</li>
 *   <li>基岩版玩家（经 Geyser 连接）：基岩端不支持聊天点击交互，改提示手动输入指令
 *       （模板见 {@code settings.checkin.join-hint-message-bedrock}）。</li>
 * </ul>
 */
public class CheckinListener implements Listener {

    private final CustomDeathMessages plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public CheckinListener(CustomDeathMessages plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isCheckinEnabled() || !cfg.isCheckinJoinHintEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        // 延迟 1 秒：Geyser 基岩版玩家会话建立较晚，立即发送可能丢失（与配方提示同理）
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (plugin.getCheckinManager().hasCheckedInToday(player.getUniqueId())) {
                return;
            }
            String template = isBedrock(player)
                    ? cfg.getCheckinJoinHintMessageBedrock()
                    : cfg.getCheckinJoinHintMessage();
            try {
                player.sendMessage(miniMessage.deserialize(template));
            } catch (Exception e) {
                // MiniMessage 解析失败时降级为纯文本
                player.sendMessage(Component.text(template));
            }
        }, 20L);
    }

    /**
     * 判断玩家当前连接是否为 Geyser 基岩版连接。
     * <p>
     * 反射访问 Geyser API（{@code GeyserApi.api().connectionByUuid(uuid)}），
     * 避免引入编译期依赖；Geyser 未安装或调用失败时返回 false（按 Java 玩家处理）。
     *
     * @param player 玩家
     * @return true = 基岩版连接
     */
    private boolean isBedrock(Player player) {
        if (Bukkit.getPluginManager().getPlugin("Geyser-Spigot") == null) {
            return false;
        }
        try {
            Class<?> apiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
            Method apiMethod = apiClass.getMethod("api");
            Object api = apiMethod.invoke(null);
            Method connMethod = api.getClass().getMethod("connectionByUuid", UUID.class);
            return connMethod.invoke(api, player.getUniqueId()) != null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}

package com.customdeath.listener;

import com.customdeath.CustomDeathMessages;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 红名机制（罪恶值）监听器。
 * <p>
 * 仅处理玩家进服/退出的罪恶值相关收尾：
 * <ul>
 *   <li>进服：补算离线消退（非在线模式）、同步红名外显、红名/临界状态提示；</li>
 *   <li>退出：下线冻结（仅在线模式）、移除红名外显、落盘。</li>
 * </ul>
 * PVP 击杀/死亡的罪恶值记录在 {@code DeathListener} 中完成（复用其击杀解析），
 * 避免对同一死亡事件重复解析。
 */
public class SinListener implements Listener {

    private final CustomDeathMessages plugin;

    public SinListener(CustomDeathMessages plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getSinManager().onJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getSinManager().onQuit(event.getPlayer());
    }
}

package com.customdeath.listener;

import com.customdeath.CustomDeathMessages;
import com.customdeath.config.ConfigManager;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * 击杀特效监听器。
 * <p>
 * 玩家击杀生物后，在被击杀者的死亡地点播放粒子爆发与音效
 * （击杀者本人与附近玩家可见可听，粒子为「中心爆发 + 横向冲击波圆环」）。
 * <p>
 * 触发规则：
 * <ul>
 *   <li>仅由玩家造成的击杀才触发（怪物被火烧死/摔死等无击杀者的死亡不触发）</li>
 *   <li>默认仅对敌对生物（实现 {@link Enemy} 接口，含 Boss）生效，
 *       可通过配置扩展至动物等被动生物或 PVP 击杀</li>
 *   <li>被动监听（MONITOR），不干扰其他插件对该事件的正常处理</li>
 * </ul>
 */
public class KillEffectListener implements Listener {

    private final CustomDeathMessages plugin;

    public KillEffectListener(CustomDeathMessages plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isKillEffectEnabled()) {
            return;
        }

        LivingEntity entity = event.getEntity();
        // 仅玩家击杀触发（箭/投掷物击杀时 getKiller() 同样能取到射出者）
        Player killer = entity.getKiller();
        if (killer == null || !killer.isOnline()) {
            return;
        }

        // PVP（被击杀者为玩家）：默认不触发，需显式开启 include-players
        if (entity instanceof Player) {
            if (!cfg.isKillEffectIncludePlayers()) {
                return;
            }
        } else if (cfg.isKillEffectOnlyHostile() && !(entity instanceof Enemy)) {
            // 非玩家生物：only-hostile=true 时仅对敌对生物生效，过滤动物等被动生物
            return;
        }

        // 播放粒子（中心爆发 + 横向冲击波圆环）
        if (cfg.isKillEffectParticleEnabled()) {
            Particle particle = cfg.getKillEffectParticle();
            if (particle != null) {
                spawnKillParticles(entity.getLocation(), particle,
                        cfg.getKillEffectParticleAmount(), cfg.getKillEffectParticleSpeed());
            }
        }

        // 播放音效（在被击杀者位置播放，击杀者与附近玩家按距离可听）
        if (cfg.isKillEffectSoundEnabled()) {
            Sound sound = cfg.getKillEffectSound();
            if (sound != null) {
                entity.getWorld().playSound(entity.getLocation(), sound, SoundCategory.PLAYERS,
                        cfg.getKillEffectSoundVolume(), cfg.getKillEffectSoundPitch());
            }
        }
    }

    /**
     * 在死亡地点生成击杀特效粒子：
     * <ol>
     *   <li>中心爆发——以实体所在位置为球心向四周随机散射 {@code amount} 个粒子（带初速）；</li>
     *   <li>横向冲击波圆环——在实体腰部高度环绕一圈粒子，形成扩散波环。</li>
     * </ol>
     *
     * @param loc     被击杀实体所在位置（Y 取实体脚底，特效重心抬高约 0.6 格）
     * @param particle 粒子类型
     * @param amount   中心爆发粒子总数
     * @param speed    粒子初速（0.0 ~ 1.0，越大向外扩散越猛烈）
     */
    private void spawnKillParticles(Location loc, Particle particle, int amount, double speed) {
        double y = loc.getY() + 0.6;
        // 中心爆发：随机偏移 0.5 格范围内散射，speed 作为初速向外甩出
        loc.getWorld().spawnParticle(particle, loc.getX(), y, loc.getZ(),
                amount, 0.5, 0.5, 0.5, speed);
        // 横向冲击波圆环：环上粒子数约为爆发量的一半（至少 12 个），半径 1.3 格
        int ringAmount = Math.max(12, amount / 2);
        double step = Math.PI * 2 / ringAmount;
        double radius = 1.3;
        for (int i = 0; i < ringAmount; i++) {
            double angle = step * i;
            loc.getWorld().spawnParticle(particle,
                    loc.getX() + Math.cos(angle) * radius,
                    y,
                    loc.getZ() + Math.sin(angle) * radius,
                    1, 0, 0, 0, 0);
        }
    }
}

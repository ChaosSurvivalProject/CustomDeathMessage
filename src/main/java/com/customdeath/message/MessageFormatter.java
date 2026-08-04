package com.customdeath.message;

import com.customdeath.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * 死亡消息格式化器。
 * <p>
 * 将配置中的消息模板替换为实际玩家名、武器、伤害值等，
 * 并通过 MiniMessage 解析为 Adventure Component。
 */
public class MessageFormatter {

    private final ConfigManager configManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public MessageFormatter(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * 格式化死亡消息。
     *
     * @param template   消息模板，含 {player} {killer} {weapon} {damage} {world} 占位符
     * @param playerName 死亡玩家名
     * @param killerName 击杀者名（可为 null 或空）
     * @param weapon     武器名（可为 null 或空）
     * @param damage     最终伤害值
     * @param world      死亡所在世界名
     * @return 解析后的 Adventure Component
     */
    public Component format(String template, String playerName, String killerName,
                            String weapon, double damage, String world) {
        String text = template;

        // 替换玩家名（套用配置中的颜色）
        if (text.contains("{player}")) {
            text = text.replace("{player}", colorize(playerName, configManager.getPlayerNameColor()));
        }

        // 替换击杀者名（套用配置中的颜色）
        if (killerName != null && !killerName.isEmpty() && text.contains("{killer}")) {
            text = text.replace("{killer}", colorize(killerName, configManager.getKillerNameColor()));
        }

        // 替换其他占位符
        text = text.replace("{weapon}", weapon == null || weapon.isEmpty() ? "" : weapon)
                   .replace("{damage}", String.format("%.1f", damage))
                   .replace("{world}", world == null ? "" : world);

        // 解析 MiniMessage 标签
        try {
            return miniMessage.deserialize(text);
        } catch (Exception e) {
            // MiniMessage 解析失败时降级为纯文本
            return Component.text(text);
        }
    }

    /**
     * 将 MiniMessage 颜色标签包裹在名称外层。
     * 例如：colorTag="<yellow>", name="Steve" → "<yellow>Steve</yellow>"
     */
    private String colorize(String name, String colorTag) {
        if (colorTag == null || colorTag.isBlank()) {
            return name;
        }
        String trimmed = colorTag.trim();
        String closing = "</" + trimmed.substring(1);
        return trimmed + name + closing;
    }
}

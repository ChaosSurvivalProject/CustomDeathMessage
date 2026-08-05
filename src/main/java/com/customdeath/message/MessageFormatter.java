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
     * @param weapon       武器显示名（可为 null 或空）
     * @param weaponDetail 武器悬浮详情（MiniMessage 片段，可为 null）
     * @param damage       最终伤害值
     * @param world        死亡所在世界名
     * @return 解析后的 Adventure Component
     */
    public Component format(String template, String playerName, String killerName,
                            String weapon, String weaponDetail, double damage, String world) {
        String text = template;

        // 替换玩家名（套用配置中的颜色）
        if (text.contains("{player}")) {
            text = text.replace("{player}", colorize(playerName, configManager.getPlayerNameColor()));
        }

        // 替换击杀者名（套用配置中的颜色）
        if (killerName != null && !killerName.isEmpty() && text.contains("{killer}")) {
            text = text.replace("{killer}", colorize(killerName, configManager.getKillerNameColor()));
        }

        // 构造武器片段（含悬浮详情）
        String weaponFragment = buildWeaponFragment(weapon, weaponDetail);

        // 替换其他占位符
        text = text.replace("{weapon}", weaponFragment)
                   .replace("{damage}", String.format("%.1f", damage))
                   .replace("{world}", world == null ? "" : world);

        // 模板本身未写 {weapon} 但确实发生了击杀（武器非空）时，自动在末尾补充武器信息
        if (!template.contains("{weapon}") && !weaponFragment.isEmpty()) {
            text += " <gold>用 " + weaponFragment + " 击杀</gold>";
        }

        // 解析 MiniMessage 标签
        try {
            return miniMessage.deserialize(text);
        } catch (Exception e) {
            // MiniMessage 解析失败时降级为纯文本
            return Component.text(text);
        }
    }

    /**
     * 构造带悬浮详情的武器片段。
     * <p>
     * 若提供了 weaponDetail，则将武器显示名包裹在 MiniMessage 的
     * {@code <hover:show_text>} 标签内，鼠标悬停时展示详细信息。
     *
     * @param weapon 武器显示名（纯文本）
     * @param detail 悬浮详情（MiniMessage 片段，可为 null）
     * @return 武器片段（含或不含 hover 标签）
     */
    private String buildWeaponFragment(String weapon, String detail) {
        if (weapon == null || weapon.isEmpty()) {
            return "";
        }
        if (detail != null && !detail.isEmpty()) {
            // 转义详情中的单引号，避免破坏 show_text 的引号包裹
            return "<hover:show_text:'" + detail.replace("'", "\\'") + "'>" + weapon + "</hover>";
        }
        return weapon;
    }

    /**
     * 格式化附加片段（复仇提示 / 连杀播报等互动文本）。
     * <p>
     * 与 {@link #format} 不同，仅支持 {player} {killer} {count} {streak} {title}
     * 五个占位符，用于在死亡播报消息末尾追加一小段文本。
     *
     * @param template   附加消息模板
     * @param playerName 死亡玩家名
     * @param killerName 击杀者名（可为 null 或空）
     * @param count      击杀者对死亡玩家的累计击杀次数
     * @param streak     击杀者当前连杀数
     * @param title      连杀等级称号（如"五连绝世"，可为 null）
     * @return 解析后的 Adventure Component
     */
    public Component formatExtra(String template, String playerName, String killerName,
                                 int count, int streak, String title) {
        if (template == null || template.isBlank()) {
            return Component.empty();
        }
        String text = template;

        // 替换玩家名与击杀者名（与主消息使用相同的颜色配置）
        if (text.contains("{player}")) {
            text = text.replace("{player}", colorize(playerName, configManager.getPlayerNameColor()));
        }
        if (killerName != null && !killerName.isEmpty() && text.contains("{killer}")) {
            text = text.replace("{killer}", colorize(killerName, configManager.getKillerNameColor()));
        }
        text = text.replace("{count}", String.valueOf(count))
                   .replace("{streak}", String.valueOf(streak));
        if (title != null && !title.isEmpty() && text.contains("{title}")) {
            text = text.replace("{title}", title);
        }

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

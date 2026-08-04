package com.customdeath;

import com.customdeath.command.ReloadCommand;
import com.customdeath.config.ConfigManager;
import com.customdeath.listener.DeathListener;
import com.customdeath.message.MessageFormatter;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * CustomDeathMessages 插件主类。
 * <p>
 * 负责初始化配置管理器、消息格式化器，
 * 注册事件监听器和命令处理器。
 */
public final class CustomDeathMessages extends JavaPlugin {

    private ConfigManager configManager;
    private MessageFormatter messageFormatter;

    @Override
    public void onEnable() {
        // 初始化配置
        configManager = new ConfigManager(this);
        configManager.loadConfig();

        // 初始化消息格式化器
        messageFormatter = new MessageFormatter(configManager);

        // 注册事件监听器
        getServer().getPluginManager().registerEvents(new DeathListener(this), this);

        // 注册命令
        var command = getCommand("customdeath");
        if (command != null) {
            command.setExecutor(new ReloadCommand(this));
        }

        getLogger().info("CustomDeathMessages v" + getPluginMeta().getVersion() + " 已启用");
    }

    @Override
    public void onDisable() {
        getLogger().info("CustomDeathMessages 已卸载");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageFormatter getMessageFormatter() {
        return messageFormatter;
    }
}

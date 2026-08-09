package com.customdeath;

import com.customdeath.command.ReloadCommand;
import com.customdeath.config.ConfigManager;
import com.customdeath.data.DeathPointManager;
import com.customdeath.listener.DeathListener;
import com.customdeath.listener.ScrollListener;
import com.customdeath.message.MessageFormatter;
import com.customdeath.recipe.RecipeManager;
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
    private DeathPointManager deathPointManager;

    @Override
    public void onEnable() {
        // 初始化配置
        configManager = new ConfigManager(this);
        configManager.loadConfig();

        // 初始化消息格式化器
        messageFormatter = new MessageFormatter(configManager);

        // 初始化死亡点记录（供回溯卷轴使用）
        deathPointManager = new DeathPointManager(this);

        // 注册事件监听器
        getServer().getPluginManager().registerEvents(new DeathListener(this), this);
        getServer().getPluginManager().registerEvents(new ScrollListener(this), this);

        // 注册合成配方（护身/回城/回溯卷轴），同时注册为监听器以自动解锁配方书
        RecipeManager recipeManager = new RecipeManager(this);
        recipeManager.registerRecipes();
        getServer().getPluginManager().registerEvents(recipeManager, this);

        // 注册命令
        var command = getCommand("customdeath");
        if (command != null) {
            command.setExecutor(new ReloadCommand(this, recipeManager));
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

    public DeathPointManager getDeathPointManager() {
        return deathPointManager;
    }
}

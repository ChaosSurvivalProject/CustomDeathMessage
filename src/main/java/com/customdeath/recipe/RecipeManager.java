package com.customdeath.recipe;

import com.customdeath.CustomDeathMessages;
import com.customdeath.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;

/**
 * 合成配方注册器。
 * <p>
 * 为护身卷轴、回城卷轴、回溯卷轴注册无序合成配方，
 * 产物为带 PDC 标识的标准物品（{@link ConfigManager#buildScrollProtect} 等）。
 * 同时监听 {@link PlayerJoinEvent}，在玩家登录时自动解锁配方到配方书（受
 * {@code settings.recipes.auto-discover} 开关控制）。
 *
 * <p>配方一览（均可在 config.yml 通过 {@code settings.recipes.enabled} 总开关关闭，
 * 每种材料各需一个，成本梯度：回城卷轴 &lt; 回溯卷轴 &lt; 护身卷轴）：
 * <ul>
 *   <li>回城卷轴：纸 + 任意颜色的床 + 末影珍珠 → 回城卷轴 ×1</li>
 *   <li>回溯卷轴：纸 + 末影珍珠 + 回响碎片 → 回溯卷轴 ×1</li>
 *   <li>护身卷轴：纸 + 金锭 + 下界之星 → 护身卷轴 ×1</li>
 * </ul>
 */
public class RecipeManager implements Listener {

    private final CustomDeathMessages plugin;

    /** 全部 16 种颜色的床（回城卷轴配方接受任意颜色）。 */
    private static final Material[] BEDS = {
            Material.WHITE_BED, Material.ORANGE_BED, Material.MAGENTA_BED, Material.LIGHT_BLUE_BED,
            Material.YELLOW_BED, Material.LIME_BED, Material.PINK_BED, Material.GRAY_BED,
            Material.LIGHT_GRAY_BED, Material.CYAN_BED, Material.PURPLE_BED, Material.BLUE_BED,
            Material.BROWN_BED, Material.GREEN_BED, Material.RED_BED, Material.BLACK_BED
    };

    /** 配方标识（供登录时自动解锁使用）。 */
    private NamespacedKey keyTown;
    private NamespacedKey keyRecall;
    private NamespacedKey keyProtect;

    public RecipeManager(CustomDeathMessages plugin) {
        this.plugin = plugin;
    }

    /**
     * 注册全部配方（受 {@code settings.recipes.enabled} 开关控制）。
     * <p>
     * 仅在插件启动时调用一次。
     */
    public void registerRecipes() {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isRecipesEnabled()) {
            return;
        }

        registerTownScrollRecipe();
        registerRecallScrollRecipe();
        registerProtectScrollRecipe();
        plugin.getLogger().info("已注册 3 个合成配方（护身卷轴 / 回城卷轴 / 回溯卷轴）");
    }

    /**
     * 玩家登录时自动解锁配方到配方书，并按配置展示一次卷轴合成配方。
     * <p>
     * 调用 {@link Player#discoverRecipe(NamespacedKey)} 将配方直接加入玩家配方书，
     * 无需手动合成一次才能看到；已解锁的配方重复调用无副作用。
     * 解锁与提示均延迟 1 秒执行——经 Geyser 连接的基岩版玩家会话建立晚于 Java 端
     * {@link PlayerJoinEvent}，立即发送的配方解锁包/聊天消息可能丢失。
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isRecipesEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (cfg.isRecipesAutoDiscover()) {
                discoverAll(player);
            }
            if (cfg.isRecipesJoinHintEnabled()) {
                player.sendMessage(buildRecipesOverview());
            }
        }, 20L);
    }

    /**
     * 一次性解锁全部卷轴配方。
     *
     * @param player 目标玩家
     */
    private void discoverAll(Player player) {
        if (keyTown != null) {
            player.discoverRecipe(keyTown);
        }
        if (keyRecall != null) {
            player.discoverRecipe(keyRecall);
        }
        if (keyProtect != null) {
            player.discoverRecipe(keyProtect);
        }
    }

    /**
     * 构建三种卷轴的合成配方总览（供 /customdeath recipe 命令与加入提示使用）。
     * <p>
     * 材料清单与下方三个 register* 方法的配方定义严格同步——改动配方时须同步此处的文案。
     * 产物名直接复用配置中的卷轴名称（含 MiniMessage 样式），配置改名后展示自动跟随。
     *
     * @return 解析后的 Adventure Component（解析失败时降级为纯文本）
     */
    public Component buildRecipesOverview() {
        ConfigManager cfg = plugin.getConfigManager();
        String overview = "<gold><bold>═══ 卷轴合成配方 ═══</bold></gold>"
                + "<newline><gold>◆</gold> " + cfg.getScrollProtectName() + " <gray>纸 + 金锭 + 下界之星</gray>"
                + "<newline><gold>◆</gold> " + cfg.getScrollTownName() + " <gray>纸 + 任意颜色床 + 末影珍珠</gray>"
                + "<newline><gold>◆</gold> " + cfg.getScrollRecallName() + " <gray>纸 + 末影珍珠 + 回响碎片</gray>"
                + "<newline><gray>在合成台按配方摆放材料即可合成（基岩版玩家同样适用）</gray>";
        try {
            return MiniMessage.miniMessage().deserialize(overview);
        } catch (Exception e) {
            // MiniMessage 解析失败时降级为纯文本
            return Component.text(overview);
        }
    }

    /**
     * 向指定玩家发送卷轴合成配方总览。
     *
     * @param player 目标玩家
     */
    public void sendRecipeOverview(Player player) {
        player.sendMessage(buildRecipesOverview());
    }

/**
 * 回城卷轴：纸 + 任意颜色的床 + 末影珍珠 → 回城卷轴 ×1（每种材料各一个）。
 */
private void registerTownScrollRecipe() {
    keyTown = new NamespacedKey(plugin, "scroll_town");
    ShapelessRecipe recipe = new ShapelessRecipe(keyTown, plugin.getConfigManager().buildScrollTown(1));
    recipe.addIngredient(Material.PAPER);
    recipe.addIngredient(new RecipeChoice.MaterialChoice(BEDS));
    recipe.addIngredient(Material.ENDER_PEARL);
    Bukkit.addRecipe(recipe);
}

/**
 * 回溯卷轴：纸 + 末影珍珠 + 回响碎片 → 回溯卷轴 ×1（每种材料各一个）。
 */
private void registerRecallScrollRecipe() {
    keyRecall = new NamespacedKey(plugin, "scroll_recall");
    ShapelessRecipe recipe = new ShapelessRecipe(keyRecall, plugin.getConfigManager().buildScrollRecall(1));
    recipe.addIngredient(Material.PAPER);
    recipe.addIngredient(Material.ENDER_PEARL);
    recipe.addIngredient(Material.ECHO_SHARD);
    Bukkit.addRecipe(recipe);
}

/**
 * 护身卷轴：纸 + 金锭 + 下界之星 → 护身卷轴 ×1（每种材料各一个）。
 */
private void registerProtectScrollRecipe() {
    keyProtect = new NamespacedKey(plugin, "scroll_protect");
    ShapelessRecipe recipe = new ShapelessRecipe(keyProtect, plugin.getConfigManager().buildScrollProtect(1));
    recipe.addIngredient(Material.PAPER);
    recipe.addIngredient(Material.GOLD_INGOT);
    recipe.addIngredient(Material.NETHER_STAR);
    Bukkit.addRecipe(recipe);
}
}

package com.customdeath.recipe;

import com.customdeath.CustomDeathMessages;
import com.customdeath.config.ConfigManager;
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
 * 成本梯度：回城卷轴 &lt; 回溯卷轴 &lt; 护身卷轴）：
 * <ul>
 *   <li>回城卷轴：纸 ×2 + 任意颜色的床 + 末影珍珠 → 回城卷轴 ×1</li>
 *   <li>回溯卷轴：纸 ×2 + 末影珍珠 ×2 + 回响碎片 → 回溯卷轴 ×1</li>
 *   <li>护身卷轴：纸 ×2 + 金锭 ×2 + 下界之星 → 护身卷轴 ×1</li>
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
     * 玩家登录时自动解锁配方到配方书（受 {@code settings.recipes.auto-discover} 控制）。
     * <p>
     * 调用 {@link Player#discoverRecipe(NamespacedKey)} 将配方直接加入玩家配方书，
     * 无需手动合成一次才能看到。已解锁的配方重复调用无副作用。
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isRecipesAutoDiscover() || !cfg.isRecipesEnabled()) {
            return;
        }
        Player player = event.getPlayer();
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
     * 回城卷轴：纸 ×2 + 任意颜色的床 + 末影珍珠 → 回城卷轴 ×1。
     */
    private void registerTownScrollRecipe() {
        keyTown = new NamespacedKey(plugin, "scroll_town");
        ShapelessRecipe recipe = new ShapelessRecipe(keyTown, plugin.getConfigManager().buildScrollTown(1));
        recipe.addIngredient(Material.PAPER, 2);
        recipe.addIngredient(new RecipeChoice.MaterialChoice(BEDS));
        recipe.addIngredient(Material.ENDER_PEARL);
        Bukkit.addRecipe(recipe);
    }

    /**
     * 回溯卷轴：纸 ×2 + 末影珍珠 ×2 + 回响碎片 → 回溯卷轴 ×1。
     */
    private void registerRecallScrollRecipe() {
        keyRecall = new NamespacedKey(plugin, "scroll_recall");
        ShapelessRecipe recipe = new ShapelessRecipe(keyRecall, plugin.getConfigManager().buildScrollRecall(1));
        recipe.addIngredient(Material.PAPER, 2);
        recipe.addIngredient(Material.ENDER_PEARL, 2);
        recipe.addIngredient(Material.ECHO_SHARD);
        Bukkit.addRecipe(recipe);
    }

    /**
     * 护身卷轴：纸 ×2 + 金锭 ×2 + 下界之星 → 护身卷轴 ×1。
     */
    private void registerProtectScrollRecipe() {
        keyProtect = new NamespacedKey(plugin, "scroll_protect");
        ShapelessRecipe recipe = new ShapelessRecipe(keyProtect, plugin.getConfigManager().buildScrollProtect(1));
        recipe.addIngredient(Material.PAPER, 2);
        recipe.addIngredient(Material.GOLD_INGOT, 2);
        recipe.addIngredient(Material.NETHER_STAR);
        Bukkit.addRecipe(recipe);
    }
}

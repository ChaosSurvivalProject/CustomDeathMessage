package com.customdeath.recipe;

import com.customdeath.CustomDeathMessages;
import com.customdeath.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;

/**
 * 合成配方注册器。
 * <p>
 * 为死亡保护券、回城卷轴、回溯卷轴注册无序合成配方，
 * 产物为带 PDC 标识的标准物品（{@link ConfigManager#buildDeathTicket} 等）。
 *
 * <p>配方一览（均可在 config.yml 通过 {@code settings.recipes.enabled} 总开关关闭，
 * 成本梯度：回城卷轴 &lt; 回溯卷轴 &lt; 死亡保护券）：
 * <ul>
 *   <li>回城卷轴：纸 ×2 + 任意颜色的床 + 末影珍珠 → 回城卷轴 ×1</li>
 *   <li>回溯卷轴：纸 ×2 + 末影珍珠 ×2 + 回响碎片 → 回溯卷轴 ×1</li>
 *   <li>死亡保护券：纸 ×2 + 金锭 ×2 + 下界之星 → 死亡保护券 ×1</li>
 * </ul>
 */
public class RecipeManager {

    private final CustomDeathMessages plugin;

    /** 全部 16 种颜色的床（回城卷轴配方接受任意颜色）。 */
    private static final Material[] BEDS = {
            Material.WHITE_BED, Material.ORANGE_BED, Material.MAGENTA_BED, Material.LIGHT_BLUE_BED,
            Material.YELLOW_BED, Material.LIME_BED, Material.PINK_BED, Material.GRAY_BED,
            Material.LIGHT_GRAY_BED, Material.CYAN_BED, Material.PURPLE_BED, Material.BLUE_BED,
            Material.BROWN_BED, Material.GREEN_BED, Material.RED_BED, Material.BLACK_BED
    };

    public RecipeManager(CustomDeathMessages plugin) {
        this.plugin = plugin;
    }

    /**
     * 注册全部配方（受 {@code settings.recipes.enabled} 开关控制）。
     * <p>
     * 仅在插件启动时调用一次，配方书会自动展示给玩家。
     */
    public void registerRecipes() {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isRecipesEnabled()) {
            return;
        }

        registerTownScrollRecipe();
        registerRecallScrollRecipe();
        registerDeathTicketRecipe();
        plugin.getLogger().info("已注册 3 个合成配方（回城卷轴 / 回溯卷轴 / 死亡保护券）");
    }

    /**
     * 回城卷轴：纸 ×2 + 任意颜色的床 + 末影珍珠 → 回城卷轴 ×1。
     */
    private void registerTownScrollRecipe() {
        NamespacedKey key = new NamespacedKey(plugin, "scroll_town");
        ShapelessRecipe recipe = new ShapelessRecipe(key, plugin.getConfigManager().buildScrollTown(1));
        recipe.addIngredient(Material.PAPER, 2);
        recipe.addIngredient(new RecipeChoice.MaterialChoice(BEDS));
        recipe.addIngredient(Material.ENDER_PEARL);
        Bukkit.addRecipe(recipe);
    }

    /**
     * 回溯卷轴：纸 ×2 + 末影珍珠 ×2 + 回响碎片 → 回溯卷轴 ×1。
     */
    private void registerRecallScrollRecipe() {
        NamespacedKey key = new NamespacedKey(plugin, "scroll_recall");
        ShapelessRecipe recipe = new ShapelessRecipe(key, plugin.getConfigManager().buildScrollRecall(1));
        recipe.addIngredient(Material.PAPER, 2);
        recipe.addIngredient(Material.ENDER_PEARL, 2);
        recipe.addIngredient(Material.ECHO_SHARD);
        Bukkit.addRecipe(recipe);
    }

    /**
     * 死亡保护券：纸 ×2 + 金锭 ×2 + 下界之星 → 死亡保护券 ×1。
     */
    private void registerDeathTicketRecipe() {
        NamespacedKey key = new NamespacedKey(plugin, "death_ticket");
        ShapelessRecipe recipe = new ShapelessRecipe(key, plugin.getConfigManager().buildDeathTicket(1));
        recipe.addIngredient(Material.PAPER, 2);
        recipe.addIngredient(Material.GOLD_INGOT, 2);
        recipe.addIngredient(Material.NETHER_STAR);
        Bukkit.addRecipe(recipe);
    }
}

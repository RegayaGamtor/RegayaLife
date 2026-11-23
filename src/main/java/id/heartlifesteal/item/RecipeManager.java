package id.heartlifesteal.item;

import id.heartlifesteal.HeartLifestealPlugin;
import id.heartlifesteal.config.Settings;
import id.heartlifesteal.config.Settings.RecipeSettings;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;

import java.util.Map;

/** Mendaftarkan crafting recipe Relic of Life dari recipe.yml. */
public final class RecipeManager {

    private final HeartLifestealPlugin plugin;
    private final NamespacedKey key;

    public RecipeManager(HeartLifestealPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "relic_of_life");
    }

    public NamespacedKey key() { return key; }

    /** Daftarkan ulang recipe sesuai pengaturan saat ini (aman dipanggil berulang). */
    public void register() {
        unregister();
        Settings s = plugin.settings();
        RecipeSettings r = s.recipe;
        if (!r.enabled() || !s.relic.enabled()) return;

        ItemStack result = plugin.items().create(ItemFactory.Kind.RELIC, r.resultAmount());
        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape(r.shape().toArray(new String[0]));
        for (Map.Entry<Character, Material> e : r.ingredients().entrySet()) {
            recipe.setIngredient(e.getKey(), e.getValue());
        }
        try {
            Bukkit.addRecipe(recipe);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Gagal mendaftarkan recipe Relic of Life: " + ex.getMessage());
            return;
        }
        if (r.autoDiscover()) {
            for (Player p : Bukkit.getOnlinePlayers()) p.discoverRecipe(key);
        }
    }

    public void unregister() {
        Bukkit.removeRecipe(key);
    }

    public void discover(Player player) {
        Settings s = plugin.settings();
        if (s.recipe.enabled() && s.recipe.autoDiscover() && s.relic.enabled()) player.discoverRecipe(key);
    }
}

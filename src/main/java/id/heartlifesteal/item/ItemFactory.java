package id.heartlifesteal.item;

import id.heartlifesteal.HeartLifestealPlugin;
import id.heartlifesteal.config.Settings;
import id.heartlifesteal.config.Settings.ItemSettings;
import id.heartlifesteal.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Membuat dan mengenali item Blood / Relic of Life (ditandai PersistentDataContainer, bukan nama). */
public final class ItemFactory {

    public enum Kind { BLOOD, RELIC }

    private final HeartLifestealPlugin plugin;
    private final NamespacedKey typeKey;

    public ItemFactory(HeartLifestealPlugin plugin) {
        this.plugin = plugin;
        this.typeKey = new NamespacedKey(plugin, "item_type");
    }

    public ItemSettings settingsOf(Kind kind) {
        Settings s = plugin.settings();
        return kind == Kind.BLOOD ? s.blood : s.relic;
    }

    @SuppressWarnings("deprecation")
    public ItemStack create(Kind kind, int amount) {
        ItemSettings cfg = settingsOf(kind);
        ItemStack stack = new ItemStack(cfg.material(), Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        meta.displayName(Text.noItalic(cfg.name()));
        List<Component> lore = new ArrayList<>();
        for (String line : cfg.lore()) lore.add(Text.noItalic(line));
        meta.lore(lore);

        if (cfg.customModelData() > 0) meta.setCustomModelData(cfg.customModelData());
        String model = cfg.itemModel();
        if (model != null && !model.isBlank()) {
            NamespacedKey key = NamespacedKey.fromString(model.trim());
            if (key != null) meta.setItemModel(key);
        }
        if (cfg.glow()) meta.setEnchantmentGlintOverride(true);

        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, kind.name());
        stack.setItemMeta(meta);
        return stack;
    }

    /** Jenis item plugin, atau null bila bukan item plugin. */
    public Kind kindOf(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return null;
        String value = meta.getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        if (value == null) return null;
        try {
            return Kind.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Masukkan ke inventori; sisa yang tidak muat dijatuhkan di kaki pemain. */
    public void give(Player player, Kind kind, int amount) {
        if (amount <= 0) return;
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(create(kind, amount));
        for (ItemStack rest : leftover.values()) {
            Item dropped = player.getWorld().dropItemNaturally(player.getLocation(), rest);
            dropped.setOwner(player.getUniqueId());
        }
    }
}

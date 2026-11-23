package id.heartlifesteal.listener;

import id.heartlifesteal.HeartLifestealPlugin;
import id.heartlifesteal.config.Settings;
import id.heartlifesteal.data.PlayerData;
import id.heartlifesteal.item.ItemFactory;
import id.heartlifesteal.item.ItemFactory.Kind;
import id.heartlifesteal.service.HeartService;
import id.heartlifesteal.util.Effects;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ShapedRecipe;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Pemakaian Blood / Relic of Life dan pencegahan eksploitasi (crafting, place, makan). */
public final class ItemListener implements Listener {

    private static final long DEBOUNCE_MS = 250L;

    private final HeartLifestealPlugin plugin;
    private final Map<UUID, Long> lastUse = new HashMap<>();

    public ItemListener(HeartLifestealPlugin plugin) {
        this.plugin = plugin;
    }

    public void forget(UUID id) {
        lastUse.remove(id);
    }

    // ------------------------------------------------------------------ pakai

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        EquipmentSlot hand = event.getHand();
        if (hand == null) return;

        Player player = event.getPlayer();
        PlayerInventory inv = player.getInventory();
        ItemStack held = hand == EquipmentSlot.HAND ? inv.getItemInMainHand() : inv.getItemInOffHand();
        Kind kind = plugin.items().kindOf(held);
        if (kind == null) return;

        // hormati proteksi plugin lain (mis. WorldGuard) yang sudah menolak penggunaan item
        if (event.useItemInHand() == Event.Result.DENY) return;

        // klik blok interaktif (peti, pintu, dll.) tetap berfungsi normal kecuali sedang sneak
        Block clicked = event.getClickedBlock();
        if (clicked != null && !player.isSneaking() && clicked.getType().isInteractable()) {
            event.setUseItemInHand(Event.Result.DENY); // cegah efek vanilla item, biarkan blok berinteraksi
            return;
        }

        event.setUseItemInHand(Event.Result.DENY);
        event.setCancelled(true); // cegah place (REDSTONE) dan efek vanilla lain

        long now = System.currentTimeMillis();
        Long last = lastUse.get(player.getUniqueId());
        if (last != null && now - last < DEBOUNCE_MS) return; // cegah pemakaian ganda dari event beruntun
        lastUse.put(player.getUniqueId(), now);

        if (kind == Kind.BLOOD) useBlood(player, hand);
        else useRelic(player, hand);
    }

    private void useBlood(Player player, EquipmentSlot hand) {
        Settings s = plugin.settings();
        if (!s.blood.enabled()) {
            plugin.messages().send(player, "item-disabled");
            return;
        }
        HeartService hearts = plugin.hearts();
        PlayerData data = hearts.profile(player);
        int max = s.limits.maxHearts();

        if (data.hearts() >= max) {
            plugin.messages().send(player, "maximum-hearts");
            if (s.blood.consumeOnFull()) consumeOne(player, hand);
            return;
        }

        int gain = Math.min(s.blood.heartsPerUse(), max - data.hearts());
        HeartService.Change change = hearts.setHearts(data, data.hearts() + gain, "blood", null);
        hearts.healBy(player, gain * 2.0);
        consumeOne(player, hand);
        Effects.playUse(plugin, player, s.blood);
        plugin.messages().send(player, "heart-increased", "hearts", change.after(), "max", max);
    }

    private void useRelic(Player player, EquipmentSlot hand) {
        Settings s = plugin.settings();
        if (!s.relic.enabled()) {
            plugin.messages().send(player, "item-disabled");
            return;
        }
        HeartService hearts = plugin.hearts();
        PlayerData data = hearts.profile(player);
        int max = s.limits.maxHearts();

        if (data.hearts() >= max) {
            plugin.messages().send(player, "maximum-hearts");
            if (s.relic.consumeOnFull()) consumeOne(player, hand);
            return;
        }

        int target = s.relic.restoreToMax() ? max : Math.min(max, data.hearts() + s.relic.heartsPerUse());
        HeartService.Change change = hearts.setHearts(data, target, "relic", null);
        hearts.healFull(player);
        consumeOne(player, hand);
        Effects.playUse(plugin, player, s.relic);
        plugin.messages().send(player, "relic-used", "hearts", change.after(), "max", max);
    }

    /** Kurangi satu item dari tangan yang dipakai (hanya dipanggil setelah pemakaian berhasil). */
    private void consumeOne(Player player, EquipmentSlot hand) {
        PlayerInventory inv = player.getInventory();
        ItemStack current = hand == EquipmentSlot.HAND ? inv.getItemInMainHand() : inv.getItemInOffHand();
        ItemStack replacement;
        if (current.getAmount() <= 1) {
            replacement = new ItemStack(Material.AIR);
        } else {
            replacement = current.clone();
            replacement.setAmount(current.getAmount() - 1);
        }
        if (hand == EquipmentSlot.HAND) inv.setItemInMainHand(replacement);
        else inv.setItemInOffHand(replacement);
    }

    // ------------------------------------------------------------ anti-eksploit

    /**
     * Item plugin tidak boleh dipakai sebagai bahan crafting apa pun, kecuali sebagai Blood pada
     * slot "BLOOD" di recipe Relic of Life (jumlahnya harus persis). Relic tidak pernah boleh jadi bahan.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        CraftingInventory inv = event.getInventory();
        ItemFactory items = plugin.items();
        int blood = 0;
        int relic = 0;
        for (ItemStack stack : inv.getMatrix()) {
            if (stack == null) continue;
            Kind kind = items.kindOf(stack);
            if (kind == Kind.BLOOD) blood++;
            else if (kind == Kind.RELIC) relic++;
        }
        if (blood == 0 && relic == 0) return;

        boolean ours = event.getRecipe() instanceof ShapedRecipe shaped
                && shaped.getKey().equals(plugin.recipes().key());
        if (relic > 0 || !ours || blood != plugin.settings().recipe.bloodCount()) {
            inv.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (plugin.items().kindOf(event.getItemInHand()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (plugin.items().kindOf(event.getItem()) != null) event.setCancelled(true);
    }
}

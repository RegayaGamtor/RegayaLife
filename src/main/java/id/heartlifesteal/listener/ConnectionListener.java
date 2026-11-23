package id.heartlifesteal.listener;

import id.heartlifesteal.HeartLifestealPlugin;
import id.heartlifesteal.config.Settings;
import id.heartlifesteal.data.PlayerData;
import id.heartlifesteal.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

/** Login (ban plugin), join, respawn, dan pembatasan health tambahan. */
public final class ConnectionListener implements Listener {

    private final HeartLifestealPlugin plugin;

    public ConnectionListener(HeartLifestealPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * "Ban" milik plugin ini: pemain tereliminasi ditolak saat login. Tidak menyentuh daftar ban server,
     * sehingga ban manual admin atau plugin lain tidak pernah terhapus oleh /heart revive.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        Settings s = plugin.settings();
        if (!s.eliminationEnabled || !s.banOnZero) return;
        PlayerData d = plugin.data().find(event.getUniqueId());
        if (d == null || !d.eliminated()) return;
        String reason = Text.apply(s.banReason, "player", d.name());
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, Text.color(reason));
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        plugin.hearts().handleJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.hud().forget(event.getPlayer().getUniqueId());
        plugin.itemListener().forget(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        // tunda 1 tick: pastikan atribut dan HP sesuai setelah respawn selesai
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = Bukkit.getPlayer(id);
            if (p == null) return;
            PlayerData d = plugin.data().find(id);
            if (d == null) return;
            if (d.eliminated() && plugin.settings().eliminationEnabled) {
                plugin.hearts().enforceElimination(p, d);
            } else {
                plugin.hearts().syncAttribute(p, d);
                plugin.hud().refresh(p);
            }
        });
    }

    /** Health Boost menambah max health di atas batas heart plugin, jadi diblokir (bisa dimatikan di config). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPotion(EntityPotionEffectEvent event) {
        if (!plugin.settings().blockHealthBoost) return;
        if (!(event.getEntity() instanceof Player)) return;
        PotionEffect added = event.getNewEffect();
        if (added == null) return;
        if (added.getType().equals(PotionEffectType.HEALTH_BOOST)) event.setCancelled(true);
    }
}

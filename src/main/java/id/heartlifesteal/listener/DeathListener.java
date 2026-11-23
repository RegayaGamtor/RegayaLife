package id.heartlifesteal.listener;

import id.heartlifesteal.HeartLifestealPlugin;
import id.heartlifesteal.config.Settings;
import id.heartlifesteal.data.PlayerData;
import id.heartlifesteal.item.ItemFactory;
import id.heartlifesteal.service.HeartService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.net.InetAddress;

/** Lifesteal: korban kehilangan heart, pembunuh yang valid mendapat Blood (dengan anti-farming). */
public final class DeathListener implements Listener {

    private final HeartLifestealPlugin plugin;

    public DeathListener(HeartLifestealPlugin plugin) {
        this.plugin = plugin;
    }

    private static boolean isNpc(Player p) {
        return p.hasMetadata("NPC"); // Citizens dan sejenisnya
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Settings s = plugin.settings();
        if (!s.lifestealEnabled) return;

        Player victim = event.getEntity();
        if (isNpc(victim)) return;

        Player killer = victim.getKiller();
        boolean pvp = killer != null
                && !killer.getUniqueId().equals(victim.getUniqueId())
                && !isNpc(killer);
        if (!pvp && !s.loseHeartOnNonPvp) return;

        HeartService hearts = plugin.hearts();
        PlayerData data = hearts.profile(victim);
        if (data.eliminated()) return; // sudah tereliminasi: cegah pengurangan ganda

        int before = data.hearts();
        String cause = pvp ? "PVP" : causeOf(victim);
        HeartService.Change change = hearts.setHearts(data, before - s.heartsLostPerDeath, cause, pvp ? killer : null);

        if (!change.eliminatedNow() && change.after() < before) {
            plugin.messages().send(victim, "heart-decreased", "amount", before - change.after(),
                    "hearts", change.after(), "max", s.limits.maxHearts());
        }

        if (pvp) awardBlood(killer, victim);
    }

    private static String causeOf(Player victim) {
        EntityDamageEvent last = victim.getLastDamageCause();
        return last == null ? "UNKNOWN" : last.getCause().name();
    }

    private void awardBlood(Player killer, Player victim) {
        Settings s = plugin.settings();
        if (!s.blood.enabled() || s.bloodPerKill <= 0) return;

        long now = System.currentTimeMillis();
        if (s.antiFarmEnabled) {
            if (s.antiFarmSameIp && sameIp(killer, victim)) {
                plugin.messages().send(killer, "blood-blocked-ip");
                return;
            }
            long remaining = plugin.antiFarm().remainingSeconds(
                    killer.getUniqueId(), victim.getUniqueId(), now, s.antiFarmCooldown);
            if (remaining > 0) {
                plugin.messages().send(killer, "blood-cooldown", "victim", victim.getName(), "seconds", remaining);
                return;
            }
            plugin.antiFarm().record(killer.getUniqueId(), victim.getUniqueId(), now);
            plugin.data().saveCooldown(killer.getUniqueId(), victim.getUniqueId(), now);
        }

        plugin.items().give(killer, ItemFactory.Kind.BLOOD, s.bloodPerKill);
        PlayerData killerData = plugin.hearts().profile(killer);
        killerData.setLastBloodAt(now);
        plugin.data().save(killerData);
        plugin.messages().send(killer, "blood-received", "amount", s.bloodPerKill);
    }

    private static boolean sameIp(Player a, Player b) {
        if (a.getAddress() == null || b.getAddress() == null) return false;
        InetAddress ia = a.getAddress().getAddress();
        InetAddress ib = b.getAddress().getAddress();
        return ia != null && ia.equals(ib);
    }
}

package id.heartlifesteal.service;

import id.heartlifesteal.HeartLifestealPlugin;
import id.heartlifesteal.config.Settings;
import id.heartlifesteal.data.DataManager;
import id.heartlifesteal.data.PlayerData;
import id.heartlifesteal.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Inti sistem heart: semua perubahan heart (kematian, item, command admin) lewat setHearts().
 * Semua dipanggil dari main thread, sehingga pengurangan heart tidak balapan meski server lag.
 * 1 heart = 2 HP; setiap perubahan langsung disinkronkan ke atribut MAX_HEALTH.
 */
public final class HeartService {

    public record Change(int before, int after, boolean eliminatedNow, boolean revivedNow) {}

    private final HeartLifestealPlugin plugin;

    public HeartService(HeartLifestealPlugin plugin) {
        this.plugin = plugin;
    }

    private DataManager data() { return plugin.data(); }
    private Settings cfg() { return plugin.settings(); }

    /** Data pemain online (dibuat dengan heart default bila belum ada). */
    public PlayerData profile(Player player) {
        return data().getOrCreate(player.getUniqueId(), player.getName(), cfg().limits.defaultHearts());
    }

    // ---------------------------------------------------------------- perubahan

    /**
     * Atur heart ke nilai target (dibatasi min/max). Menangani eliminasi (heart 0) dan pemulihan
     * status eliminated bila heart kembali di atas 0. Data disimpan permanen secara asinkron.
     *
     * @param killer pembunuh (boleh null) — hanya untuk riwayat eliminasi
     */
    public Change setHearts(PlayerData d, int target, String cause, Player killer) {
        Settings s = cfg();
        int before = d.hearts();
        int after = s.limits.clamp(target);
        boolean wasEliminated = d.eliminated();

        d.setHearts(after);
        boolean eliminatedNow = false;
        boolean revivedNow = false;
        if (s.eliminationEnabled && after <= 0) {
            if (!wasEliminated) {
                d.setEliminated(true);
                d.setEliminatedAt(System.currentTimeMillis());
                eliminatedNow = true;
            }
        } else if (wasEliminated) {
            d.setEliminated(false);
            revivedNow = true;
        }
        data().save(d);

        Player online = Bukkit.getPlayer(d.uuid());
        if (eliminatedNow) {
            handleElimination(d, online, cause, killer);
        } else if (online != null) {
            syncAttribute(online, d);
            if (revivedNow) afterRevive(online);
        }
        if (online != null) plugin.hud().refresh(online);
        return new Change(before, after, eliminatedNow, revivedNow);
    }

    public Change loseHearts(Player victim, int amount, Player killer, String cause) {
        PlayerData d = profile(victim);
        return setHearts(d, d.hearts() - amount, cause, killer);
    }

    public Change reset(PlayerData d) {
        return setHearts(d, cfg().limits.defaultHearts(), "reset", null);
    }

    /** Hidupkan kembali pemain yang tereliminasi. Mengembalikan false bila tidak tereliminasi. */
    public boolean revive(PlayerData d) {
        if (!d.eliminated()) return false;
        setHearts(d, cfg().heartsOnRevive, "revive", null);
        return true;
    }

    // --------------------------------------------------------------- eliminasi

    private void handleElimination(PlayerData d, Player online, String cause, Player killer) {
        Settings s = cfg();
        data().recordElimination(d.uuid(), d.name(),
                killer == null ? null : killer.getUniqueId(),
                killer == null ? null : killer.getName(),
                cause == null ? "UNKNOWN" : cause, System.currentTimeMillis());

        String broadcast = plugin.messages().raw("eliminated-broadcast", "player", d.name());
        if (!broadcast.isEmpty()) Bukkit.broadcast(Text.color(plugin.messages().raw("prefix") + broadcast));

        if (online != null) enforceElimination(online, d);
    }

    /** Terapkan hukuman eliminasi pada pemain online: kick (ban plugin) atau spectator. */
    public void enforceElimination(Player player, PlayerData d) {
        Settings s = cfg();
        UUID id = d.uuid();
        String name = d.name();
        if (s.banOnZero) {
            net.kyori.adventure.text.Component reason = Text.color(Text.apply(s.banReason, "player", name));
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player p = Bukkit.getPlayer(id);
                if (p != null) p.kick(reason);
            });
        } else {
            plugin.messages().send(player, "eliminated");
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player p = Bukkit.getPlayer(id);
                if (p != null && !p.isDead()) p.setGameMode(GameMode.SPECTATOR);
            });
        }
    }

    private void afterRevive(Player player) {
        if (player.getGameMode() == GameMode.SPECTATOR) player.setGameMode(cfg().reviveGameMode);
        plugin.messages().send(player, "revived", "hearts", profile(player).hearts(),
                "max", cfg().limits.maxHearts());
    }

    // ------------------------------------------------------------- atribut/HP

    /** Samakan MAX_HEALTH pemain dengan jumlah heart (heart x 2). */
    public void syncAttribute(Player player, PlayerData d) {
        if (d.eliminated() || d.hearts() <= 0) return;
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;
        double target = d.hearts() * 2.0;
        if (Math.abs(attr.getBaseValue() - target) > 0.0001) attr.setBaseValue(target);
        double max = attr.getValue();
        if (!player.isDead() && player.getHealth() > max) player.setHealth(max);
    }

    /** Pemeriksaan ringan berkala/respawn: perbaiki bila plugin lain mengubah max health. */
    public void verify(Player player) {
        PlayerData d = data().find(player.getUniqueId());
        if (d != null) syncAttribute(player, d);
    }

    public void healBy(Player player, double hp) {
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null || player.isDead()) return;
        player.setHealth(Math.min(attr.getValue(), player.getHealth() + hp));
    }

    public void healFull(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null || player.isDead()) return;
        player.setHealth(attr.getValue());
    }

    // ------------------------------------------------------------------- join

    public void handleJoin(Player player) {
        PlayerData d = profile(player);
        Settings s = cfg();
        if (d.eliminated() && s.eliminationEnabled) {
            enforceElimination(player, d);
        } else {
            syncAttribute(player, d);
        }
        plugin.hud().show(player);
        plugin.recipes().discover(player);
    }

    public String statusText(PlayerData d) {
        if (d.eliminated()) return plugin.messages().raw("status-eliminated");
        if (d.hearts() <= cfg().hud.criticalThreshold()) return plugin.messages().raw("status-critical");
        return plugin.messages().raw("status-alive");
    }
}

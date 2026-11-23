package id.heartlifesteal.util;

import id.heartlifesteal.config.Settings.ItemSettings;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

/** Efek suara, partikel, dan animasi saat item dipakai. Animasi hanya berjalan sesaat (1 detik). */
public final class Effects {
    private Effects() {}

    public static void playUse(Plugin plugin, Player player, ItemSettings item) {
        String sound = item.sound();
        if (sound != null && !sound.isBlank()) {
            try {
                player.playSound(player.getLocation(), sound, item.soundVolume(), item.soundPitch());
            } catch (RuntimeException ignored) {
                // nama suara tidak valid: abaikan saja
            }
        }
        Particle particle = item.particle();
        if (particle == null) return;
        try {
            Location base = player.getLocation().add(0, 1.0, 0);
            player.getWorld().spawnParticle(particle, base, Math.max(1, item.particleCount()), 0.4, 0.6, 0.4, 0.02);
        } catch (RuntimeException ignored) {
            // partikel butuh data tambahan (mis. DUST): abaikan
            return;
        }
        if (item.animation()) spiral(plugin, player, particle);
    }

    private static void spiral(Plugin plugin, Player player, Particle particle) {
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (!player.isOnline() || tick >= 20) {
                    cancel();
                    return;
                }
                double angle = tick * 0.6;
                double y = tick * 0.1;
                Location loc = player.getLocation();
                try {
                    for (int strand = 0; strand < 2; strand++) {
                        double a = angle + strand * Math.PI;
                        player.getWorld().spawnParticle(particle,
                                loc.clone().add(Math.cos(a) * 0.8, y, Math.sin(a) * 0.8), 1, 0, 0, 0, 0);
                    }
                } catch (RuntimeException ex) {
                    cancel();
                    return;
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}

package id.heartlifesteal.config;

import id.heartlifesteal.HeartLifestealPlugin;
import id.heartlifesteal.data.HeartLimits;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/** Salinan (snapshot) seluruh pengaturan dari config.yml dan recipe.yml. Tidak berubah setelah dibuat. */
public final class Settings {

    public record ItemSettings(boolean enabled, Material material, String name, List<String> lore,
                               int customModelData, String itemModel, boolean glow,
                               String sound, float soundVolume, float soundPitch,
                               Particle particle, int particleCount, boolean animation,
                               int heartsPerUse, boolean restoreToMax, boolean consumeOnFull) {}

    public record RecipeSettings(boolean enabled, boolean autoDiscover, int resultAmount,
                                 List<String> shape, Map<Character, Material> ingredients, int bloodCount) {}

    public record HudSettings(boolean enabled, int updateInterval, List<String> lines, int criticalThreshold) {}

    public record DatabaseSettings(String type, String sqliteFile, String host, int port, String database,
                                   String username, String password, boolean useSsl, String extraParams) {}

    public final HeartLimits limits;
    public final int syncIntervalTicks;
    public final boolean blockHealthBoost;

    public final boolean lifestealEnabled;
    public final int heartsLostPerDeath;
    public final boolean loseHeartOnNonPvp;
    public final int bloodPerKill;
    public final boolean antiFarmEnabled;
    public final int antiFarmCooldown;
    public final boolean antiFarmSameIp;

    public final ItemSettings blood;
    public final ItemSettings relic;
    public final RecipeSettings recipe;

    public final boolean eliminationEnabled;
    public final boolean banOnZero;
    public final String banReason;

    public final boolean reviveEnabled;
    public final int heartsOnRevive;
    public final GameMode reviveGameMode;

    public final int topSize;
    public final boolean topHideEliminated;

    public final HudSettings hud;
    public final DatabaseSettings database;

    private Settings(HeartLifestealPlugin plugin) {
        Logger log = plugin.getLogger();
        FileConfiguration c = plugin.getConfig();

        boolean elimination = c.getBoolean("elimination.enabled", true);

        int max = Math.max(1, c.getInt("settings.maximum-hearts", 10));
        int min = Math.max(0, c.getInt("settings.minimum-hearts", 0));
        if (min > max) {
            log.warning("settings.minimum-hearts lebih besar dari maximum-hearts; memakai 0.");
            min = 0;
        }
        int def = c.getInt("settings.default-hearts", 10);
        int floor = elimination ? min : Math.max(1, min);
        int safeDef = Math.max(Math.max(1, floor), Math.min(max, def));
        if (safeDef != def) log.warning("settings.default-hearts di luar batas; dipakai " + safeDef + ".");
        this.limits = new HeartLimits(safeDef, max, min, elimination);
        this.syncIntervalTicks = Math.max(0, c.getInt("settings.sync-interval", 200));
        this.blockHealthBoost = c.getBoolean("settings.block-health-boost", true);

        this.lifestealEnabled = c.getBoolean("lifesteal.enabled", true);
        this.heartsLostPerDeath = Math.max(0, c.getInt("lifesteal.hearts-lost-per-death", 1));
        this.loseHeartOnNonPvp = c.getBoolean("lifesteal.lose-heart-on-non-pvp-death", true);
        this.bloodPerKill = Math.max(0, c.getInt("lifesteal.blood-per-kill", 1));
        this.antiFarmEnabled = c.getBoolean("lifesteal.anti-farming.enabled", true);
        this.antiFarmCooldown = Math.max(0, c.getInt("lifesteal.anti-farming.cooldown", 300));
        this.antiFarmSameIp = c.getBoolean("lifesteal.anti-farming.block-same-ip", false);

        this.blood = readItem(c.getConfigurationSection("blood"), log, "blood",
                Material.REDSTONE, "&c&lBlood", Particle.HEART, false, 1);
        this.relic = readItem(c.getConfigurationSection("relic-of-life"), log, "relic-of-life",
                Material.NETHER_STAR, "&6&lRelic of Life", Particle.TOTEM_OF_UNDYING, true, 5);

        this.recipe = readRecipe(plugin, this.blood.material(), log);

        this.eliminationEnabled = elimination;
        this.banOnZero = c.getBoolean("elimination.ban-on-zero-hearts", true);
        this.banReason = c.getString("elimination.ban-reason", "&cKamu telah kehilangan seluruh heart!");

        this.reviveEnabled = c.getBoolean("revive.enabled", true);
        int reviveHearts = Math.max(1, c.getInt("revive.hearts-on-revive", 1));
        this.heartsOnRevive = Math.min(max, reviveHearts);
        GameMode gm = GameMode.SURVIVAL;
        String gmName = c.getString("revive.gamemode", "SURVIVAL");
        try {
            gm = GameMode.valueOf(gmName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            log.warning("revive.gamemode tidak valid: " + gmName + " (dipakai SURVIVAL)");
        }
        this.reviveGameMode = gm;

        this.topSize = Math.max(1, Math.min(50, c.getInt("leaderboard.size", 10)));
        this.topHideEliminated = c.getBoolean("leaderboard.hide-eliminated", false);

        List<String> lines = c.getStringList("hud.lines");
        this.hud = new HudSettings(
                c.getBoolean("hud.enabled", true),
                Math.max(0, c.getInt("hud.update-interval", 20)),
                lines.isEmpty() ? List.of("&6&lHEART LIFESTEAL", "&c❤ Hearts: &f%heart_current%/%heart_max%") : lines,
                Math.max(0, c.getInt("hud.critical-threshold", 2)));

        this.database = new DatabaseSettings(
                c.getString("database.type", "SQLITE"),
                c.getString("database.sqlite-file", "data.db"),
                c.getString("database.mysql.host", "localhost"),
                c.getInt("database.mysql.port", 3306),
                c.getString("database.mysql.database", "heartlifesteal"),
                c.getString("database.mysql.username", "root"),
                c.getString("database.mysql.password", ""),
                c.getBoolean("database.mysql.use-ssl", false),
                c.getString("database.mysql.extra-params", ""));
    }

    public static Settings load(HeartLifestealPlugin plugin) {
        return new Settings(plugin);
    }

    // ------------------------------------------------------------------ helper

    private static ItemSettings readItem(ConfigurationSection s, Logger log, String id, Material defMaterial,
                                         String defName, Particle defParticle, boolean defGlow, int defHeartsPerUse) {
        if (s == null) s = new YamlConfiguration();
        ConfigurationSection fx = s.getConfigurationSection("effects");
        if (fx == null) fx = new YamlConfiguration();

        Material material = defMaterial;
        String matName = s.getString("material", defMaterial.name());
        Material parsed = Material.matchMaterial(matName == null ? "" : matName);
        if (parsed != null && parsed.isItem() && !parsed.isAir()) {
            material = parsed;
        } else {
            log.warning(id + ".material tidak valid: " + matName + " (dipakai " + defMaterial.name() + ")");
        }

        Particle particle = defParticle;
        String pName = fx.getString("particle", defParticle.name());
        if (pName == null || pName.isBlank() || pName.equalsIgnoreCase("NONE")) {
            particle = null;
        } else {
            try {
                particle = Particle.valueOf(pName.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                log.warning(id + ".effects.particle tidak valid: " + pName + " (dipakai " + defParticle.name() + ")");
            }
        }

        return new ItemSettings(
                s.getBoolean("enabled", true),
                material,
                s.getString("name", defName),
                s.getStringList("lore"),
                Math.max(0, s.getInt("custom-model-data", 0)),
                s.getString("item-model", ""),
                s.getBoolean("glow", defGlow),
                fx.getString("sound", ""),
                (float) fx.getDouble("volume", 1.0),
                (float) fx.getDouble("pitch", 1.0),
                particle,
                Math.max(1, fx.getInt("particle-count", 12)),
                fx.getBoolean("animation", true),
                Math.max(1, s.getInt("hearts-per-use", defHeartsPerUse)),
                s.getBoolean("restore-to-maximum", id.equals("relic-of-life")),
                s.getBoolean("consume-on-full-hearts", false));
    }

    private static RecipeSettings disabledRecipe() {
        return new RecipeSettings(false, false, 1, List.of(), Map.of(), 0);
    }

    private static RecipeSettings readRecipe(HeartLifestealPlugin plugin, Material bloodMaterial, Logger log) {
        File file = new File(plugin.getDataFolder(), "recipe.yml");
        if (!file.exists()) plugin.saveResource("recipe.yml", false);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);

        if (!y.getBoolean("enabled", true)) return disabledRecipe();

        List<String> rawShape = y.getStringList("shape");
        ConfigurationSection ing = y.getConfigurationSection("ingredients");
        if (rawShape.isEmpty() || rawShape.size() > 3 || ing == null) {
            log.warning("recipe.yml: 'shape' harus 1-3 baris dan 'ingredients' wajib ada. Recipe dinonaktifkan.");
            return disabledRecipe();
        }

        List<String> shape = new ArrayList<>();
        for (String row : rawShape) {
            if (row.length() > 3) {
                log.warning("recipe.yml: baris '" + row + "' lebih dari 3 karakter. Recipe dinonaktifkan.");
                return disabledRecipe();
            }
            shape.add(String.format("%-3s", row));
        }

        Map<Character, Material> map = new LinkedHashMap<>();
        Map<Character, Boolean> isBlood = new LinkedHashMap<>();
        for (String key : ing.getKeys(false)) {
            if (key.length() != 1) {
                log.warning("recipe.yml: kunci bahan '" + key + "' harus satu karakter (diabaikan).");
                continue;
            }
            String value = ing.getString(key, "");
            boolean blood = value.equalsIgnoreCase("BLOOD");
            Material m = blood ? bloodMaterial : Material.matchMaterial(value);
            if (m == null || !m.isItem() || m.isAir()) {
                log.warning("recipe.yml: bahan '" + key + "' tidak valid: " + value + ". Recipe dinonaktifkan.");
                return disabledRecipe();
            }
            map.put(key.charAt(0), m);
            isBlood.put(key.charAt(0), blood);
        }

        boolean any = false;
        int bloodCount = 0;
        for (String row : shape) {
            for (char ch : row.toCharArray()) {
                if (ch == ' ') continue;
                if (!map.containsKey(ch)) {
                    log.warning("recipe.yml: karakter '" + ch + "' ada di shape tetapi tidak ada di ingredients. "
                            + "Recipe dinonaktifkan.");
                    return disabledRecipe();
                }
                any = true;
                if (Boolean.TRUE.equals(isBlood.get(ch))) bloodCount++;
            }
        }
        if (!any) {
            log.warning("recipe.yml: shape kosong. Recipe dinonaktifkan.");
            return disabledRecipe();
        }

        return new RecipeSettings(true, y.getBoolean("auto-discover", true),
                Math.max(1, Math.min(64, y.getInt("result-amount", 1))), shape, map, bloodCount);
    }
}

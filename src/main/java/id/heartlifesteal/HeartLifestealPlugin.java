package id.heartlifesteal;

import id.heartlifesteal.command.HeartCommand;
import id.heartlifesteal.config.Messages;
import id.heartlifesteal.config.Settings;
import id.heartlifesteal.data.AntiFarm;
import id.heartlifesteal.data.DataManager;
import id.heartlifesteal.data.Database;
import id.heartlifesteal.hud.HudManager;
import id.heartlifesteal.item.ItemFactory;
import id.heartlifesteal.item.RecipeManager;
import id.heartlifesteal.listener.ConnectionListener;
import id.heartlifesteal.listener.DeathListener;
import id.heartlifesteal.listener.ItemListener;
import id.heartlifesteal.service.HeartService;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;

/** Heart Lifesteal — sistem heart permanen berbasis PvP untuk Paper 1.21.x. */
public final class HeartLifestealPlugin extends JavaPlugin {

    private volatile Settings settings;
    private Messages messages;
    private DataManager dataManager;
    private AntiFarm antiFarm;
    private ItemFactory items;
    private RecipeManager recipes;
    private HeartService hearts;
    private HudManager hud;
    private ItemListener itemListener;
    private BukkitTask syncTask;
    private BukkitTask hudTask;

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        saveDefaultConfig();
        if (!new File(getDataFolder(), "recipe.yml").exists()) saveResource("recipe.yml", false);

        this.messages = new Messages(this);
        this.settings = Settings.load(this);

        this.antiFarm = new AntiFarm();
        this.dataManager = new DataManager(createDatabase(), getLogger());
        try {
            dataManager.load(antiFarm, settings.antiFarmCooldown * 1000L);
        } catch (Exception ex) {
            getLogger().severe("Gagal memuat database: " + ex.getMessage());
            getLogger().severe("Plugin dinonaktifkan agar data heart tidak rusak. Periksa bagian 'database' di config.yml.");
            dataManager.shutdown();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.items = new ItemFactory(this);
        this.recipes = new RecipeManager(this);
        this.hearts = new HeartService(this);
        this.hud = new HudManager(this);
        this.itemListener = new ItemListener(this);

        Bukkit.getPluginManager().registerEvents(new ConnectionListener(this), this);
        Bukkit.getPluginManager().registerEvents(new DeathListener(this), this);
        Bukkit.getPluginManager().registerEvents(itemListener, this);

        PluginCommand command = getCommand("heart");
        if (command != null) {
            HeartCommand handler = new HeartCommand(this);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }

        recipes.register();
        restartTasks();

        // jika plugin dimuat ulang saat ada pemain online
        for (Player p : Bukkit.getOnlinePlayers()) hearts.handleJoin(p);

        getLogger().info("Heart Lifesteal aktif. Pemain tercatat: " + dataManager.all().size());
    }

    @Override
    public void onDisable() {
        cancelTasks();
        if (hud != null) hud.hideAll();
        if (recipes != null) recipes.unregister();
        if (dataManager != null) dataManager.shutdown();
    }

    private Database createDatabase() {
        Settings.DatabaseSettings d = settings.database;
        if (d.type().equalsIgnoreCase("MYSQL")) {
            return Database.mysql(d.host(), d.port(), d.database(), d.username(), d.password(),
                    d.useSsl(), d.extraParams());
        }
        return Database.sqlite(new File(getDataFolder(), d.sqliteFile()));
    }

    /** Muat ulang config.yml, messages.yml, dan recipe.yml (pengaturan database butuh restart). */
    public void reloadAll() {
        reloadConfig();
        messages.reload();
        this.settings = Settings.load(this);
        recipes.register();
        restartTasks();
        for (Player p : Bukkit.getOnlinePlayers()) {
            hearts.verify(p);
        }
        hud.reloadAll();
    }

    private void restartTasks() {
        cancelTasks();
        Settings s = this.settings;
        if (s.syncIntervalTicks > 0) {
            // pemeriksaan ringan: hanya membandingkan 1 angka atribut per pemain
            syncTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
                for (Player p : Bukkit.getOnlinePlayers()) hearts.verify(p);
            }, s.syncIntervalTicks, s.syncIntervalTicks);
        }
        if (s.hud.enabled() && s.hud.updateInterval() > 0) {
            hudTask = Bukkit.getScheduler().runTaskTimer(this, () -> hud.refreshAll(),
                    s.hud.updateInterval(), s.hud.updateInterval());
        }
    }

    private void cancelTasks() {
        if (syncTask != null) { syncTask.cancel(); syncTask = null; }
        if (hudTask != null) { hudTask.cancel(); hudTask = null; }
    }

    public Settings settings() { return settings; }
    public Messages messages() { return messages; }
    public DataManager data() { return dataManager; }
    public AntiFarm antiFarm() { return antiFarm; }
    public ItemFactory items() { return items; }
    public RecipeManager recipes() { return recipes; }
    public HeartService hearts() { return hearts; }
    public HudManager hud() { return hud; }
    public ItemListener itemListener() { return itemListener; }
}

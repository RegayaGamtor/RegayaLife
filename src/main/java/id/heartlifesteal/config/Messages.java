package id.heartlifesteal.config;

import id.heartlifesteal.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Pemuat messages.yml. Kunci yang hilang otomatis memakai nilai bawaan dari dalam jar. */
public final class Messages {
    private final JavaPlugin plugin;
    private YamlConfiguration cfg = new YamlConfiguration();

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) plugin.saveResource("messages.yml", false);
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);
        InputStream in = plugin.getResource("messages.yml");
        if (in != null) {
            loaded.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
        }
        this.cfg = loaded;
    }

    /** Teks mentah (masih berkode &) dengan placeholder terisi. */
    public String raw(String key, Object... kv) {
        String value = cfg.getString(key);
        if (value == null) value = "&c[pesan tidak ditemukan: " + key + "]";
        return Text.apply(value, kv);
    }

    public Component component(String key, Object... kv) {
        return Text.color(raw(key, kv));
    }

    public List<String> list(String key) {
        return cfg.getStringList(key);
    }

    /** Kirim pesan dengan prefix. Pesan kosong ("") berarti dimatikan dan tidak dikirim. */
    public void send(CommandSender to, String key, Object... kv) {
        String body = raw(key, kv);
        if (body.isEmpty()) return;
        to.sendMessage(Text.color(raw("prefix") + body));
    }

    /** Kirim tanpa prefix (untuk baris daftar/bantuan). */
    public void sendPlain(CommandSender to, String key, Object... kv) {
        String body = raw(key, kv);
        if (body.isEmpty()) return;
        to.sendMessage(Text.color(body));
    }
}

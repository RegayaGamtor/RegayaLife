package id.heartlifesteal.hud;

import id.heartlifesteal.HeartLifestealPlugin;
import id.heartlifesteal.config.Settings;
import id.heartlifesteal.data.PlayerData;
import id.heartlifesteal.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * HUD sidebar per pemain. Hanya menulis ulang scoreboard bila isinya benar-benar berubah,
 * dan di-refresh langsung saat heart berubah (event), jadi interval berkala hampir tanpa biaya.
 */
public final class HudManager {

    private static final class State {
        final Scoreboard board;
        final Objective objective;
        List<String> entries = new ArrayList<>();
        String title = "";

        State(Scoreboard board, Objective objective) {
            this.board = board;
            this.objective = objective;
        }
    }

    private final HeartLifestealPlugin plugin;
    private final Map<UUID, State> states = new HashMap<>(); // hanya diakses dari main thread

    public HudManager(HeartLifestealPlugin plugin) {
        this.plugin = plugin;
    }

    public void show(Player player) {
        if (!plugin.settings().hud.enabled()) return;
        State state = states.get(player.getUniqueId());
        if (state == null) {
            ScoreboardManager manager = Bukkit.getScoreboardManager();
            if (manager == null) return;
            Scoreboard board = manager.getNewScoreboard();
            Objective objective = board.registerNewObjective("hl_hud", Criteria.DUMMY, Component.text("HeartLifesteal"));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            hideNumbers(objective);
            state = new State(board, objective);
            states.put(player.getUniqueId(), state);
            player.setScoreboard(board);
        }
        render(player, state);
    }

    public void refresh(Player player) {
        if (!plugin.settings().hud.enabled()) return;
        State state = states.get(player.getUniqueId());
        if (state == null) {
            show(player);
        } else {
            render(player, state);
        }
    }

    public void refreshAll() {
        for (Player p : Bukkit.getOnlinePlayers()) refresh(p);
    }

    /** Lepas HUD dan kembalikan scoreboard utama server. */
    public void hide(Player player) {
        State state = states.remove(player.getUniqueId());
        if (state == null) return;
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null && player.isOnline()) player.setScoreboard(manager.getMainScoreboard());
    }

    /** Dipanggil saat pemain keluar (tidak perlu mengembalikan scoreboard). */
    public void forget(UUID id) {
        states.remove(id);
    }

    public void hideAll() {
        for (Player p : Bukkit.getOnlinePlayers()) hide(p);
        states.clear();
    }

    /** Dipanggil setelah /heart reload: terapkan pengaturan HUD yang baru. */
    public void reloadAll() {
        if (!plugin.settings().hud.enabled()) {
            hideAll();
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            State state = states.get(p.getUniqueId());
            if (state != null) {
                state.entries = new ArrayList<>();
                state.title = "";
                for (String old : new ArrayList<>(state.board.getEntries())) state.board.resetScores(old);
            }
            show(p);
        }
    }

    // ----------------------------------------------------------------- render

    private void render(Player player, State state) {
        Settings s = plugin.settings();
        PlayerData data = plugin.data().find(player.getUniqueId());
        if (data == null) return;

        List<String> lines = s.hud.lines();
        String title = Text.legacy(fill(lines.get(0), player, data, s));

        List<String> entries = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 1; i < lines.size(); i++) {
            String entry = Text.legacy(fill(lines.get(i), player, data, s));
            if (entry.isEmpty()) entry = " ";
            while (!seen.add(entry)) entry = entry + "\u00a7r"; // entri scoreboard harus unik
            entries.add(entry);
        }

        if (title.equals(state.title) && entries.equals(state.entries)) return; // tidak ada perubahan

        if (!title.equals(state.title)) {
            state.objective.displayName(Text.color(fill(lines.get(0), player, data, s)));
            state.title = title;
        }
        if (!entries.equals(state.entries)) {
            for (String old : state.entries) state.board.resetScores(old);
            int score = entries.size();
            for (String entry : entries) state.objective.getScore(entry).setScore(score--);
            state.entries = entries;
        }
    }

    private String fill(String line, Player player, PlayerData data, Settings s) {
        return line
                .replace("%player_name%", player.getName())
                .replace("%heart_current%", String.valueOf(data.hearts()))
                .replace("%heart_max%", String.valueOf(s.limits.maxHearts()))
                .replace("%heart_status%", plugin.hearts().statusText(data));
    }

    /** Sembunyikan angka merah di kanan sidebar (Paper 1.20.4+). Gagal diam-diam bila API tidak ada. */
    private static void hideNumbers(Objective objective) {
        try {
            Class<?> numberFormat = Class.forName("io.papermc.paper.scoreboard.numbers.NumberFormat");
            Object blank = numberFormat.getMethod("blank").invoke(null);
            Objective.class.getMethod("numberFormat", numberFormat).invoke(objective, blank);
        } catch (Throwable ignored) {
            // versi server tidak mendukung: angka tetap tampil, tidak masalah
        }
    }
}

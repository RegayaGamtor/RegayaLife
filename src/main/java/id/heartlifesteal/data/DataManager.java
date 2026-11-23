package id.heartlifesteal.data;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Cache memori seluruh data pemain + penulisan database asinkron lewat satu thread.
 * Main thread tidak pernah menunggu database (kecuali saat startup memuat data).
 */
public final class DataManager {

    @FunctionalInterface
    private interface SqlTask { void run() throws SQLException; }

    private final Database db;
    private final Logger log;
    private final Map<UUID, PlayerData> byUuid = new ConcurrentHashMap<>();
    private final Map<String, UUID> byName = new ConcurrentHashMap<>();
    private final ExecutorService executor;
    private volatile boolean closed;

    public DataManager(Database db, Logger log) {
        this.db = db;
        this.log = log;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "HeartLifesteal-DB");
            t.setDaemon(false);
            return t;
        });
    }

    // ---------------------------------------------------------------- startup

    /** Dipanggil sekali saat startup (memblokir, sebelum plugin aktif). */
    public void load(AntiFarm antiFarm, long cooldownMillis) throws SQLException {
        db.init();
        for (PlayerData d : db.loadPlayers()) {
            byUuid.put(d.uuid(), d);
            indexName(d);
        }
        long now = System.currentTimeMillis();
        db.loadCooldowns(now - Math.max(0, cooldownMillis), antiFarm::load);
        db.purgeCooldowns(now - Math.max(0, cooldownMillis));
    }

    // ----------------------------------------------------------------- lookup

    public PlayerData find(UUID uuid) { return byUuid.get(uuid); }

    public PlayerData findByName(String name) {
        if (name == null) return null;
        UUID id = byName.get(name.toLowerCase(Locale.ROOT));
        return id == null ? null : byUuid.get(id);
    }

    public Collection<PlayerData> all() { return byUuid.values(); }

    /** Ambil data pemain; buat baru (heart default) bila belum ada. Panggil dari main thread. */
    public PlayerData getOrCreate(UUID uuid, String name, int defaultHearts) {
        PlayerData d = byUuid.get(uuid);
        if (d == null) {
            d = new PlayerData(uuid, name, defaultHearts);
            byUuid.put(uuid, d);
            indexName(d);
            save(d);
            return d;
        }
        if (name != null && !d.name().equals(name)) {
            byName.remove(d.name().toLowerCase(Locale.ROOT), uuid);
            d.setName(name);
            indexName(d);
            save(d);
        }
        return d;
    }

    private void indexName(PlayerData d) {
        byName.put(d.name().toLowerCase(Locale.ROOT), d.uuid());
    }

    // ----------------------------------------------------------------- tulis

    /** Simpan salinan data secara asinkron. */
    public void save(PlayerData d) {
        PlayerData snapshot = d.copy();
        async("simpan pemain " + snapshot.name(), () -> db.upsertPlayer(snapshot));
    }

    public void saveCooldown(UUID killer, UUID victim, long time) {
        async("simpan cooldown Blood", () -> db.upsertCooldown(killer, victim, time));
    }

    public void recordElimination(UUID uuid, String name, UUID killerUuid, String killerName,
                                  String cause, long time) {
        async("catat eliminasi " + name, () -> db.insertElimination(uuid, name, killerUuid, killerName, cause, time));
    }

    public CompletableFuture<List<EliminationRecord>> history(UUID uuid, int limit) {
        CompletableFuture<List<EliminationRecord>> future = new CompletableFuture<>();
        if (closed) {
            future.completeExceptionally(new IllegalStateException("DataManager sudah ditutup"));
            return future;
        }
        executor.execute(() -> {
            try {
                future.complete(db.recentEliminations(uuid, limit));
            } catch (Exception ex) {
                db.reset();
                future.completeExceptionally(ex);
            }
        });
        return future;
    }

    private void async(String what, SqlTask task) {
        if (closed) return;
        executor.execute(() -> {
            for (int attempt = 1; attempt <= 2; attempt++) {
                try {
                    task.run();
                    return;
                } catch (SQLException ex) {
                    if (attempt == 2) {
                        log.log(Level.SEVERE, "Database gagal (" + what + ")", ex);
                    } else {
                        db.reset(); // sambungan putus: coba sekali lagi dengan koneksi baru
                    }
                }
            }
        });
    }

    // ---------------------------------------------------------------- shutdown

    /** Tunggu semua penulisan selesai lalu tutup koneksi. */
    public void shutdown() {
        closed = true;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(20, TimeUnit.SECONDS)) {
                log.warning("Penulisan database belum selesai setelah 20 detik; sebagian data mungkin belum tersimpan.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        db.close();
    }
}

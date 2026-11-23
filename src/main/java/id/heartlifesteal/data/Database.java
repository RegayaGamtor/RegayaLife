package id.heartlifesteal.data;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lapisan JDBC murni (SQLite default, MySQL opsional). Semua akses lewat satu koneksi
 * dan dipanggil dari satu thread DB, sehingga aman dari balapan data.
 */
public final class Database implements AutoCloseable {

    public enum Type { SQLITE, MYSQL }

    private final Type type;
    private final String url;
    private final String user;
    private final String password;
    private Connection connection;

    private Database(Type type, String url, String user, String password) {
        this.type = type;
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public static Database sqlite(File file) {
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null) parent.mkdirs();
        return new Database(Type.SQLITE, "jdbc:sqlite:" + file.getAbsolutePath(), null, null);
    }

    public static Database mysql(String host, int port, String database, String user, String password,
                                 boolean useSsl, String extraParams) {
        StringBuilder sb = new StringBuilder("jdbc:mysql://")
                .append(host).append(':').append(port).append('/').append(database)
                .append("?useSSL=").append(useSsl)
                .append("&characterEncoding=utf8&autoReconnect=true");
        if (extraParams != null && !extraParams.isBlank()) sb.append('&').append(extraParams);
        return new Database(Type.MYSQL, sb.toString(), user, password);
    }

    public Type type() { return type; }

    private synchronized Connection conn() throws SQLException {
        if (connection != null) {
            try {
                if (!connection.isClosed() && (type == Type.SQLITE || connection.isValid(2))) return connection;
            } catch (SQLException ignored) {
                // sambungan rusak, buat ulang di bawah
            }
            closeQuietly();
        }
        loadDriver();
        connection = (user == null)
                ? DriverManager.getConnection(url)
                : DriverManager.getConnection(url, user, password);
        return connection;
    }

    private void loadDriver() throws SQLException {
        String driver = type == Type.SQLITE ? "org.sqlite.JDBC" : "com.mysql.cj.jdbc.Driver";
        try {
            Class.forName(driver);
        } catch (ClassNotFoundException e) {
            throw new SQLException("Driver JDBC tidak ditemukan: " + driver
                    + " (Paper seharusnya sudah membawanya)", e);
        }
    }

    private synchronized void closeQuietly() {
        if (connection != null) {
            try { connection.close(); } catch (SQLException ignored) { }
            connection = null;
        }
    }

    /** Paksa koneksi dibuat ulang pada pemakaian berikutnya. */
    public synchronized void reset() { closeQuietly(); }

    @Override
    public synchronized void close() { closeQuietly(); }

    // ------------------------------------------------------------------ skema

    public synchronized void init() throws SQLException {
        String autoId = type == Type.SQLITE
                ? "INTEGER PRIMARY KEY AUTOINCREMENT"
                : "BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY";
        try (Statement st = conn().createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS hl_players ("
                    + "uuid VARCHAR(36) NOT NULL PRIMARY KEY,"
                    + "name VARCHAR(32) NOT NULL,"
                    + "hearts INTEGER NOT NULL,"
                    + "eliminated INTEGER NOT NULL DEFAULT 0,"
                    + "eliminated_at BIGINT NOT NULL DEFAULT 0,"
                    + "last_blood_at BIGINT NOT NULL DEFAULT 0,"
                    + "updated_at BIGINT NOT NULL DEFAULT 0)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS hl_blood_cooldowns ("
                    + "killer_uuid VARCHAR(36) NOT NULL,"
                    + "victim_uuid VARCHAR(36) NOT NULL,"
                    + "last_blood BIGINT NOT NULL,"
                    + "PRIMARY KEY (killer_uuid, victim_uuid))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS hl_eliminations ("
                    + "id " + autoId + ","
                    + "uuid VARCHAR(36) NOT NULL,"
                    + "name VARCHAR(32) NOT NULL,"
                    + "killer_uuid VARCHAR(36),"
                    + "killer_name VARCHAR(32),"
                    + "cause VARCHAR(100) NOT NULL,"
                    + "created_at BIGINT NOT NULL)");
        }
    }

    // ----------------------------------------------------------------- pemain

    public synchronized List<PlayerData> loadPlayers() throws SQLException {
        List<PlayerData> out = new ArrayList<>();
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT uuid, name, hearts, eliminated, eliminated_at, last_blood_at FROM hl_players")) {
            while (rs.next()) {
                UUID id;
                try {
                    id = UUID.fromString(rs.getString(1));
                } catch (IllegalArgumentException ex) {
                    continue; // baris rusak dilewati
                }
                out.add(new PlayerData(id, rs.getString(2), rs.getInt(3), rs.getInt(4) != 0,
                        rs.getLong(5), rs.getLong(6)));
            }
        }
        return out;
    }

    public synchronized void upsertPlayer(PlayerData d) throws SQLException {
        String sql = type == Type.SQLITE
                ? "INSERT INTO hl_players (uuid, name, hearts, eliminated, eliminated_at, last_blood_at, updated_at) "
                + "VALUES (?,?,?,?,?,?,?) ON CONFLICT(uuid) DO UPDATE SET name=excluded.name, hearts=excluded.hearts, "
                + "eliminated=excluded.eliminated, eliminated_at=excluded.eliminated_at, "
                + "last_blood_at=excluded.last_blood_at, updated_at=excluded.updated_at"
                : "INSERT INTO hl_players (uuid, name, hearts, eliminated, eliminated_at, last_blood_at, updated_at) "
                + "VALUES (?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE name=VALUES(name), hearts=VALUES(hearts), "
                + "eliminated=VALUES(eliminated), eliminated_at=VALUES(eliminated_at), "
                + "last_blood_at=VALUES(last_blood_at), updated_at=VALUES(updated_at)";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, d.uuid().toString());
            ps.setString(2, d.name());
            ps.setInt(3, d.hearts());
            ps.setInt(4, d.eliminated() ? 1 : 0);
            ps.setLong(5, d.eliminatedAt());
            ps.setLong(6, d.lastBloodAt());
            ps.setLong(7, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    // --------------------------------------------------------------- cooldown

    public synchronized void loadCooldowns(long sinceMillis, CooldownSink sink) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT killer_uuid, victim_uuid, last_blood FROM hl_blood_cooldowns WHERE last_blood >= ?")) {
            ps.setLong(1, sinceMillis);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        sink.accept(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)), rs.getLong(3));
                    } catch (IllegalArgumentException ignored) {
                        // baris rusak dilewati
                    }
                }
            }
        }
    }

    @FunctionalInterface
    public interface CooldownSink {
        void accept(UUID killer, UUID victim, long time);
    }

    public synchronized void upsertCooldown(UUID killer, UUID victim, long time) throws SQLException {
        String sql = type == Type.SQLITE
                ? "INSERT INTO hl_blood_cooldowns (killer_uuid, victim_uuid, last_blood) VALUES (?,?,?) "
                + "ON CONFLICT(killer_uuid, victim_uuid) DO UPDATE SET last_blood=excluded.last_blood"
                : "INSERT INTO hl_blood_cooldowns (killer_uuid, victim_uuid, last_blood) VALUES (?,?,?) "
                + "ON DUPLICATE KEY UPDATE last_blood=VALUES(last_blood)";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, killer.toString());
            ps.setString(2, victim.toString());
            ps.setLong(3, time);
            ps.executeUpdate();
        }
    }

    public synchronized void purgeCooldowns(long beforeMillis) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("DELETE FROM hl_blood_cooldowns WHERE last_blood < ?")) {
            ps.setLong(1, beforeMillis);
            ps.executeUpdate();
        }
    }

    // --------------------------------------------------------------- riwayat

    public synchronized void insertElimination(UUID uuid, String name, UUID killerUuid, String killerName,
                                               String cause, long time) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement(
                "INSERT INTO hl_eliminations (uuid, name, killer_uuid, killer_name, cause, created_at) "
                        + "VALUES (?,?,?,?,?,?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setString(3, killerUuid == null ? null : killerUuid.toString());
            ps.setString(4, killerName);
            ps.setString(5, cause.length() > 100 ? cause.substring(0, 100) : cause);
            ps.setLong(6, time);
            ps.executeUpdate();
        }
    }

    public synchronized List<EliminationRecord> recentEliminations(UUID uuid, int limit) throws SQLException {
        List<EliminationRecord> out = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT name, killer_name, cause, created_at FROM hl_eliminations "
                        + "WHERE uuid = ? ORDER BY created_at DESC LIMIT ?")) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new EliminationRecord(rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(4)));
                }
            }
        }
        return out;
    }
}

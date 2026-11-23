package id.heartlifesteal.data;

/** Satu baris riwayat eliminasi. */
public record EliminationRecord(String playerName, String killerName, String cause, long createdAt) {}

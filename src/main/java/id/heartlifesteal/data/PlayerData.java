package id.heartlifesteal.data;

import java.util.UUID;

/** Data heart satu pemain. Hanya dimodifikasi dari main thread; ke database dikirim salinannya. */
public final class PlayerData {
    private final UUID uuid;
    private String name;
    private int hearts;
    private boolean eliminated;
    private long eliminatedAt;
    private long lastBloodAt;

    public PlayerData(UUID uuid, String name, int hearts) {
        this.uuid = uuid;
        this.name = name == null ? "?" : name;
        this.hearts = hearts;
    }

    public PlayerData(UUID uuid, String name, int hearts, boolean eliminated, long eliminatedAt, long lastBloodAt) {
        this(uuid, name, hearts);
        this.eliminated = eliminated;
        this.eliminatedAt = eliminatedAt;
        this.lastBloodAt = lastBloodAt;
    }

    public UUID uuid() { return uuid; }
    public String name() { return name; }
    public int hearts() { return hearts; }
    public boolean eliminated() { return eliminated; }
    public long eliminatedAt() { return eliminatedAt; }
    public long lastBloodAt() { return lastBloodAt; }

    public void setName(String name) { this.name = name == null ? "?" : name; }
    public void setHearts(int hearts) { this.hearts = hearts; }
    public void setEliminated(boolean eliminated) { this.eliminated = eliminated; }
    public void setEliminatedAt(long eliminatedAt) { this.eliminatedAt = eliminatedAt; }
    public void setLastBloodAt(long lastBloodAt) { this.lastBloodAt = lastBloodAt; }

    public PlayerData copy() {
        return new PlayerData(uuid, name, hearts, eliminated, eliminatedAt, lastBloodAt);
    }
}

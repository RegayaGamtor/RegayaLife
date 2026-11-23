package id.heartlifesteal.data;

/**
 * Batas jumlah heart (logika murni, tanpa Bukkit).
 * 1 heart = 2 HP.
 */
public record HeartLimits(int defaultHearts, int maxHearts, int minHearts, boolean eliminationEnabled) {

    /** Batas bawah efektif: bila eliminasi mati, pemain tidak boleh turun di bawah 1 heart. */
    public int floor() {
        return eliminationEnabled ? Math.max(0, minHearts) : Math.max(1, minHearts);
    }

    public int clamp(int value) {
        return Math.max(floor(), Math.min(maxHearts, value));
    }
}

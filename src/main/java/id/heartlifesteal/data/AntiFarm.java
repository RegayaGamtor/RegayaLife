package id.heartlifesteal.data;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Anti-farming: cooldown Blood per pasangan (pembunuh, korban). Logika murni. */
public final class AntiFarm {
    private final Map<String, Long> lastBlood = new ConcurrentHashMap<>();

    private static String key(UUID killer, UUID victim) {
        return killer + ">" + victim;
    }

    /** Sisa cooldown dalam detik (0 bila boleh mendapat Blood). */
    public long remainingSeconds(UUID killer, UUID victim, long nowMs, long cooldownSeconds) {
        if (cooldownSeconds <= 0) return 0;
        Long last = lastBlood.get(key(killer, victim));
        if (last == null) return 0;
        long remainingMs = last + cooldownSeconds * 1000L - nowMs;
        return remainingMs <= 0 ? 0 : (remainingMs + 999) / 1000;
    }

    public void record(UUID killer, UUID victim, long nowMs) {
        lastBlood.put(key(killer, victim), nowMs);
        if (lastBlood.size() > 20_000) {
            // pengaman memori: buang entri yang sudah sangat lama
            long threshold = nowMs - 3_600_000L;
            lastBlood.values().removeIf(t -> t < threshold);
        }
    }

    public void load(UUID killer, UUID victim, long time) {
        lastBlood.put(key(killer, victim), time);
    }

    public int size() { return lastBlood.size(); }
}

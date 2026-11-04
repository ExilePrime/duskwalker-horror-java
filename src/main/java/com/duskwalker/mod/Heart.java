package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.HashMap;
import java.util.Map;

import static com.duskwalker.mod.Core.*;

/**
 * DUSKWALKER v12 - KALP ATISI MOTORU + AKIL SAGLIGI SISI (heart.js)
 * Kalp atisi: >=70 sessiz | 50-69: 58 bpm | 30-49: 78 | 15-29: 102 | <15: 128. Sis: 4 kademe.
 */
public final class Heart {
    private Heart() {}

    private static final Map<String, Long> nextBeat = new HashMap<>();
    private static final Map<String, Integer> fogTier = new HashMap<>();

    private static int bpmOf(Player player) {
        double s = getSanity(player);
        int bpm = s >= 70 ? 0 : s >= 50 ? 58 : s >= 30 ? 78 : s >= 15 ? 102 : 128;
        if (Noise.getNoise(player) > 70) bpm = (bpm == 0 ? 80 : bpm) + 14;
        return bpm;
    }

    private static int tierOf(double s) { return s >= 70 ? 0 : s >= 50 ? 1 : s >= 30 ? 2 : s >= 15 ? 3 : 4; }

    private static void setTier(Player p, int tier) {
        int cur = fogTier.getOrDefault(p.id(), 0);
        if (cur == tier) return;
        if (cur > 0) safe(() -> p.fogPop("duskwalker_sanity"));
        if (tier > 0) safe(() -> p.fogPush("duskwalker:sanity_fog_" + tier, "duskwalker_sanity"));
        fogTier.put(p.id(), tier);
    }

    public static void init() {
        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("heart")) return;
            long tick = Sys.currentTick();
            for (Player p : World.getPlayers()) {
                try {
                    if (!isSurvival(p) || realm.contains(p.id())) continue;
                    int bpm = bpmOf(p);
                    if (bpm == 0) continue;
                    if (tick < nextBeat.getOrDefault(p.id(), 0L)) continue;
                    double vol = Math.min(0.85, 0.25 + (bpm - 55) / 120.0);
                    playTo(p, "duskwalker.heartbeat", vol, 0.9 + (bpm - 55) / 400.0);
                    nextBeat.put(p.id(), tick + Math.round(TICKS_PER_SEC * 60.0 / bpm));
                } catch (Throwable e) { /* yut */ }
            }
        }, 4);

        Sys.runInterval(() -> {
            boolean on = !isDefeated() && isOptIn("sanity_fog");
            for (Player p : World.getPlayers()) {
                try {
                    if (!on || !isSurvival(p) || realm.contains(p.id())) { setTier(p, 0); continue; }
                    setTier(p, tierOf(getSanity(p)));
                } catch (Throwable e) { /* yut */ }
            }
        }, TICKS_PER_SEC * 2);

        try {
            Events.After.playerLeave.subscribe(ev -> { nextBeat.remove(ev.playerId); fogTier.remove(ev.playerId); });
        } catch (Throwable e) { /* yut */ }
    }
}

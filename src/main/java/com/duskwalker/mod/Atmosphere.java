package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.List;

import static com.duskwalker.mod.Core.*;

/**
 * DUSKWALKER v10 - ATMOSFER (atmosphere.js)
 * Alacakaranlik riuteli, safak, gece ambiyansi, paylasilan korku.
 */
public final class Atmosphere {
    private Atmosphere() {}

    private static int lastTime = -1;
    private static int lastDuskDay = -1;

    private static void onDusk(int day) {
        for (Player p : World.getPlayers()) {
            if (!isSurvival(p)) continue;
            title(p, "§8Alacakaranlik", "§7Gun " + day, 50);
            playTo(p, "ambient.cave", 0.7, 0.5);
            addStat(p, "dusksSeen");
        }
    }

    private static void onDawn() {
        for (Player p : World.getPlayers()) {
            if (!isSurvival(p)) continue;
            addStat(p, "nightsSurvived");
            maxStat(p, "longestDay", getDay());
            gainSanity(p, 6);
            actionbar(p, "§eSabah oldu. Bir geceyi daha atlattin.§r");
            playTo(p, "random.orb", 0.4, 1.4);
        }
    }

    // ---------------------------------------------------------
    //  Gece ambiyansi
    // ---------------------------------------------------------
    private static final Object[][] NIGHT_SOUNDS = {
        {"ambient.cave", 0.35, 0.6},
        {"mob.endermen.idle", 0.3, 0.5},
        {"mob.ghast.moan", 0.25, 0.5},
        {"mob.bat.idle", 0.3, 0.7},
        {"dig.wood", 0.4, 0.6}
    };

    private static double lerpChance(double sanity) {
        // saglamlik ne kadar dusukse o kadar sik: 75 -> %10, 0 -> %55
        return 0.10 + (75 - sanity) / 75 * 0.45;
    }

    public static void init() {
        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("atmosphere")) { lastTime = -1; return; }
            int t = timeOfDay();
            if (lastTime >= 0) {
                // aksam: 12500 esigi gecildi
                if (lastTime < 12500 && t >= 12500) {
                    int day = getDay();
                    if (lastDuskDay != day) { lastDuskDay = day; onDusk(day); }
                }
                // safak: zaman sarmaladi (gece -> gun) ya da uyuyarak atlandi
                if (lastTime > 18000 && t < 4000) onDawn();
            }
            lastTime = t;
        }, 40);

        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("atmosphere") || !isNight()) return;
            for (Player p : World.getPlayers()) {
                try {
                    if (!isSurvival(p)) continue;
                    double s = getSanity(p);
                    if (s > 75 || !chance(lerpChance(s))) continue;
                    Object[] snd = pick(NIGHT_SOUNDS);
                    playTo(p, (String) snd[0], ((Number) snd[1]).doubleValue(), ((Number) snd[2]).doubleValue() + rand(-0.1, 0.1));
                } catch (Throwable e) { /* yut */ }
            }
        }, 20 * 25);

        // Paylasilan korku (cok oyunculu)
        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("atmosphere")) return;
            List<Player> players = World.getPlayers();
            if (players.size() < 2) return;
            for (Player a : players) {
                try {
                    if (getSanity(a) > 20) continue;
                    for (Player b : players) {
                        if (a.id().equals(b.id()) || !a.dimension().id().equals(b.dimension().id())) continue;
                        if (hdist(a.location(), b.location()) > 40 || !chance(0.5)) continue;
                        say(b, "§8(" + a.name() + " yakininda bir sey fisildiyor...)§r");
                        playTo(b, "mob.endermen.idle", 0.4, 0.5);
                        loseSanity(b, 1);
                    }
                } catch (Throwable e) { /* yut */ }
            }
        }, TICKS_PER_MIN);
    }
}

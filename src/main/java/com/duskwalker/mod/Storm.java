package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import static com.duskwalker.mod.Core.*;

/**
 * DUSKWALKER v11 - FIRTINA OYUNLARI (storm.js)
 * Yildirim Silueti + gok gurultulu havada ekstra hayalet ses / adim.
 */
public final class Storm {
    private Storm() {}

    private static String weatherOf(Player player) {
        return safe(() -> player.dimension().getWeather(), (String) null); // "Clear" | "Rain" | "Thunder"
    }

    private static boolean stormy(Player player) {
        String w = weatherOf(player);
        return "Rain".equals(w) || "Thunder".equals(w);
    }

    private static void flash(Player player) {
        if (getPrefs(player).noflash) return;
        effect(player, "night_vision", 6, 0);
    }

    public static void init() {
        registerEvent(EventDef.of("lightning_silhouette").name("Lightning Silhouette").minDay(6).weight(3).cooldown(13L * TICKS_PER_MIN)
            .cond(p -> isNight() && !isUnderground(p) && (stormy(p) || getSanity(p) <= 50))
            .run(player -> {
                Dimension dimension = player.dimension();
                // gorus alaninda, ~16 blok ileride bir nokta
                Vec3 v = player.getViewDirection();
                Vec3 l = player.location();
                Vec3 spot = standSpot(dimension, new Vec3(l.x() + v.x() * 16, l.y(), l.z() + v.z() * 16), 0, 3, 16);
                if (spot == null) return false;

                flash(player);
                Sys.runTimeout(() -> {
                    if (!isValid(player)) return;
                    flash(player);
                    Entity st = spawnEffigy(dimension, spot, 1);
                    if (st != null) {
                        faceToward(st, eyePos(player));
                        // parlama sonunda kaybolur
                        Sys.runTimeout(() -> despawn(st, false), 8);
                    }
                    addStat(player, "silhouetteSeen");
                }, 12);
                Sys.runTimeout(() -> { if (isValid(player)) flash(player); }, 24);
                // gok gurultusu gecikmeli
                Sys.runTimeout(() -> {
                    if (!isValid(player)) return;
                    playTo(player, "ambient.weather.thunder", 0.9, 0.8);
                    loseSanity(player, 9);
                    addEvidence(player, 1);
                }, 60);
                return true;
            }));

        // gok gurultulu havada ekstra hayalet ses / adim
        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("lightning_silhouette")) return;
            for (Player p : World.getPlayers()) {
                try {
                    if (!isSurvival(p) || !isNight() || !"Thunder".equals(weatherOf(p)) || isUnderground(p)) continue;
                    if (!chance(0.18)) continue;
                    triggerEvent(chance(0.5) ? "phantom_sound" : "delayed_steps", p, false);
                } catch (Throwable e) { /* yut */ }
            }
        }, TICKS_PER_SEC * 20);
    }
}

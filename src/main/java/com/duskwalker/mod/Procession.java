package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.ArrayList;
import java.util.List;

import static com.duskwalker.mod.Core.*;

/**
 * DUSKWALKER v11 - ALAY (procession) VE HOLLOW NIGHT (procession.js)
 * Alay: gece gorus alaninin onunden soldan saga bes siluet gecer. Hollow Night: her 16 gecede bir.
 */
public final class Procession {
    private Procession() {}

    private static final double SPACING = 2.6;

    private static final class Walker {
        Entity e; double x, y, z;
        Walker(Entity e, double x, double y, double z) { this.e = e; this.x = x; this.y = y; this.z = z; }
    }

    private static long dayIndex() { return (long) Math.floor(safe(() -> (double) World.getAbsoluteTime(), 0.0) / 24000); }
    private static boolean isHollowNight() { return dayIndex() % 16 == 15; }

    private static int lastT = -1;
    private static long nextProcession = 0;

    public static void init() {
        registerEvent(EventDef.of("procession").name("Procession").minDay(12).weight(1).cooldown(40L * TICKS_PER_MIN)
            .cond(p -> isNight())
            .run(player -> {
                Dimension dimension = player.dimension();
                Vec3 l = player.location();
                Vec3 v = player.getViewDirection();
                double len = Math.hypot(v.x(), v.z());
                if (len == 0) len = 1;
                double fx = v.x() / len, fz = v.z() / len;
                // yol: oyuncunun ~20 blok onunden, "sol" -> "sag"
                double rx = -fz, rz = fx;
                double cx = l.x() + fx * 20, cz = l.z() + fz * 20;
                double sx = cx - rx * 22, sz = cz - rz * 22;
                Integer gy = groundY(dimension, sx, l.y(), sz);
                if (gy == null) return false;

                List<Walker> walkers = new ArrayList<>();
                for (int i = 0; i < 5; i++) {
                    double px = sx - rx * SPACING * i, pz = sz - rz * SPACING * i;
                    Integer gyi = groundY(dimension, px, gy, pz);
                    if (gyi == null) continue;
                    Entity st = spawnEffigy(dimension, new Vec3(px, gyi, pz), 2);
                    if (st != null) walkers.add(new Walker(st, px, gyi, pz));
                }
                if (walkers.size() < 3) { for (Walker w : walkers) despawn(w.e, false); return false; }

                final int[] ticks = { 0 };
                final int[] handle = { 0 };
                handle[0] = Sys.runInterval(() -> {
                    try {
                        ticks[0] += 5;
                        if (!isValid(player)) { Sys.clearRun(handle[0]); for (Walker w : walkers) despawn(w.e, false); return; }
                        for (Walker w : walkers) {
                            if (!isValid(w.e)) continue;
                            w.x += rx * 1.1; w.z += rz * 1.1;
                            Integer y = groundY(dimension, w.x, w.y, w.z);
                            if (y != null) w.y = y;
                            safe(() -> w.e.teleport(new Vec3(w.x, w.y, w.z), Opts.Teleport.dim(dimension).facing(new Vec3(w.x + rx * 4, w.y + 1.5, w.z + rz * 4))));
                        }
                        if (ticks[0] % 20 == 0) playAt(dimension, "step.stone", new Vec3(walkers.get(0).x, walkers.get(0).y, walkers.get(0).z), 0.5, 0.55);
                        if (ticks[0] >= 120) {
                            Sys.clearRun(handle[0]);
                            for (Walker w : walkers) despawn(w.e, true);
                        }
                    } catch (Throwable e) { safe(() -> Sys.clearRun(handle[0])); }
                }, 5);

                playTo(player, "mob.wither.ambient", 0.5, 0.45);
                loseSanity(player, 12);
                addEvidence(player, 2);
                addStat(player, "processionSeen");
                return true;
            }));

        // Hollow Night: her 16 gecede bir
        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("procession")) { lastT = -1; return; }
            int t = timeOfDay();
            if (lastT >= 0 && lastT < 12500 && t >= 12500 && isHollowNight()) {
                nextProcession = Sys.currentTick() + 60L * TICKS_PER_SEC;
                for (Player p : World.getPlayers()) {
                    title(p, tx("§8Bos Gece", "§8Hollow Night"), tx("§7bu gece gecit var", "§7a procession walks tonight"), 80);
                    addStat(p, "hollowNights");
                }
            }
            lastT = t;
        }, 40);

        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("procession") || !isNight() || !isHollowNight()) return;
            long tick = Sys.currentTick();
            if (tick < nextProcession) return;
            nextProcession = tick + 3L * TICKS_PER_MIN;
            for (Player p : World.getPlayers()) {
                try { if (isSurvival(p)) triggerEvent("procession", p, true); } catch (Throwable e) { /* yut */ }
            }
        }, TICKS_PER_SEC * 10);
    }
}

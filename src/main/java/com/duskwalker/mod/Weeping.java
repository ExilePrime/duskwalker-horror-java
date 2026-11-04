package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.duskwalker.mod.Core.*;

/**
 * DUSKWALKER v11 - "BAKMA / BAKARSAN" OLAYLARI (weeping.js)
 *  1) dont_blink  2) eyes_dark  3) shadow_follower
 */
public final class Weeping {
    private Weeping() {}

    // ---------------------------------------------------------
    //  1) DON'T BLINK
    // ---------------------------------------------------------
    private static final double BLINK_WATCH_COS = 0.62;   // bu degerin ustu "bakiyor" sayilir
    private static final double BLINK_STEP = 1.25;        // bakilmiyorken her 5 tikte ilerleme (blok)
    private static final double BLINK_CATCH = 3.1;        // yakalama mesafesi

    // ---------------------------------------------------------
    //  3) GOLGE TAKIPCI
    // ---------------------------------------------------------
    private static final class TrailPt {
        final double x, y, z; final String d;
        TrailPt(double x, double y, double z, String d) { this.x = x; this.y = y; this.z = z; this.d = d; }
    }
    private static final Map<String, List<TrailPt>> trails = new HashMap<>(); // oyuncu id -> son konumlar (5 tikte bir)
    private static final int TRAIL_MAX = 80;

    public static void init() {
        // ---- 1) DON'T BLINK ----
        registerEvent(EventDef.of("dont_blink").name("Don't Blink").minDay(9).weight(2).cooldown(22L * TICKS_PER_MIN)
            .cond(p -> isNight())
            .run(player -> {
                Dimension dimension = player.dimension();
                Vec3 start = standSpot(dimension, player.location(), 24, 32, 28);
                if (start == null) return false;
                Entity statue = spawnEffigy(dimension, start, 1);
                if (statue == null) return false;

                final Vec3[] cur = { new Vec3(start.x(), start.y(), start.z()) };
                final int[] ticks = { 0 };
                final int[] watchedTicks = { 0 };
                final int[] movedTicks = { 0 };
                final int[] handle = { 0 };
                String hint = tx("§8(ona bakmaya devam et...)§r", "§8(keep looking at it...)§r");
                say(player, tx("§8(uzakta bir siluet duruyor)§r", "§8(a silhouette stands in the distance)§r"));

                java.util.function.Consumer<Boolean> finish = caught -> {
                    Sys.clearRun(handle[0]);
                    if (caught) {
                        fade(player, 0, 0, 0, 0.05, 0.25, 0.7);
                        playTo(player, "mob.wither.spawn", 0.9, 0.5);
                        shake(player, 0.22, 1.4);
                        loseSanity(player, hasPerk(player, "stone_stare") ? 10 : 20);
                        addEvidence(player, 2);
                        addStat(player, "blinkCaught");
                        say(player, tx("§4Duskwalker§r: Gozlerini kirptin.", "§4Duskwalker§r: You blinked."));
                    } else {
                        addStat(player, "blinkSurvived");
                        actionbar(player, tx("§7...gitti§r", "§7...gone§r"));
                    }
                    Sys.runTimeout(() -> despawn(statue, true), caught ? 6 : 0);
                };

                handle[0] = Sys.runInterval(() -> {
                    try {
                        ticks[0] += 5;
                        if (!isValid(player) || !isValid(statue) || !player.dimension().id().equals(dimension.id())) {
                            Sys.clearRun(handle[0]); despawn(statue, false); return;
                        }
                        Vec3 c = cur[0];
                        Vec3 head = new Vec3(c.x(), c.y() + 1.7, c.z());
                        double dot = viewDot(player, head);
                        double d = hdist(c, player.location());
                        boolean watching = dot > BLINK_WATCH_COS;

                        if (watching) {
                            watchedTicks[0] += 5;
                            // bakisini kaybetmemesi icin arada bir nabiz
                            if (ticks[0] % 40 == 0) playTo(player, "mob.warden.heartbeat", 0.6, 0.9 + (1 - d / 32) * 0.4);
                        } else {
                            movedTicks[0] += 5;
                            double dx = player.location().x() - c.x(), dz = player.location().z() - c.z();
                            double len = Math.hypot(dx, dz);
                            if (len == 0) len = 1;
                            double nx = c.x() + dx / len * BLINK_STEP, nz = c.z() + dz / len * BLINK_STEP;
                            Integer gy = groundY(dimension, nx, c.y(), nz);
                            if (gy != null) {
                                cur[0] = new Vec3(nx, gy, nz);
                                Vec3 nc = cur[0];
                                safe(() -> statue.teleport(nc, Opts.Teleport.dim(dimension).facing(eyePos(player))));
                                if (movedTicks[0] % 10 == 0) playAt(dimension, "step.stone", nc, 0.7, 0.6);
                            }
                        }
                        faceToward(statue, eyePos(player));

                        if (d <= BLINK_CATCH) { finish.accept(true); return; }
                        // 40 sn dayandi / cok uzaklasti: kaybolur (oyuncu kurtuldu)
                        if (ticks[0] > 20 * 60 || d > 60) { finish.accept(false); return; }
                        // oyuncu 12+ sn boyunca hic bakmadiysa ipucu ver
                        if (watchedTicks[0] == 0 && ticks[0] == 20 * 12) actionbar(player, hint);
                    } catch (Throwable e) { safe(() -> Sys.clearRun(handle[0])); }
                }, 5);
                return true;
            }));

        // ---- 2) KARANLIKTAKI GOZLER ----
        registerEvent(EventDef.of("eyes_dark").name("Eyes in the Dark").minDay(4).weight(4).cooldown(10L * TICKS_PER_MIN)
            .cond(p -> isNight())
            .run(player -> {
                Dimension dimension = player.dimension();
                Vec3 spot = standSpot(dimension, player.location(), 15, 22, 24);
                if (spot == null) return false;
                Vec3 center = new Vec3(spot.x(), spot.y() + 1.7, spot.z());
                final int[] ticks = { 0 };
                final int[] lookedTicks = { 0 };
                final int[] handle = { 0 };

                handle[0] = Sys.runInterval(() -> {
                    try {
                        ticks[0] += 4;
                        if (!isValid(player) || !player.dimension().id().equals(dimension.id())) { Sys.clearRun(handle[0]); return; }
                        double d = hdist(center, player.location());
                        double dot = viewDot(player, center);

                        // gozler oyuncuya dogru bakar: yatay dik vektor
                        double dx = player.location().x() - center.x(), dz = player.location().z() - center.z();
                        double len = Math.hypot(dx, dz);
                        if (len == 0) len = 1;
                        double rx = -dz / len * 0.17, rz = dx / len * 0.17;

                        // kirpma: her ~3 sn'de 4 tik gozler kapanir
                        boolean blinking = ticks[0] % 64 < 8;
                        if (!blinking) {
                            particle(dimension, "minecraft:redstone_ore_dust_particle", new Vec3(center.x() + rx, center.y(), center.z() + rz));
                            particle(dimension, "minecraft:redstone_ore_dust_particle", new Vec3(center.x() - rx, center.y(), center.z() - rz));
                        }

                        if (dot > 0.93) lookedTicks[0] += 4; else lookedTicks[0] = Math.max(0, lookedTicks[0] - 2);

                        if (lookedTicks[0] >= 36) {
                            // dogrudan bakildi: gozler soner
                            Sys.clearRun(handle[0]);
                            playAt(dimension, "mob.endermen.portal", center, 0.6, 0.5);
                            smoke(dimension, center, 5);
                            addStat(player, "eyesStaredDown");
                            loseSanity(player, 3);
                            return;
                        }
                        if (d < 7.5) {
                            // cok yaklasti: gozler birden yuzune sicrar gibi kaybolur
                            Sys.clearRun(handle[0]);
                            playTo(player, "mob.endermen.stare", 0.8, 0.5);
                            shake(player, 0.1, 0.6);
                            loseSanity(player, 9);
                            addEvidence(player, 1);
                            return;
                        }
                        if (ticks[0] > 20 * 26) Sys.clearRun(handle[0]);
                    } catch (Throwable e) { safe(() -> Sys.clearRun(handle[0])); }
                }, 4);
                return true;
            }));

        // ---- 3) GOLGE TAKIPCI ----
        Sys.runInterval(() -> {
            for (Player p : World.getPlayers()) {
                try {
                    Vec3 l = p.location();
                    List<TrailPt> t = trails.get(p.id());
                    if (t == null) { t = new ArrayList<>(); trails.put(p.id(), t); }
                    String dimId = p.dimension().id();
                    if (!t.isEmpty() && !t.get(t.size() - 1).d.equals(dimId)) t.clear();
                    t.add(new TrailPt(l.x(), l.y(), l.z(), dimId));
                    while (t.size() > TRAIL_MAX) t.remove(0);
                } catch (Throwable e) { /* yut */ }
            }
        }, 5);

        try {
            Events.After.playerLeave.subscribe(ev -> { trails.remove(ev.playerId); });
        } catch (Throwable e) { /* yut */ }

        registerEvent(EventDef.of("shadow_follower").name("Shadow Follower").minDay(8).weight(2).cooldown(24L * TICKS_PER_MIN)
            .cond(p -> {
                List<TrailPt> t = trails.get(p.id());
                return t != null && t.size() >= 60;
            })
            .run(player -> {
                Dimension dimension = player.dimension();
                List<TrailPt> trail = trails.get(player.id());
                if (trail == null || trail.size() < 60) return false;
                TrailPt first = trail.get(0);
                Entity statue = spawnEffigy(dimension, new Vec3(first.x, first.y, first.z), 4);
                if (statue == null) return false;

                final int[] lag = { 56 };           // ~14 sn geriden baslar
                final int[] ticks = { 0 };
                final int[] handle = { 0 };
                say(player, tx("§8(adimlarini biri tekrarliyor...)§r", "§8(someone is repeating your steps...)§r"));

                handle[0] = Sys.runInterval(() -> {
                    try {
                        ticks[0] += 5;
                        if (!isValid(player) || !isValid(statue) || !player.dimension().id().equals(dimension.id())) {
                            Sys.clearRun(handle[0]); despawn(statue, false); return;
                        }
                        List<TrailPt> t = trails.get(player.id());
                        if (t == null || t.size() < 4) return;
                        // her 3 sn'de bir 1 ornek yaklasir: oyuncu yavaslarsa golge yetisir
                        if (ticks[0] % 60 == 0 && lag[0] > 4) lag[0] -= 1;
                        int idx = Math.max(0, t.size() - 1 - lag[0]);
                        TrailPt a = t.get(idx), b = t.get(Math.min(t.size() - 1, idx + 1));
                        if (!a.d.equals(dimension.id())) { Sys.clearRun(handle[0]); despawn(statue, false); return; }
                        safe(() -> statue.teleport(new Vec3(a.x, a.y, a.z), Opts.Teleport.dim(dimension).facing(new Vec3(b.x, b.y + 1.5, b.z))));

                        double d = dist3(new Vec3(a.x, a.y, a.z), player.location());
                        if (d < 2.6) {
                            Sys.clearRun(handle[0]);
                            playTo(player, "mob.endermen.stare", 0.9, 0.4);
                            fade(player, 0, 0, 0, 0.05, 0.2, 0.6);
                            loseSanity(player, 14);
                            addEvidence(player, 2);
                            addStat(player, "shadowCaught");
                            Sys.runTimeout(() -> despawn(statue, true), 8);
                            return;
                        }
                        if (ticks[0] > 20 * 100) { Sys.clearRun(handle[0]); despawn(statue, true); addStat(player, "shadowEscaped"); }
                    } catch (Throwable e) { safe(() -> Sys.clearRun(handle[0])); }
                }, 5);
                return true;
            }));
    }
}

package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.duskwalker.mod.Core.*;

/**
 * DUSKWALKER v12 - KALABALIK (5 ozgun mekanik)  [v10/crowd.js]
 *  1) ring_of_faces, 2) wall_of_faces, 3) horizon_watchers, 4) death_echo, 5) distant_distress
 */
public final class Crowd {
    private Crowd() {}

    private static final Map<String, Long> distressCd = new HashMap<>();

    public static void init() {
        // 1) Yuzler halkasi
        registerEvent(EventDef.of("ring_of_faces").name("Ring of Faces").minDay(9).weight(2).cooldown(22L * TICKS_PER_MIN)
            .cond(p -> isNight())
            .run(Crowd::ringOfFaces));

        // 2) Yuzler duvari
        registerEvent(EventDef.of("wall_of_faces").name("Wall of Faces").minDay(10).weight(2).cooldown(24L * TICKS_PER_MIN)
            .cond(p -> isNight())
            .run(Crowd::wallOfFaces));

        // 3) Ufuk gozculeri
        registerEvent(EventDef.of("horizon_watchers").name("Horizon Watchers").minDay(6).weight(3).cooldown(26L * TICKS_PER_MIN)
            .cond(p -> isNight())
            .run(Crowd::horizonWatchers));

        // 4) Olum yankisi (cok oyunculu)
        try {
            Events.After.entityDie.subscribe(ev -> {
                try {
                    Entity d = ev.deadEntity;
                    if (d == null || !"minecraft:player".equals(d.typeId()) || !isEnabled("death_echo")) return;
                    for (Player p : World.getPlayers()) {
                        if (p.id().equals(d.id()) || !isSurvival(p)) continue;
                        playTo(p, "duskwalker.creak", 0.7, 0.55);
                        say(p, tx("§8(biri sustu...)§r", "§8(someone fell silent...)§r"));
                        loseSanity(p, 3);
                        addStat(p, "deathEchoes");
                    }
                } catch (Throwable e) { /* yut */ }
            });
        } catch (Throwable e) { /* yut */ }

        // 5) Uzak sikinti
        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("distant_distress")) return;
            List<Player> players = World.getPlayers();
            if (players.size() < 2) return;
            long tick = Sys.currentTick();
            for (Player a : players) {
                try {
                    if (getSanity(a) >= 12 || tick < distressCd.getOrDefault(a.id(), 0L)) continue;
                    distressCd.put(a.id(), tick + 3L * TICKS_PER_MIN);
                    for (Player b : players) {
                        if (b.id().equals(a.id()) || !isSurvival(b) || hdist(a.location(), b.location()) > 150) continue;
                        playTo(b, "duskwalker.whisper", 0.5, 0.55);
                        actionbar(b, tx("§8(uzakta biri cokuyor...)§r", "§8(someone far away is breaking...)§r"));
                        addStat(b, "distressHeard");
                    }
                } catch (Throwable e) { /* yut */ }
            }
        }, TICKS_PER_SEC * 20);

        try {
            Events.After.playerLeave.subscribe(ev -> distressCd.remove(ev.playerId));
        } catch (Throwable e) { /* yut */ }
    }

    // ---------------------------------------------------------
    //  1) Yuzler halkasi
    // ---------------------------------------------------------
    private static Boolean ringOfFaces(Player player) {
        Dimension dim = player.dimension();
        Vec3 l = player.location();
        List<Entity> ring = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            double a = (Math.PI * 2 * i) / 7 + rand(-0.15, 0.15);
            Vec3 spot = standSpot(dim, new Vec3(l.x() + Math.cos(a) * 13, l.y(), l.z() + Math.sin(a) * 13), 0, 1.5, 8);
            if (spot == null) continue;
            Entity e = spawnEffigy(dim, spot, 1 + (i % 4));
            if (e != null) { faceToward(e, eyePos(player)); ring.add(e); }
        }
        if (ring.size() < 4) { for (Entity e : ring) despawn(e, false); return false; }
        playTo(player, "duskwalker.drone", 0.8, 0.7);
        actionbar(player, tx("§8(etrafini sardilar)§r", "§8(they surrounded you)§r"));
        Sys.runTimeout(() -> {
            for (Entity e : ring) despawn(e, true);
            if (!isValid(player)) return;
            playTo(player, "duskwalker.tick", 1.0, 0.6);
            loseSanity(player, 9);
            addEvidence(player, 2);
        }, 6 * TICKS_PER_SEC);
        return true;
    }

    // ---------------------------------------------------------
    //  2) Yuzler duvari
    // ---------------------------------------------------------
    private static Boolean wallOfFaces(Player player) {
        Dimension dim = player.dimension();
        Vec3 l = player.location();
        Vec3 v = player.getViewDirection();
        double len = Math.hypot(v.x(), v.z());
        if (len == 0) len = 1;
        double fx = v.x() / len, fz = v.z() / len;
        Vec3 center = new Vec3(l.x() + fx * 12, l.y(), l.z() + fz * 12);
        double rx = -fz, rz = fx;
        int[] waited = { 0 };
        int[] lookTicks = { 0 };
        int[] n = { 0 };
        List<Entity>[] placed = new List[] { null };
        int[] handle = { 0 };
        handle[0] = Sys.runInterval(() -> {
            try {
                n[0] += 5;
                if (!isValid(player)) {
                    Sys.clearRun(handle[0]);
                    if (placed[0] != null) for (Entity e : placed[0]) despawn(e, false);
                    return;
                }
                if (placed[0] == null) {
                    waited[0] += 5;
                    // oyuncu duvarin oldugu yere BAKMIYORSA kur (en fazla 30 sn bekle)
                    if (viewDot(player, new Vec3(center.x(), center.y() + 1.5, center.z())) < 0.25) {
                        placed[0] = new ArrayList<>();
                        for (int i = -3; i <= 3; i++) {
                            double x = center.x() + rx * i * 1.6, z = center.z() + rz * i * 1.6;
                            Integer y = groundY(dim, x, l.y(), z);
                            if (y == null) continue;
                            Entity e = spawnEffigy(dim, new Vec3(x, y, z), 1 + ((i + 3) % 4));
                            if (e != null) { faceToward(e, eyePos(player)); placed[0].add(e); }
                        }
                        n[0] = 0;
                        if (placed[0].size() < 3) {
                            Sys.clearRun(handle[0]);
                            for (Entity e : placed[0]) despawn(e, false);
                        }
                    } else if (waited[0] > 20 * 30) {
                        Sys.clearRun(handle[0]);
                    }
                    return;
                }
                boolean looking = viewDot(player, new Vec3(center.x(), center.y() + 1.5, center.z())) > 0.8;
                lookTicks[0] = looking ? lookTicks[0] + 5 : 0;
                if (lookTicks[0] >= 20) {
                    Sys.clearRun(handle[0]);
                    for (Entity e : placed[0]) despawn(e, true);
                    playTo(player, "duskwalker.whisper", 0.6, 1.0);
                    actionbar(player, tx("§8(hic orada degillerdi)§r", "§8(they were never there)§r"));
                    loseSanity(player, 4);
                    addStat(player, "wallLooked");
                } else if (n[0] > 20 * 12) {
                    Sys.clearRun(handle[0]);
                    for (Entity e : placed[0]) despawn(e, true);
                    shake(player, 0.1, 0.8);
                    loseSanity(player, 9);
                    addEvidence(player, 2);
                }
            } catch (Throwable e) { safe(() -> Sys.clearRun(handle[0])); }
        }, 5);
        return true;
    }

    // ---------------------------------------------------------
    //  3) Ufuk gozculeri
    // ---------------------------------------------------------
    private static Boolean horizonWatchers(Player player) {
        Dimension dim = player.dimension();
        Vec3 l = player.location();
        int count = Math.min(12, 3 + (int) Math.floor(getDay() / 3.0));
        List<Entity> crowd = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double a = Math.random() * Math.PI * 2;
            double d = rand(38, 50);
            Vec3 spot = standSpot(dim, new Vec3(l.x() + Math.cos(a) * d, l.y(), l.z() + Math.sin(a) * d), 0, 2, 8);
            if (spot == null) continue;
            Entity e = spawnEffigy(dim, spot, 1 + (i % 4));
            if (e != null) { faceToward(e, eyePos(player)); crowd.add(e); }
        }
        if (crowd.size() < 2) { for (Entity e : crowd) despawn(e, false); return false; }
        say(player, tx("§8(ufukta " + crowd.size() + " siluet var)§r", "§8(" + crowd.size() + " silhouettes stand on the horizon)§r"));
        int[] n = { 0 };
        boolean[] turned = { false };
        int[] handle = { 0 };
        handle[0] = Sys.runInterval(() -> {
            try {
                n[0] += 10;
                if (!isValid(player) || n[0] > 20 * 100) {
                    Sys.clearRun(handle[0]);
                    for (Entity e : crowd) despawn(e, true);
                    return;
                }
                boolean near = false;
                for (Entity e : crowd) {
                    if (isValid(e) && dist3(e.location(), player.location()) < 24) { near = true; break; }
                }
                for (Entity e : crowd) {
                    if (!isValid(e)) continue;
                    if (turned[0]) {
                        Vec3 l2 = e.location(), p2 = player.location();
                        safe(() -> faceToward(e, new Vec3(l2.x() + (l2.x() - p2.x()), l2.y() + 1.5, l2.z() + (l2.z() - p2.z()))));
                    } else {
                        faceToward(e, eyePos(player));
                    }
                }
                if (near && !turned[0]) {
                    turned[0] = true;
                    playTo(player, "duskwalker.creak", 0.9, 0.6);
                    actionbar(player, tx("§8(hepsi ayni anda sirtini dondu)§r", "§8(they all turned their backs at once)§r"));
                    loseSanity(player, 6);
                    addStat(player, "watchersTurned");
                }
            } catch (Throwable e) { safe(() -> Sys.clearRun(handle[0])); }
        }, 10);
        return true;
    }
}

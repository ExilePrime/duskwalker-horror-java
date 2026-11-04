package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.duskwalker.mod.Core.*;

/**
 * DUSKWALKER v10 - DUNYA OLAYLARI (events_world.js)
 * 1) Sahte Cokme 2) Donen Kafalar 3) Durursan Gelir 4) Soguk Nokta 5) Sahte Arkadas
 */
public final class EventsWorld {
    private EventsWorld() {}

    private static final Set<String> HEAD_BLACKLIST = new HashSet<>(List.of(
        "minecraft:player", "minecraft:item", "minecraft:xp_orb", "minecraft:armor_stand", "minecraft:boat",
        "minecraft:minecart", "minecraft:arrow", "minecraft:painting", "minecraft:falling_block",
        "duskwalker:duskwalker", "duskwalker:seep", "duskwalker:watcher", "duskwalker:aylin", "duskwalker:echo"
    ));

    /** Core.effect yalniz Player aliyor; JS surumu herhangi bir varlik uzerinde calisiyordu. */
    private static void effectEnt(Entity e, String id, int ticks, int amplifier) {
        if (id.equals("darkness") && !isOptIn("darkness_fx")) return;
        safe(() -> e.addEffect(id, ticks, amplifier, false));
    }

    private static List<Entity> nearbyLivings(Player player, double radius, int limit) {
        Opts.EntityQuery q = Opts.EntityQuery.any().near(player.location(), radius).excludeFamily("monster");
        List<Entity> list = safe(() -> player.dimension().getEntities(q), new ArrayList<Entity>());
        List<Entity> out = new ArrayList<>();
        for (Entity e : list) {
            if (HEAD_BLACKLIST.contains(e.typeId())) continue;
            if (!safe(() -> e.hasHealth(), false)) continue;
            out.add(e);
            if (out.size() >= limit) break;
        }
        return out;
    }

    public static void init() {
        // ---------------------------------------------------------
        //  1) SAHTE COKME
        // ---------------------------------------------------------
        registerEvent(EventDef.of("freeze_frame").name("Sahte Cokme").minDay(10).weight(1).cooldown(25L * TICKS_PER_MIN)
            .cond(p -> getSanity(p) <= 85 && !safe(() -> p.isFlying(), false))
            .run(player -> {
                Dimension dimension = player.dimension();
                fade(player, 0, 0, 0, 0.2, 3.6, 0.6);
                effect(player, "slowness", 100, 255);
                effect(player, "blindness", 80, 0);
                effect(player, "mining_fatigue", 100, 255);
                title(player, "§8Baglanti kesildi", "§7...", 70);
                playTo(player, "mob.endermen.stare", 0.6, 0.4);

                // 5 sn sonra isik geri gelir; Duskwalker artik cok yakindadir ve kipirdamaz
                Sys.runTimeout(() -> {
                    if (!isValid(player)) return;
                    Vec3 spot = standSpot(dimension, behindPlayer(player, 3.5), 0, 2, 10);
                    if (spot == null) spot = behindPlayer(player, 4);
                    Entity statue = spawnStatue(dimension, spot);
                    if (statue == null) return;
                    faceToward(statue, eyePos(player));
                    playTo(player, "ambient.cave", 0.9, 0.5);
                    loseSanity(player, 12);
                    addEvidence(player, 2);
                    Sys.runTimeout(() -> despawn(statue, true), 60);
                }, 100);
                return true;
            }));

        // ---------------------------------------------------------
        //  2) DONEN KAFALAR
        // ---------------------------------------------------------
        registerEvent(EventDef.of("turning_heads").name("Donen Kafalar").minDay(5).weight(3).cooldown(12L * TICKS_PER_MIN)
            .cond(p -> isNight())
            .run(player -> {
                List<Entity> herd = nearbyLivings(player, 26, 14);
                if (herd.size() < 2) return false;

                for (Entity e : herd) effectEnt(e, "slowness", 20 * 12, 100);

                // faz 1: hepsi oyuncuya bakar (4 sn)
                int[] t = {0};
                int[] handle = {0};
                handle[0] = Sys.runInterval(() -> {
                    try {
                        t[0] += 4;
                        if (!isValid(player)) { Sys.clearRun(handle[0]); return; }
                        int phase = t[0] <= 80 ? 1 : 2;
                        for (Entity e : herd) {
                            if (!isValid(e)) continue;
                            if (phase == 1) {
                                faceToward(e, eyePos(player));
                            } else {
                                // faz 2: ayni anda oyuncunun ARKASINA bakarlar
                                Vec3 behind = behindPlayer(player, 12);
                                faceToward(e, new Vec3(behind.x(), e.location().y() + 1, behind.z()));
                            }
                        }
                        if (t[0] == 84) {
                            playTo(player, "mob.endermen.idle", 0.7, 0.5);
                            actionbar(player, "§8(hepsi ayni yere bakiyor...)§r");
                        }
                        if (t[0] >= 180) {
                            Sys.clearRun(handle[0]);
                            for (Entity e : herd) safe(() -> e.removeEffect("slowness"));
                        }
                    } catch (Throwable e) { safe(() -> Sys.clearRun(handle[0])); }
                }, 4);

                loseSanity(player, 6 + Math.min(6, herd.size()));
                return true;
            }));

        // ---------------------------------------------------------
        //  3) DURURSAN GELIR
        // ---------------------------------------------------------
        registerEvent(EventDef.of("stand_still").name("Durursan Gelir").minDay(7).weight(4).cooldown(12L * TICKS_PER_MIN)
            .cond(p -> stillSeconds(p) >= 15 && (isNight() || isUnderground(p)))
            .run(player -> {
                Dimension dimension = player.dimension();
                Vec3 home = new Vec3(player.location().x(), player.location().y(), player.location().z());
                Vec3 start = standSpot(dimension, home, 26, 32, 20);
                if (start == null) return false;
                Entity stalker = spawnStatue(dimension, start);
                if (stalker == null) return false;

                int[] elapsed = {0};
                Vec3[] cur = {new Vec3(start.x(), start.y(), start.z())};
                playTo(player, "mob.warden.heartbeat", 0.8, 0.8);
                say(player, "§8(kıpırdama...)§r");

                int[] handle = {0};
                handle[0] = Sys.runInterval(() -> {
                    try {
                        elapsed[0] += 10;
                        if (!isValid(player) || !isValid(stalker)) { Sys.clearRun(handle[0]); despawn(stalker, false); return; }
                        Vec3 l = player.location();
                        double moved = Math.hypot(l.x() - home.x(), l.z() - home.z());
                        double d = hdist(cur[0], l);

                        // oyuncu kipirdadi: kacti, yaratik dagilir
                        if (moved > 2.5) {
                            Sys.clearRun(handle[0]);
                            playTo(player, "random.fizz", 0.7, 0.6);
                            despawn(stalker, true);
                            actionbar(player, "§7...gitti§r");
                            loseSanity(player, 3);
                            return;
                        }

                        // her 2.5 sn'de bir 3 blok yaklasir; kalp atisi hizlanir
                        if (elapsed[0] % 50 == 0 && d > 5) {
                            double dx = (l.x() - cur[0].x()) / d, dz = (l.z() - cur[0].z()) / d;
                            double nx = cur[0].x() + dx * 3, nz = cur[0].z() + dz * 3;
                            Vec3 spot = standSpot(dimension, new Vec3(nx, cur[0].y(), nz), 0, 1.5, 6);
                            if (spot == null) spot = new Vec3(nx, cur[0].y(), nz);
                            cur[0] = new Vec3(spot.x(), spot.y(), spot.z());
                            safe(() -> stalker.teleport(cur[0], Opts.Teleport.dim(dimension).facing(eyePos(player))));
                        }
                        int beatEvery = d > 18 ? 40 : d > 10 ? 24 : 12;
                        if (elapsed[0] % beatEvery == 0) playTo(player, "mob.warden.heartbeat", 0.9, d > 10 ? 0.9 : 1.2);

                        // 5 blok altina gelirse son sans: hala kipirdamadiysa dehset
                        if (d <= 5) {
                            Sys.clearRun(handle[0]);
                            fade(player, 0, 0, 0, 0.05, 0.15, 0.5);
                            playTo(player, "mob.wither.spawn", 0.9, 0.4);
                            shake(player, 0.2, 1.2);
                            loseSanity(player, 18);
                            addEvidence(player, 2);
                            Sys.runTimeout(() -> despawn(stalker, true), 12);
                            return;
                        }
                        if (elapsed[0] > 20 * 45) { Sys.clearRun(handle[0]); despawn(stalker, true); }
                    } catch (Throwable e) { safe(() -> Sys.clearRun(handle[0])); }
                }, 10);
                return true;
            }));

        // ---------------------------------------------------------
        //  4) SOGUK NOKTA
        // ---------------------------------------------------------
        registerEvent(EventDef.of("cold_spot").name("Soguk Nokta").minDay(4).weight(3).cooldown(9L * TICKS_PER_MIN)
            .run(player -> {
                Dimension dimension = player.dimension();
                Vec3 spot = standSpot(dimension, frontPlayer(player, 7), 0, 3, 12);
                if (spot == null) return false;
                Vec3 center = new Vec3(spot.x(), spot.y() + 1, spot.z());
                int[] ticks = {0};
                boolean[] touched = {false};
                int[] handle = {0};

                handle[0] = Sys.runInterval(() -> {
                    try {
                        ticks[0] += 5;
                        if (!isValid(player) || ticks[0] > 20 * 28) { Sys.clearRun(handle[0]); return; }
                        // bolge: durgun, mavimsi duman
                        for (int i = 0; i < 3; i++) {
                            particle(dimension, "minecraft:basic_smoke_particle", new Vec3(
                                center.x() + rand(-1, 1), center.y() + rand(-0.8, 1.2), center.z() + rand(-1, 1)));
                        }
                        if (ticks[0] % 20 == 0) particle(dimension, "minecraft:blue_flame_particle", new Vec3(center.x(), center.y(), center.z()));
                        if (!touched[0] && dist3(player.location(), center) < 2.2) {
                            touched[0] = true;
                            effect(player, "slowness", 80, 1);
                            effect(player, "nausea", 60, 0);
                            playTo(player, "mob.endermen.stare", 0.5, 0.5);
                            actionbar(player, "§bicinden gecen bir sogukluk...§r");
                            loseSanity(player, 6);
                            addEvidence(player, 1);
                            Sys.runTimeout(() -> safe(() -> Sys.clearRun(handle[0])), 40);
                        }
                    } catch (Throwable e) { safe(() -> Sys.clearRun(handle[0])); }
                }, 5);
                return true;
            }));

        // ---------------------------------------------------------
        //  5) SAHTE ARKADAS
        // ---------------------------------------------------------
        registerEvent(EventDef.of("fake_friend").name("Sahte Arkadas").minDay(6).weight(2).cooldown(30L * TICKS_PER_MIN)
            .cond(p -> {
                if (getPrefs(p).nochat) return false;
                Set<String> online = new HashSet<>();
                for (Player x : World.getPlayers()) online.add(x.name());
                for (String n : getKnownNames()) if (!n.equals(p.name()) && !online.contains(n)) return true;
                return false;
            })
            .run(player -> {
                Set<String> online = new HashSet<>();
                for (Player x : World.getPlayers()) online.add(x.name());
                List<String> offline = new ArrayList<>();
                for (String n : getKnownNames()) if (!n.equals(player.name()) && !online.contains(n)) offline.add(n);
                if (offline.isEmpty()) return false;
                String name = pick(offline);
                say(player, "§e" + name + " oyuna katildi");
                Sys.runTimeout(() -> {
                    if (!isValid(player)) return;
                    say(player, "<" + name + "> arkandayim");
                    playTo(player, "ambient.cave", 0.6, 0.6);
                    loseSanity(player, 8);
                }, randInt(100, 180));
                Sys.runTimeout(() -> {
                    if (isValid(player)) say(player, "§e" + name + " oyundan ayrildi");
                }, 20 * 22);
                return true;
            }));
    }
}

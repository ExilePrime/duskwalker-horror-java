package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static com.duskwalker.mod.Core.*;

/**
 * DUSKWALKER v12 - PSIKE SISTEMLERI (12 ozgun mekanik)  [v10/psyche.js]
 * adrenaline, memory_marks, shaking_hands, microsleep, dark_hunger, death_scar,
 * breaking_point, grounding, panic_gasp, clarity (habituation core'da).
 */
public final class Psyche {
    private Psyche() {}

    private static void onlySurvivors(Consumer<Player> fn) {
        for (Player p : World.getPlayers()) {
            try { if (isSurvival(p) && !realm.contains(p.id())) fn.accept(p); } catch (Throwable e) { /* yut */ }
        }
    }

    private static final Map<String, Long> adrenalineCd = new HashMap<>();
    private static final String MARK_KEY = "duskwalker:v12marks";
    private static final Map<String, Long> markCd = new HashMap<>();
    private static final Set<String> breaking = new HashSet<>();

    private static final String SLEEP_KEY = "duskwalker:v12lastSleep";
    private static final Set<String> wasSleeping = new HashSet<>();
    private static final Map<String, Integer> darkTicks = new HashMap<>();
    private static final Map<String, Long> scarUntil = new HashMap<>();

    private static double absDay() { return Math.floor(safe(() -> (double) World.getAbsoluteTime(), 0.0) / 24000); }

    private static boolean isLightBlock(String typeId) {
        return typeId.endsWith("torch") || typeId.endsWith("lantern") || typeId.endsWith("campfire") || typeId.endsWith("candle");
    }

    public static void init() {
        // ---------------------------------------------------------
        //  Korku dinleyicileri: adrenalin, hatira isareti, kirilma
        // ---------------------------------------------------------
        scareListeners.add((p, amount, next) -> {
            long tick = Sys.currentTick();
            // 1) adrenalin
            if (isEnabled("adrenaline") && amount >= 10 && tick >= adrenalineCd.getOrDefault(p.id(), 0L)) {
                adrenalineCd.put(p.id(), tick + 60L * TICKS_PER_SEC);
                effect(p, "speed", 70, 1);
                actionbar(p, tx("§e(adrenalin!)§r", "§e(adrenaline!)§r"));
                addStat(p, "adrenalineRushes");
            }
            // 3) hatira isareti
            if (isEnabled("memory_marks") && amount >= 12 && !realm.contains(p.id())) {
                List<Object> marks = Json.asList(playerGet(p, MARK_KEY, null));
                if (marks == null) marks = Json.newList();
                Vec3 l = p.location();
                String dimId = p.dimension().id();
                boolean near = false;
                for (Object o : marks) {
                    Map<String, Object> m = Json.asMap(o);
                    if (m == null) continue;
                    if (dimId.equals(m.get("d")) && Math.hypot(Json.num(m.get("x"), 0) - l.x(), Json.num(m.get("z"), 0) - l.z()) < 12) { near = true; break; }
                }
                if (!near) {
                    Map<String, Object> rec = Json.newMap();
                    rec.put("x", Math.floor(l.x())); rec.put("z", Math.floor(l.z())); rec.put("y", Math.floor(l.y())); rec.put("d", dimId);
                    marks.add(rec);
                    while (marks.size() > 8) marks.remove(0);
                    playerSet(p, MARK_KEY, marks);
                }
            }
            // 8) cokus
            if (isEnabled("breaking_point") && next <= 0.5 && !breaking.contains(p.id())) startBreak(p);
        });

        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("memory_marks")) return;
            long tick = Sys.currentTick();
            onlySurvivors(p -> {
                List<Object> marks = Json.asList(playerGet(p, MARK_KEY, null));
                if (marks == null) marks = Json.newList();
                for (int i = 0; i < marks.size(); i++) {
                    Map<String, Object> m = Json.asMap(marks.get(i));
                    if (m == null) continue;
                    Vec3 pl = p.location();
                    if (!p.dimension().id().equals(m.get("d")) || Math.hypot(Json.num(m.get("x"), 0) - pl.x(), Json.num(m.get("z"), 0) - pl.z()) > 8) continue;
                    String key = p.id() + ":" + i;
                    if (tick < markCd.getOrDefault(key, 0L)) continue;
                    markCd.put(key, tick + 3L * TICKS_PER_MIN);
                    actionbar(p, tx("§8(bu yeri hatirliyorsun...)§r", "§8(you remember this place...)§r"));
                    playTo(p, "duskwalker.whisper", 0.5, 0.7);
                    loseSanity(p, 2);
                    addStat(p, "memoryMarks");
                }
            });
        }, TICKS_PER_SEC * 5);

        // ---------------------------------------------------------
        //  4) Titreyen eller
        // ---------------------------------------------------------
        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("shaking_hands")) return;
            onlySurvivors(p -> {
                if (getSanity(p) < 10) {
                    effect(p, "mining_fatigue", 120, 0);
                    if (chance(0.15)) actionbar(p, tx("§8(ellerin titriyor)§r", "§8(your hands are shaking)§r"));
                }
            });
        }, TICKS_PER_SEC * 4);

        // ---------------------------------------------------------
        //  5) Mikro uyku (uykusuzluk)
        // ---------------------------------------------------------
        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("microsleep")) return;
            for (Player p : World.getPlayers()) {
                try {
                    if (p.isSleeping()) {
                        wasSleeping.add(p.id());
                        safe(() -> p.setDynamicProperty(SLEEP_KEY, absDay()));
                    } else if (safe(() -> p.getDynamicProperty(SLEEP_KEY), (Object) null) == null) {
                        safe(() -> p.setDynamicProperty(SLEEP_KEY, absDay()));
                    }
                } catch (Throwable e) { /* yut */ }
            }
        }, 40);

        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("microsleep") || !isNight()) return;
            onlySurvivors(p -> {
                Object last = safe(() -> p.getDynamicProperty(SLEEP_KEY), (Object) absDay());
                double awake = absDay() - (last instanceof Number n ? n.doubleValue() : absDay());
                if (awake < 3 || !chance(0.3)) return;
                fade(p, 0, 0, 0, 0.15, 0.7, 0.5);
                effect(p, "slowness", 50, 1);
                actionbar(p, tx("§8(mikro uyku...)§r", "§8(microsleep...)§r"));
                addStat(p, "microsleeps");
                loseSanity(p, 2);
            });
        }, TICKS_PER_SEC * 75);

        // ---------------------------------------------------------
        //  6) Karanlik acligi
        // ---------------------------------------------------------
        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("dark_hunger")) return;
            onlySurvivors(p -> {
                if (!isNight() || !p.dimension().id().equals("minecraft:overworld") || refuge.getOrDefault(p.id(), 0) > 0) { darkTicks.put(p.id(), 0); return; }
                int n = darkTicks.getOrDefault(p.id(), 0) + 1;
                darkTicks.put(p.id(), n);
                if (n == 6) actionbar(p, tx("§8(karanlik acikiyor...)§r", "§8(the dark is getting hungry...)§r"));
                if (n >= 6) loseSanity(p, 1);
            });
        }, TICKS_PER_SEC * 10);

        // ---------------------------------------------------------
        //  7) Olum izi
        // ---------------------------------------------------------
        try {
            Events.After.entityDie.subscribe(ev -> {
                try {
                    if (ev.deadEntity != null && "minecraft:player".equals(ev.deadEntity.typeId()) && isEnabled("death_scar"))
                        scarUntil.put(ev.deadEntity.id(), Sys.currentTick() + 5L * TICKS_PER_MIN + 400);
                } catch (Throwable e) { /* yut */ }
            });
            Events.After.playerSpawn.subscribe(ev -> {
                try {
                    if (ev.initialSpawn || !isEnabled("death_scar")) return;
                    Player pl = ev.player;
                    Sys.runTimeout(() -> {
                        if (isValid(pl)) {
                            title(pl, tx("§8Iz", "§8The Scar"), tx("§7olum seni hatirliyor", "§7death remembers you"), 60);
                            addStat(pl, "scars");
                        }
                    }, 40);
                } catch (Throwable e) { /* yut */ }
            });
        } catch (Throwable e) { /* yut */ }

        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("death_scar")) return;
            long tick = Sys.currentTick();
            onlySurvivors(p -> {
                if (scarUntil.getOrDefault(p.id(), 0L) <= tick) return;
                effect(p, "darkness", 80, 0);
                playTo(p, "duskwalker.heartbeat", 0.4, 0.8);
            });
        }, TICKS_PER_SEC * 30);

        // ---------------------------------------------------------
        //  9) Topraklanma (egil + isiga bak)
        // ---------------------------------------------------------
        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("grounding")) return;
            onlySurvivors(p -> {
                if (!p.isSneaking() || stillSeconds(p) < 3 || getSanity(p) >= 99) return;
                Dimension dim = p.dimension();
                Vec3 l = p.location();
                Vec3 target = null;
                for (int i = 0; i < 70 && target == null; i++) {
                    Vec3 pos = new Vec3(Math.floor(l.x()) + randInt(-4, 4), Math.floor(l.y()) + randInt(-1, 3), Math.floor(l.z()) + randInt(-4, 4));
                    Block b = safe(() -> dim.getBlock(pos), (Block) null);
                    if (b != null && isLightBlock(b.typeId())) target = new Vec3(b.x() + 0.5, b.y() + 0.5, b.z() + 0.5);
                }
                if (target == null || viewDot(p, target) < 0.85) return;
                gainSanity(p, 1.5);
                addStat(p, "groundingSeconds", 2);
                actionbar(p, tx("§bnefes al... isiga bak§r", "§bbreathe... watch the light§r"));
            });
        }, TICKS_PER_SEC * 2);

        // ---------------------------------------------------------
        //  10) Panik solugu
        // ---------------------------------------------------------
        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("panic_gasp")) return;
            onlySurvivors(p -> {
                if (getSanity(p) < 20 && p.isSprinting()) {
                    playTo(p, "duskwalker.breath", 0.8, 1.2);
                    loseSanity(p, 1);
                    effect(p, "slowness", 30, 0);
                    addStat(p, "gasps");
                }
            });
        }, TICKS_PER_SEC * 2);

        // ---------------------------------------------------------
        //  11) Berraklik
        // ---------------------------------------------------------
        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("clarity")) return;
            long tick = Sys.currentTick();
            onlySurvivors(p -> {
                if (clarityUntil.getOrDefault(p.id(), 0L) > tick) return;
                if (tick - lastScareTick.getOrDefault(p.id(), 0L) < 5L * TICKS_PER_MIN) return;
                if (!isNight() || getSanity(p) < 40) return;
                clarityUntil.put(p.id(), tick + TICKS_PER_MIN);
                actionbar(p, tx("§bzihin berrak§r", "§bclear mind§r"));
                addStat(p, "clarityMoments");
            });
        }, TICKS_PER_SEC * 20);

        try {
            Events.After.playerLeave.subscribe(ev -> {
                adrenalineCd.remove(ev.playerId); darkTicks.remove(ev.playerId); scarUntil.remove(ev.playerId);
                breaking.remove(ev.playerId); wasSleeping.remove(ev.playerId);
            });
        } catch (Throwable e) { /* yut */ }
    }

    // ---------------------------------------------------------
    //  8) Cokus noktasi
    // ---------------------------------------------------------
    private static void startBreak(Player p) {
        breaking.add(p.id());
        Dimension dim = p.dimension();
        lullUntil.put(p.id(), Sys.currentTick() + 30L * TICKS_PER_SEC);
        title(p, tx("§4Cokus", "§4Breaking Point"), tx("§7akil sagligin tukendi", "§7your mind has run out"), 80);
        effect(p, "blindness", 60, 0);
        effect(p, "slowness", 200, 1);
        effect(p, "nausea", 200, 0);
        playTo(p, "duskwalker.heartbeat", 1.0, 1.4);
        List<Entity> ring = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            double a = (Math.PI * 2 * i) / 6;
            Vec3 l = p.location();
            double x = l.x() + Math.cos(a) * 6, z = l.z() + Math.sin(a) * 6;
            Integer y = groundY(dim, x, l.y(), z);
            if (y == null) continue;
            Entity e = spawnEffigy(dim, new Vec3(x, y, z), 1 + (i % 4));
            if (e != null) { faceToward(e, eyePos(p)); ring.add(e); }
        }
        int[] n = { 0 };
        int[] handle = { 0 };
        handle[0] = Sys.runInterval(() -> {
            try {
                n[0]++;
                if (!isValid(p)) {
                    Sys.clearRun(handle[0]);
                    for (Entity e : ring) despawn(e, false);
                    breaking.remove(p.id());
                    return;
                }
                if (n[0] % 2 == 0) playTo(p, "duskwalker.heartbeat", 0.9, 1.3);
                if (n[0] % 5 == 0) playTo(p, "duskwalker.whisper", 0.8, rand(0.6, 1.1));
                for (Entity e : ring) if (isValid(e)) faceToward(e, eyePos(p));
                if (n[0] >= 20) {
                    Sys.clearRun(handle[0]);
                    for (Entity e : ring) despawn(e, true);
                    setSanity(p, 25);
                    actionbar(p, tx("§7(sessizlik... sonra nefes)§r", "§7(silence... then breath)§r"));
                    addStat(p, "breakingPoints");
                    Sys.runTimeout(() -> breaking.remove(p.id()), 100);
                }
            } catch (Throwable e) { safe(() -> Sys.clearRun(handle[0])); }
        }, 20);
    }
}

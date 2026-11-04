package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.duskwalker.mod.Core.*;

/**
 * DUSKWALKER v12 - GOKYUZU VE GUN DONUMLERI (5 ozgun mekanik)  [v10/sky.js]
 *  1) moon_gaze, 2) dusk_choir, 3) night_marker, 4) gift_at_dawn, 5) eclipse_vigil
 */
public final class Sky {
    private Sky() {}

    private static void onlySurvivors(Consumer<Player> fn) {
        for (Player p : World.getPlayers()) {
            try { if (isSurvival(p) && !realm.contains(p.id())) fn.accept(p); } catch (Throwable e) { /* yut */ }
        }
    }

    private static final Map<String, Integer> gaze = new HashMap<>();   // oyuncu id -> bakilan tik
    private static final Map<String, Long> gazeCd = new HashMap<>();

    private static double[] moonDir() {
        double g = (timeOfDay() / 24000.0) * Math.PI * 2; // 0 = dogu (+x)
        return new double[] { -Math.cos(g), -Math.sin(g), 0 };
    }

    private static int lastT = -1;
    private static final String MARKERS = "duskwalker:v12markers";

    private static final Map<String, Long> vigilDone = new HashMap<>(); // oyuncu id -> son odul gunu

    private static void dawnFor(Player p) {
        Vec3 l = p.location();
        Dimension dim = p.dimension();
        // 3) isaret tabelasi
        if (isEnabled("night_marker") && !isUnderground(p) && dim.id().equals("minecraft:overworld")) {
            Vec3 pos = new Vec3(Math.floor(l.x()), Math.floor(l.y()), Math.floor(l.z()));
            Block b = safe(() -> dim.getBlock(pos), (Block) null);
            Block below = safe(() -> dim.getBlock(new Vec3(pos.x(), pos.y() - 1, pos.z())), (Block) null);
            if (b != null && below != null && b.typeId().equals("minecraft:air") && !below.typeId().equals("minecraft:air")
                && !below.typeId().contains("water") && !below.typeId().contains("lava")) {
                try {
                    b.setType("minecraft:standing_sign");
                    double n = getStat(p, "nightsSurvived", 0) + 1;
                    String nm = p.name().toUpperCase();
                    if (nm.length() > 14) nm = nm.substring(0, 14);
                    String ns = Json.stringify(n);
                    b.setSignText(tx("GECE " + ns + "\nATLATILDI\n" + nm, "NIGHT " + ns + "\nSURVIVED\n" + nm));
                    List<Object> marks = Json.asList(playerGet(p, MARKERS, null));
                    if (marks == null) marks = Json.newList();
                    Map<String, Object> rec = Json.newMap();
                    rec.put("x", pos.x()); rec.put("y", pos.y()); rec.put("z", pos.z()); rec.put("d", dim.id());
                    marks.add(rec);
                    while (marks.size() > 6) {
                        Map<String, Object> old = Json.asMap(marks.remove(0));
                        safe(() -> {
                            Dimension d2 = World.getDimension(Json.str(old.get("d"), "minecraft:overworld").replace("minecraft:", ""));
                            Block ob = d2.getBlock(new Vec3(Json.num(old.get("x"), 0), Json.num(old.get("y"), 0), Json.num(old.get("z"), 0)));
                            if (ob != null && ob.typeId().equals("minecraft:standing_sign")) ob.setType("minecraft:air");
                        });
                    }
                    playerSet(p, MARKERS, marks);
                    addStat(p, "markersPlaced");
                } catch (Throwable e) { /* yut */ }
            }
        }
        // 4) hediye
        if (isEnabled("gift_at_dawn") && chance(0.35)) {
            try {
                String[][] gifts = { { "minecraft:bread", "3" }, { "minecraft:torch", "8" }, { "minecraft:golden_carrot", "2" }, { "minecraft:candle", "2" }, { "minecraft:apple", "2" } };
                String[] g = pick(gifts);
                ItemStack it = new ItemStack(g[0], Integer.parseInt(g[1]));
                it.nameTag = tx("§7Birakilmis", "§7Left Behind");
                it.setLore(tx("§8Sana birakilmis. Kim?", "§8Left for you. By whom?"));
                dim.spawnItem(it, new Vec3(l.x() + 1, l.y() + 0.5, l.z()));
                addStat(p, "giftsFound");
            } catch (Throwable e) { /* yut */ }
        }
    }

    public static void init() {
        // 1) Aya bakis
        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("moon_gaze") || !isNight()) { gaze.clear(); return; }
            long tick = Sys.currentTick();
            double[] md = moonDir();
            if (md[1] < 0.12) { gaze.clear(); return; }
            onlySurvivors(p -> {
                if (isUnderground(p) || !p.dimension().id().equals("minecraft:overworld") || tick < gazeCd.getOrDefault(p.id(), 0L)) return;
                Vec3 v = p.getViewDirection();
                double dot = v.x() * md[0] + v.y() * md[1] + v.z() * md[2];
                if (dot < 0.985) { gaze.remove(p.id()); return; }
                int n = gaze.getOrDefault(p.id(), 0) + 1;
                gaze.put(p.id(), n);
                if (n < 8) return;                     // 8 * 10 tik = 4 saniye
                gaze.remove(p.id());
                gazeCd.put(p.id(), tick + 4L * TICKS_PER_MIN);
                addStat(p, "moonGazes");
                int ph = moonPhase();
                if (ph == 0) { title(p, tx("§fAy da sana bakiyor", "§fThe moon stares back"), "", 60); loseSanity(p, 4); playTo(p, "duskwalker.drone", 0.6, 1.2); }
                else if (ph == 1) { effect(p, "nausea", 120, 0); actionbar(p, tx("§7(iki ay... hangisi gercek?)§r", "§7(two moons... which is real?)§r")); loseSanity(p, 4); }
                else if (ph == 3) { actionbar(p, tx("§8(orada hicbir sey yok)§r", "§8(there is nothing there)§r")); loseSanity(p, 6); playTo(p, "duskwalker.whisper", 0.7, 0.6); }
                else if (ph == 4) { effect(p, "darkness", 160, 0); playTo(p, "duskwalker.whisper", 0.9, 0.5); loseSanity(p, 5); }
                else if (ph == 7) { shake(p, 0.12, 1.0); loseSanity(p, 8); playTo(p, "duskwalker.heartbeat", 0.9, 1.3); }
                else { gainSanity(p, 2); actionbar(p, tx("§7(sakin bir ay. icini rahatlatti)§r", "§7(a quiet moon. it eased you)§r")); }
            });
        }, 10);

        // 2-5) Gunbatimi / safak dongusu
        Sys.runInterval(() -> {
            if (isDefeated()) { lastT = -1; return; }
            int t = timeOfDay();
            if (lastT >= 0) {
                // 2) gunbatimi korosu
                if (lastT < 12500 && t >= 12500 && isEnabled("dusk_choir") && chance(0.6)) {
                    for (Player p : World.getPlayers()) {
                        if (!isSurvival(p)) continue;
                        double[] pitches = { 0.8, 1.0, 1.2, 0.9 };
                        for (int i = 0; i < pitches.length; i++) {
                            double pitch = pitches[i];
                            Sys.runTimeout(() -> { if (isValid(p)) playTo(p, "duskwalker.drone", 0.55, pitch); }, i * 50);
                        }
                        Sys.runTimeout(() -> { if (isValid(p)) actionbar(p, tx("§8(uzaktan bir koro yukseliyor...)§r", "§8(a choir rises in the distance...)§r")); }, 20);
                        addStat(p, "choirs");
                    }
                }
                // 3-4) safak
                if (lastT > 18000 && t < 4000) onlySurvivors(Sky::dawnFor);
            }
            lastT = t;
        }, 40);

        // 5) Tutulma nobeti
        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("eclipse_vigil") || !isNight() || moonPhase() != 4) return;
            long day = (long) Math.floor(safe(() -> (double) World.getAbsoluteTime(), 0.0) / 24000);
            onlySurvivors(p -> {
                Long done = vigilDone.get(p.id());
                if ((done != null && done == day) || !isInRefuge(p) || stillSeconds(p) < 60) return;
                vigilDone.put(p.id(), day);
                gainSanity(p, 30);
                addStat(p, "eclipseVigils");
                title(p, tx("§6Tutulma Nobeti", "§6Eclipse Vigil"), tx("§7isigin yaninda bekledin", "§7you waited beside the light"), 80);
                playTo(p, "random.levelup", 0.8, 0.6);
                try { p.inventory().addItem(new ItemStack("minecraft:ender_pearl", 1)); } catch (Throwable e) { /* yut */ }
            });
        }, TICKS_PER_SEC * 10);

        try {
            Events.After.playerLeave.subscribe(ev -> { gaze.remove(ev.playerId); gazeCd.remove(ev.playerId); vigilDone.remove(ev.playerId); });
        } catch (Throwable e) { /* yut */ }
    }
}

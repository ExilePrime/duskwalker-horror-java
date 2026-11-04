package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.*;

import static com.duskwalker.mod.Core.*;

/**
 * DUSKWALKER v14 - #3 DUNUN SEN'I (Zamansal Yankilar) - past_selves.js'in Java karsiligi.
 * Her gece oyuncunun rotasi kaydedilir (2 sn'de bir konum + blok yerlestirme/kirma eylemleri);
 * sonraki gecelerde gecmis "sen" ayni rotada yurur. !dwpast / !dwpast now
 */
public final class PastSelves {
    private PastSelves() {}

    private static final String KEY_PAST = "duskwalker:v14past";
    private static final int MAX_NIGHTS = 3, MAX_PTS = 300;
    private static final int[] SKINS = { 1, 4, 2 };

    private static int absDay() { return (int) Math.floor(safe(() -> (double) World.getAbsoluteTime(), 0.0) / 24000); }

    private static String fmt(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return String.valueOf((long) v);
        return String.valueOf(v);
    }

    // ---------------------------------------------------------
    //  Kayit
    // ---------------------------------------------------------
    private static final class Act { final int idx; final String kind; Act(int idx, String kind) { this.idx = idx; this.kind = kind; } }

    private static final class Rec {
        int day;
        final List<int[]> pts = new ArrayList<>();
        final List<Act> acts = new ArrayList<>();
    }

    private static final Map<String, Rec> rec = new HashMap<>(); // id -> { day, pts, acts }

    private static Map<String, Object> loadData(Player p) {
        Object o = playerGet(p, KEY_PAST, null);
        Map<String, Object> m = Json.asMap(o);
        if (m == null) m = Json.newMap();
        if (Json.asList(m.get("nights")) == null) m.put("nights", Json.newList());
        return m;
    }

    private static void persist(Player p) {
        Rec r = rec.remove(p.id());
        if (r == null || r.pts.size() < 20) return;
        Map<String, Object> data = loadData(p);
        List<Object> nights = Json.asList(data.get("nights"));
        Map<String, Object> night = Json.newMap();
        night.put("day", (double) r.day);
        List<Object> pts = Json.newList();
        for (int[] pt : r.pts) pts.add(new ArrayList<Object>(List.of((double) pt[0], (double) pt[1], (double) pt[2])));
        List<Object> acts = Json.newList();
        for (Act a : r.acts) acts.add(new ArrayList<Object>(List.of((double) a.idx, a.kind)));
        night.put("pts", pts);
        night.put("acts", acts);
        nights.add(night);
        while (nights.size() > MAX_NIGHTS) nights.remove(0);
        playerSet(p, KEY_PAST, data);
    }

    private static void act(Player p, String kind) {
        Rec r = rec.get(p.id());
        if (r != null && r.acts.size() < 80) r.acts.add(new Act(r.pts.size(), kind));
    }

    // ---------------------------------------------------------
    //  Oynatma
    // ---------------------------------------------------------
    private static final Map<String, Set<String>> playedTonight = new HashMap<>(); // oyuncu id -> Set("gun:geceIndeksi")

    private static int nearestIndex(List<int[]> route, Vec3 loc) {
        int best = -1; double bd = 60;
        for (int i = 0; i < route.size() - 8; i++) {
            double d = hdist(new Vec3(route.get(i)[0], 0, route.get(i)[2]), loc);
            if (d < bd && Math.abs(route.get(i)[1] - loc.y()) < 30) { bd = d; best = i; }
        }
        return best;
    }

    private static boolean playRoute(Player p, List<int[]> route, List<Act> acts, int order, double nightDay) {
        Dimension dim = p.dimension();
        int start = nearestIndex(route, p.location());
        if (start < 0) return false;
        int[] first = route.get(start);
        Integer gy = groundY(dim, first[0] + 0.5, first[1], first[2] + 0.5);
        Entity ghost = spawnEffigy(dim, new Vec3(first[0] + 0.5, gy == null ? first[1] : gy, first[2] + 0.5), SKINS[order % SKINS.length]);
        if (ghost == null) return false;
        String[] trNames = { " Dunku ", " Evvelki ", " Eski " };
        String[] enNames = { "Yesterday's ", "Two nights ago: ", "Long ago: " };
        safe(() -> ghost.setNameTag(tx("§7" + trNames[order % 3].trim() + " " + p.name(), "§7" + enNames[order % 3] + p.name())));
        Map<Integer, String> actMap = new HashMap<>();
        for (Act a : acts) actMap.put(a.idx, a.kind);
        int[] k = { start };
        double[] frac = { 0 };
        int[] ticks = { 0 };
        boolean[] touched = { false };
        say(p, tx("§8(uzakta biri... senin gibi yuruyor)§r", "§8(someone in the distance... walks like you)§r"));
        int[] handle = { -1 };
        handle[0] = Sys.runInterval(() -> {
            try {
                ticks[0] += 10;
                if (!isValid(p) || !isValid(ghost) || !p.dimension().id().equals(dim.id())) { Sys.clearRun(handle[0]); despawn(ghost, false); return; }
                frac[0] += 0.25;                 // 40 tikte bir nokta => 10 tikte 0.25
                while (frac[0] >= 1) {
                    frac[0] -= 1; k[0]++;
                    String kind = actMap.get(k[0]);
                    if (kind != null) {
                        int[] rp = route.get(Math.min(k[0], route.size() - 1));
                        playAt(dim, kind.equals("p") ? "dig.stone" : "dig.gravel", new Vec3(rp[0], rp[1], rp[2]), 0.9, 0.8);
                    }
                }
                if (k[0] >= route.size() - 1) {
                    Sys.clearRun(handle[0]);
                    int[] end = route.get(route.size() - 1);
                    // sonuna kadar takip edildiyse hediye
                    if (hdist(new Vec3(end[0], 0, end[2]), p.location()) < 14) {
                        smoke(dim, new Vec3(end[0] + 0.5, end[1] + 1, end[2] + 0.5), 8);
                        gainSanity(p, 3);
                        if (chance(0.5)) {
                            safe(() -> {
                                ItemStack it = new ItemStack(pick("minecraft:golden_carrot", "minecraft:bread", "minecraft:torch", "minecraft:candle"), 3);
                                it.nameTag = tx("§7Dunku sen birakti", "§7Left by yesterday's you");
                                it.setLore(tx("§8Guvende oldugun yer burasiydi.", "§8This is where you were safe."));
                                dim.spawnItem(it, new Vec3(end[0] + 0.5, end[1] + 0.6, end[2] + 0.5));
                            });
                            actionbar(p, tx("§7(dunku sen sana bir sey birakti)§r", "§7(yesterday's you left something for you)§r"));
                            addStat(p, "pastGifts");
                        }
                    }
                    despawn(ghost, true);
                    return;
                }
                int[] a = route.get(k[0]), b = route.get(k[0] + 1);
                double x = a[0] + (b[0] - a[0]) * frac[0] + 0.5, y = a[1] + (b[1] - a[1]) * frac[0], z = a[2] + (b[2] - a[2]) * frac[0] + 0.5;
                safe(() -> ghost.teleport(new Vec3(x, y, z), Opts.Teleport.dim(dim).facing(new Vec3(b[0] + 0.5, b[1] + 1.6, b[2] + 0.5))));
                if (!touched[0] && hdist(new Vec3(x, 0, z), p.location()) < 4 && Math.abs(y - p.location().y()) < 4) {
                    touched[0] = true;
                    faceToward(ghost, eyePos(p));
                    playTo(p, "duskwalker.whisper", 0.8, 0.8);
                    actionbar(p, tx("§8(kendini gordun)§r", "§8(you saw yourself)§r"));
                    loseSanity(p, 5);
                    addStat(p, "metPastSelf");
                    Sys.runTimeout(() -> despawn(ghost, true), 16);
                    Sys.clearRun(handle[0]);
                    return;
                }
                if (ticks[0] > 20 * 140) { Sys.clearRun(handle[0]); despawn(ghost, true); }
            } catch (Throwable e) { safe(() -> Sys.clearRun(handle[0])); }
        }, 10);
        return true;
    }

    private static List<int[]> toRoute(Object o) {
        List<int[]> out = new ArrayList<>();
        List<Object> l = Json.asList(o);
        if (l == null) return out;
        for (Object pt : l) {
            List<Object> c = Json.asList(pt);
            if (c == null || c.size() < 3) continue;
            out.add(new int[] { (int) Json.num(c.get(0), 0), (int) Json.num(c.get(1), 0), (int) Json.num(c.get(2), 0) });
        }
        return out;
    }

    private static List<Act> toActs(Object o) {
        List<Act> out = new ArrayList<>();
        List<Object> l = Json.asList(o);
        if (l == null) return out;
        for (Object a : l) {
            List<Object> c = Json.asList(a);
            if (c == null || c.size() < 2) continue;
            out.add(new Act((int) Json.num(c.get(0), 0), Json.str(c.get(1), "")));
        }
        return out;
    }

    private static boolean trySpawn(Player p, boolean force) {
        Map<String, Object> data = loadData(p);
        List<Object> nights = Json.asList(data.get("nights"));
        if (nights.isEmpty()) return false;
        int day = absDay();
        Set<String> set = playedTonight.computeIfAbsent(p.id(), k -> new HashSet<>());
        boolean played = false;
        // en yeni gece once
        for (int i = nights.size() - 1; i >= 0; i--) {
            Map<String, Object> n = Json.asMap(nights.get(i));
            if (n == null) continue;
            double nDay = Json.num(n.get("day"), 0);
            String key = day + ":" + fmt(nDay);
            if (nDay == day || (!force && set.contains(key))) continue;
            if (playRoute(p, toRoute(n.get("pts")), toActs(n.get("acts")), nights.size() - 1 - i, nDay)) {
                set.add(key); played = true;
                if (!force) break;
            }
        }
        return played;
    }

    private static int lastT = -1;
    private static long nightStart = -1;

    // =========================================================
    //  Kayit
    // =========================================================
    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        initialized = true;

        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("past_selves") || !isNight()) return;
            int day = absDay();
            for (Player p : World.getPlayers()) {
                try {
                    if (!isSurvival(p) || realm.contains(p.id()) || !p.dimension().id().equals("minecraft:overworld")) continue;
                    Rec r = rec.get(p.id());
                    if (r == null || r.day != day) {
                        if (r != null) persist(p);
                        r = new Rec(); r.day = day; rec.put(p.id(), r);
                    }
                    if (r.pts.size() >= MAX_PTS) continue;
                    Vec3 l = p.location();
                    r.pts.add(new int[] { (int) Math.round(l.x()), (int) Math.round(l.y()), (int) Math.round(l.z()) });
                } catch (Throwable e) { /* yut */ }
            }
        }, 40);

        Events.After.playerPlaceBlock.subscribe(ev -> safe(() -> act(ev.player, "p")));
        Events.After.playerBreakBlock.subscribe(ev -> safe(() -> act(ev.player, "b")));
        Events.Before.playerLeave.subscribe(ev -> safe(() -> persist(ev.player)));

        Sys.runInterval(() -> {
            if (isDefeated()) { lastT = -1; return; }
            int t = timeOfDay();
            if (lastT > 18000 && t < 4000) for (Player p : World.getPlayers()) safe(() -> persist(p));
            lastT = t;
        }, 40);

        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("past_selves") || !isNight() || getDay() < 3) return;
            long tick = Sys.currentTick();
            if (nightStart < 0) nightStart = tick;
            if (tick - nightStart < 2 * TICKS_PER_MIN) return;
            for (Player p : World.getPlayers()) {
                try {
                    if (!isSurvival(p) || realm.contains(p.id()) || !p.dimension().id().equals("minecraft:overworld") || !chance(0.12)) continue;
                    trySpawn(p, false);
                } catch (Throwable e) { /* yut */ }
            }
        }, TICKS_PER_SEC * 10);

        Sys.runInterval(() -> { if (!isNight()) nightStart = -1; }, TICKS_PER_SEC * 20);

        Events.After.playerLeave.subscribe(ev -> { playedTonight.remove(ev.playerId); rec.remove(ev.playerId); });

        Events.Before.chatSend.subscribe(ev -> {
            String m = ev.message;
            if (!"!dwpast".equals(m) && !"!dwpast now".equals(m)) return;
            ev.cancel = true;
            Player p = ev.sender;
            Sys.run(() -> {
                try {
                    Map<String, Object> data = loadData(p);
                    if (m.equals("!dwpast now")) {
                        say(p, trySpawn(p, true) ? tx("§7Gecmis rotalar oynatiliyor.§r", "§7Replaying past routes.§r")
                            : tx("§7Yakinda oynatilacak bir rota yok (rotanin 60 blok yakininda ol).§r", "§7No route to replay nearby (stay within 60 blocks of a recorded route).§r"));
                        return;
                    }
                    say(p, tx("§6Dunun Sen'i§r\n§7Kayitli gece: §f", "§6Yesterday's You§r\n§7Recorded nights: §f") + Json.asList(data.get("nights")).size() + "/3"
                        + tx("\n§8Rota yakinindayken, gece, gecmis sen yurur.", "\n§8Near a recorded route at night, your past self walks it."));
                } catch (Throwable e) { /* yut */ }
            });
        });
    }
}

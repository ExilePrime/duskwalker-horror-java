package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.*;

import static com.duskwalker.mod.Core.*;

/**
 * DUSKWALKER v14 - #2 SIGINAK VE KUSATMA (ussu savunma oyunu) - sanctuary.js'in Java karsiligi.
 *
 * Bir MIKNATIS TASI (lodestone) yerlestirirsen orasi bir SIGINAK cekirdegi olur
 * (dunyada en fazla 2 siginak/oyuncu, aralarinda en az 40 blok).
 * SIGINAK ICINDE (yaricap seviyeye gore 12 / 20 / 28 / 36 blok): korku olaylarinin %90'i bastirilir, saglamlik yavasca toparlanir.
 * KUSATMA: her aksam ihtimalle ufuktan "hayaletler" siginaga yurur; ona bakarsan kipirdamaz,
 * yaninda isik varsa dagilir, cekirdege ulasan 12 butunluk goturur.
 * !dwsanctuary : durum    !dwsanctuary siege : kusatmayi simdi baslat (test)
 */
public final class Sanctuary {
    private Sanctuary() {}

    private static final String SANCT_KEY = "duskwalker:v14sanct";
    private static final int[] RADII = { 12, 20, 28, 36 };
    private static final String[] LIGHT_SUFFIX = { "torch", "lantern", "campfire", "glowstone", "shroomlight", "candle" };

    // ---------------------------------------------------------
    //  Yardimcilar (siginak kaydi = Json haritasi: x,y,z,d,owner,sieges,integrity,born)
    // ---------------------------------------------------------
    /** JS sayi -> metin: tam sayi degerli ise ondaliksiz. */
    private static String fmt(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return String.valueOf((long) v);
        return String.valueOf(v);
    }

    private static double nx(Map<String, Object> s, String k) { return Json.num(s.get(k), 0); }

    private static int levelOf(Map<String, Object> s) {
        double sg = nx(s, "sieges");
        return 1 + (sg >= 2 ? 1 : 0) + (sg >= 5 ? 1 : 0) + (sg >= 9 ? 1 : 0);
    }

    private static int radiusOf(Map<String, Object> s) { return RADII[levelOf(s) - 1]; }

    private static double hd(Map<String, Object> s, Vec3 l) { return Math.hypot(nx(s, "x") - l.x(), nx(s, "z") - l.z()); }

    private static List<Map<String, Object>> all() {
        List<Map<String, Object>> out = new ArrayList<>();
        Object raw = worldGet(SANCT_KEY, null);
        List<Object> l = Json.asList(raw);
        if (l != null) for (Object o : l) { Map<String, Object> m = Json.asMap(o); if (m != null) out.add(m); }
        return out;
    }

    private static void save(List<Map<String, Object>> list) { worldSet(SANCT_KEY, list); }

    private static Dimension dimById(String id) {
        return safe(() -> World.getDimension(id.replaceFirst("minecraft:", "")), (Dimension) null);
    }

    private static boolean isLight(String typeId) {
        for (String suf : LIGHT_SUFFIX) if (typeId.endsWith(suf)) return true;
        return false;
    }

    private static String sKey(Map<String, Object> s) {
        return Json.str(s.get("d"), "") + ":" + fmt(nx(s, "x")) + ":" + fmt(nx(s, "y")) + ":" + fmt(nx(s, "z"));
    }

    // ---------------------------------------------------------
    //  Siginak icindeki etkiler
    // ---------------------------------------------------------
    public static Map<String, Object> sanctuaryAt(Vec3 loc, String dimId) {
        for (Map<String, Object> s : all()) {
            if (!dimId.equals(Json.str(s.get("d"), null))) continue;
            if (hd(s, loc) <= radiusOf(s) && Math.abs(nx(s, "y") - loc.y()) <= 24) return s;
        }
        return null;
    }

    private static final Set<String> inside = new HashSet<>();

    // ---------------------------------------------------------
    //  Kusatma
    // ---------------------------------------------------------
    private static final class Wraith {
        Entity e; double x, y, z; String state;
        Wraith(Entity e, double x, double y, double z, String state) { this.e = e; this.x = x; this.y = y; this.z = z; this.state = state; }
    }

    private static final class Siege {
        Map<String, Object> s; Dimension dim; List<Wraith> wraiths; long start; int total; int repelled; int breached;
    }

    private static final Map<String, Siege> sieges = new LinkedHashMap<>(); // anahtar -> durum

    private static boolean lightNear(Dimension dim, Vec3 loc) {
        int bx = (int) Math.floor(loc.x()), by = (int) Math.floor(loc.y()), bz = (int) Math.floor(loc.z());
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 0; dy <= 2; dy++) {
            final int fx = bx + dx, fy = by + dy, fz = bz + dz;
            Block b = safe(() -> dim.getBlock(new Vec3(fx, fy, fz)), (Block) null);
            if (b != null && isLight(b.typeId())) return true;
        }
        return false;
    }

    private static List<Player> playersNear(String dimId, Map<String, Object> s, double dist, boolean survivalOnly) {
        List<Player> out = new ArrayList<>();
        for (Player p : World.getPlayers()) {
            if (!p.dimension().id().equals(dimId) || hd(s, p.location()) >= dist) continue;
            if (survivalOnly && !isSurvival(p)) continue;
            out.add(p);
        }
        return out;
    }

    public static boolean startSiege(Map<String, Object> s, boolean force) {
        String key = sKey(s);
        if (sieges.containsKey(key)) return false;
        Dimension dim = dimById(Json.str(s.get("d"), ""));
        if (dim == null) return false;
        List<Player> here = playersNear(Json.str(s.get("d"), ""), s, 70, true);
        if (here.isEmpty() && !force) return false;
        int count = (int) Math.min(16, 3 + levelOf(s) * 2 + Math.floor(getDay() / 6.0));
        int r = radiusOf(s);
        List<Wraith> wraiths = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double a = (Math.PI * 2 * i) / count + rand(-0.25, 0.25);
            double d = r + rand(24, 34);
            double x = nx(s, "x") + 0.5 + Math.cos(a) * d, z = nx(s, "z") + 0.5 + Math.sin(a) * d;
            Integer y = groundY(dim, x, nx(s, "y"), z);
            if (y == null) continue;
            Entity e = spawnEffigy(dim, new Vec3(x, y, z), 3);
            if (e != null) wraiths.add(new Wraith(e, x, y, z, "walking"));
        }
        if (wraiths.size() < 2) { for (Wraith w : wraiths) despawn(w.e, false); return false; }
        Siege st = new Siege();
        st.s = s; st.dim = dim; st.wraiths = wraiths; st.start = Sys.currentTick(); st.total = wraiths.size();
        sieges.put(key, st);
        for (Player p : here) {
            title(p, tx("§4Kusatma", "§4Siege"), tx("§7ufukta " + wraiths.size() + " hayalet", "§7" + wraiths.size() + " wraiths on the horizon"), 80);
            playTo(p, "duskwalker.drone", 0.9, 0.6);
        }
        return true;
    }

    private static int indexOfKey(List<Map<String, Object>> list, String key) {
        for (int i = 0; i < list.size(); i++) if (sKey(list.get(i)).equals(key)) return i;
        return -1;
    }

    private static void endSiege(String key, Siege st) {
        sieges.remove(key);
        for (Wraith w : st.wraiths) if (isValid(w.e)) despawn(w.e, true);
        List<Map<String, Object>> list = all();
        int i = indexOfKey(list, key);
        if (i < 0) return;
        Map<String, Object> s = list.get(i);
        String sd = Json.str(s.get("d"), "");
        List<Player> here = playersNear(sd, s, radiusOf(s) + 40, false);
        if (nx(s, "integrity") <= 0) {
            list.remove(i);
            save(list);
            for (Player p : here) {
                title(p, tx("§4Siginak Dustu", "§4The Sanctuary Fell"), tx("§7miknatisi yeniden yerlestir", "§7place the lodestone again"), 80);
                loseSanity(p, 10);
            }
            return;
        }
        int oldLevel = levelOf(s);
        s.put("sieges", nx(s, "sieges") + 1);
        save(list);
        for (Player p : here) {
            gainSanity(p, 15);
            addStat(p, "siegesRepelled");
            giveItem(p, "minecraft:golden_apple", 1);
            title(p, tx("§6Kusatma Savusturuldu", "§6Siege Repelled"),
                tx("§7" + st.repelled + "/" + st.total + " hayalet geri puskurtuldu", "§7" + st.repelled + "/" + st.total + " wraiths driven back"), 80);
            playTo(p, "random.levelup", 0.9, 0.7);
        }
        if (levelOf(s) > oldLevel) {
            for (Player p : here) say(p, tx("§6[Siginak] Seviye " + levelOf(s) + "! Yaricap " + radiusOf(s) + " blok.", "§6[Sanctuary] Level " + levelOf(s) + "! Radius " + radiusOf(s) + " blocks."));
        }
    }

    private static int lastT = -1;

    // =========================================================
    //  Kayit
    // =========================================================
    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        initialized = true;

        // ---- Kurma / yikma ----
        Events.After.playerPlaceBlock.subscribe(ev -> {
            try {
                Player p = ev.player;
                if (!isEnabled("sanctuary") || !"minecraft:lodestone".equals(ev.block.typeId())) return;
                if (!p.dimension().id().equals("minecraft:overworld")) {
                    say(p, tx("§7Siginak sadece Overworld'de kurulur.§r", "§7Sanctuaries can only be founded in the Overworld.§r"));
                    return;
                }
                List<Map<String, Object>> list = all();
                int mine = 0;
                for (Map<String, Object> s : list) if (p.name().equals(Json.str(s.get("owner"), null))) mine++;
                Block l = ev.block;
                if (mine >= 2) { say(p, tx("§cEn fazla 2 siginagin olabilir.§r", "§cYou may hold at most 2 sanctuaries.§r")); return; }
                Vec3 lv = new Vec3(l.x(), l.y(), l.z());
                for (Map<String, Object> s : list) {
                    if (hd(s, lv) < 40) {
                        say(p, tx("§cBaska bir siginaga cok yakin (en az 40 blok).§r", "§cToo close to another sanctuary (40 blocks minimum).§r"));
                        return;
                    }
                }
                Map<String, Object> rec = Json.newMap();
                rec.put("x", (double) l.x()); rec.put("y", (double) l.y()); rec.put("z", (double) l.z());
                rec.put("d", p.dimension().id()); rec.put("owner", p.name());
                rec.put("sieges", 0.0); rec.put("integrity", 100.0); rec.put("born", (double) getDay());
                list.add(rec);
                save(list);
                title(p, tx("§6Siginak Kuruldu", "§6Sanctuary Founded"), tx("§7isigini koru; kusatma gelecek", "§7keep your light; a siege will come"), 80);
                say(p, tx("§6[Siginak]§r Yaricap 12 blok. Cevresine mesale hatti kur, geceleri hayaletler gelecek. §8(!dwsanctuary)",
                    "§6[Sanctuary]§r Radius 12 blocks. Build a ring of torches; wraiths will come at night. §8(!dwsanctuary)"));
                playTo(p, "random.levelup", 0.9, 0.6);
                addStat(p, "sanctuariesFounded");
            } catch (Throwable e) { /* yut */ }
        });

        Events.After.playerBreakBlock.subscribe(ev -> {
            try {
                if (ev.brokenBlockPermutation != null && ev.brokenBlockPermutation.typeId() != null
                    && !ev.brokenBlockPermutation.typeId().equals("minecraft:lodestone")) return;
                Block l = ev.block;
                List<Map<String, Object>> list = all();
                int idx = -1;
                for (int i = 0; i < list.size(); i++) {
                    Map<String, Object> s = list.get(i);
                    if (Json.str(s.get("d"), "").equals(ev.player.dimension().id()) && nx(s, "x") == l.x() && nx(s, "y") == l.y() && nx(s, "z") == l.z()) { idx = i; break; }
                }
                if (idx < 0) return;
                list.remove(idx);
                save(list);
                say(ev.player, tx("§8(siginak dagildi)§r", "§8(the sanctuary dissolved)§r"));
            } catch (Throwable e) { /* yut */ }
        });

        // ---- Siginak icindeki etkiler (her 2 saniye) ----
        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("sanctuary")) { safeZone.clear(); return; }
            long tick = Sys.currentTick();
            List<Map<String, Object>> list = all();
            for (Player p : World.getPlayers()) {
                try {
                    if (!isSurvival(p) || realm.contains(p.id())) { safeZone.remove(p.id()); continue; }
                    Map<String, Object> s = sanctuaryAt(p.location(), p.dimension().id());
                    if (s == null) { if (inside.remove(p.id())) safeZone.remove(p.id()); continue; }
                    safeZone.add(p.id());
                    if (!inside.contains(p.id())) {
                        inside.add(p.id());
                        actionbar(p, tx("§6Siginaktasin (seviye ", "§6You are within the sanctuary (level ") + levelOf(s) + "§6)§r");
                    }
                    Object lastO = safe(() -> p.getDynamicProperty(KEY.LAST_SANITY_EVENT), (Object) null);
                    double last = lastO instanceof Number n ? n.doubleValue() : 0;
                    if (tick - last > 15 * TICKS_PER_SEC && getSanity(p) < 100) gainSanity(p, 1);
                } catch (Throwable e) { /* yut */ }
            }
            // gorsel halka: yakindaki oyuncu icin cekirdegin yaricapini gosterir
            if (tick % 100 < 40) {
                for (Map<String, Object> s : list) {
                    String sd = Json.str(s.get("d"), "");
                    Dimension dim = dimById(sd);
                    if (dim == null) continue;
                    boolean near = false;
                    for (Player p : World.getPlayers()) {
                        if (p.dimension().id().equals(sd) && hd(s, p.location()) < radiusOf(s) + 10) { near = true; break; }
                    }
                    if (!near) continue;
                    int r = radiusOf(s);
                    for (int i = 0; i < 14; i++) {
                        double a = (Math.PI * 2 * i) / 14 + tick * 0.01;
                        particle(dim, "minecraft:villager_happy", new Vec3(nx(s, "x") + 0.5 + Math.cos(a) * r, nx(s, "y") + 1, nx(s, "z") + 0.5 + Math.sin(a) * r));
                    }
                    particle(dim, "minecraft:soul_particle", new Vec3(nx(s, "x") + 0.5, nx(s, "y") + 1.4, nx(s, "z") + 0.5));
                }
            }
        }, 40);

        Events.After.playerLeave.subscribe(ev -> inside.remove(ev.playerId));

        // ---- Kusatma dongusu (her 5 tik) ----
        Sys.runInterval(() -> {
            if (isDefeated() || sieges.isEmpty()) return;
            long tick = Sys.currentTick();
            for (Map.Entry<String, Siege> en : new ArrayList<>(sieges.entrySet())) {
                String key = en.getKey();
                Siege st = en.getValue();
                try {
                    Map<String, Object> s = st.s;
                    Dimension dim = st.dim;
                    String sd = Json.str(s.get("d"), "");
                    Vec3 core = new Vec3(nx(s, "x") + 0.5, nx(s, "y"), nx(s, "z") + 0.5);
                    List<Player> watchers = playersNear(sd, s, 60, false);
                    int alive = 0;
                    for (Wraith w : st.wraiths) {
                        if (!w.state.equals("walking")) continue;
                        if (!isValid(w.e)) { w.state = "gone"; st.repelled += 1; continue; }
                        alive++;
                        // bakilirsa kipirdamaz
                        Vec3 head = new Vec3(w.x, w.y + 1.7, w.z);
                        boolean watched = false;
                        for (Player p : watchers) {
                            if (hdist(p.location(), new Vec3(w.x, w.y, w.z)) < 36 && viewDot(p, head) > 0.85) { watched = true; break; }
                        }
                        if (watched) continue;
                        double dx = core.x() - w.x, dz = core.z() - w.z;
                        double d = Math.hypot(dx, dz);
                        if (d == 0) d = 1;
                        w.x += dx / d * 0.9; w.z += dz / d * 0.9;
                        Integer gy = groundY(dim, w.x, w.y, w.z);
                        if (gy != null) w.y = gy;
                        final Wraith fw = w;
                        safe(() -> fw.e.teleport(new Vec3(fw.x, fw.y, fw.z), Opts.Teleport.dim(dim).facing(new Vec3(core.x(), core.y() + 1, core.z()))));
                        // isik hatti
                        if (tick % 10 < 5 && lightNear(dim, new Vec3(w.x, w.y, w.z))) {
                            w.state = "repelled"; st.repelled += 1;
                            smoke(dim, new Vec3(w.x, w.y + 1, w.z), 8);
                            playAt(dim, "random.fizz", new Vec3(w.x, w.y, w.z), 0.9, 0.5);
                            despawn(w.e, false);
                            continue;
                        }
                        // cekirdege ulasti
                        if (hdist(new Vec3(w.x, 0, w.z), core) < 3) {
                            w.state = "breached"; st.breached += 1;
                            s.put("integrity", Math.max(0, nx(s, "integrity") - 12));
                            List<Map<String, Object>> list = all();
                            int i = indexOfKey(list, key);
                            if (i >= 0) { list.get(i).put("integrity", s.get("integrity")); save(list); }
                            smoke(dim, new Vec3(core.x(), core.y() + 1, core.z()), 12);
                            playAt(dim, "duskwalker.creak", core, 1.2, 0.5);
                            String integ = fmt(nx(s, "integrity"));
                            for (Player p : watchers) {
                                shake(p, 0.16, 0.8);
                                actionbar(p, tx("§4Siginak hasar aldi! §7butunluk " + integ, "§4The sanctuary was hit! §7integrity " + integ));
                            }
                            despawn(w.e, true);
                        }
                    }
                    if (tick % 40 < 5 && alive > 0) {
                        String integ = fmt(nx(s, "integrity"));
                        for (Player p : watchers) {
                            actionbar(p, tx("§6Kusatma: §f" + alive + " §7hayalet kaldi | butunluk §f" + integ, "§6Siege: §f" + alive + " §7wraiths left | integrity §f" + integ));
                        }
                    }
                    if (alive == 0 || nx(s, "integrity") <= 0 || tick - st.start > 20 * 170) endSiege(key, st);
                } catch (Throwable e) { sieges.remove(key); }
            }
        }, 5);

        // ---- Aksam: kusatma zari + sabah onarim ----
        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("sanctuary")) { lastT = -1; return; }
            int t = timeOfDay();
            if (lastT >= 0) {
                if (lastT < 12500 && t >= 12500) {
                    for (Map<String, Object> s : all()) {
                        double chanceOfSiege = Math.min(0.8, 0.25 + getDay() * 0.02 + levelOf(s) * 0.05);
                        if (chance(chanceOfSiege)) Sys.runTimeout(() -> safe(() -> startSiege(s, false)), 60 * TICKS_PER_SEC);
                    }
                }
                if (lastT > 18000 && t < 4000) {
                    List<Map<String, Object>> list = all();
                    for (Map<String, Object> s : list) s.put("integrity", Math.min(100, nx(s, "integrity") + 10));
                    save(list);
                }
            }
            lastT = t;
        }, 40);

        // ---- Komut ----
        Events.Before.chatSend.subscribe(ev -> {
            String m = ev.message;
            if (!"!dwsanctuary".equals(m) && !"!dwsanctuary siege".equals(m)) return;
            ev.cancel = true;
            Player p = ev.sender;
            Sys.run(() -> {
                try {
                    List<Map<String, Object>> list = new ArrayList<>();
                    for (Map<String, Object> s : all()) if (Json.str(s.get("d"), "").equals(p.dimension().id())) list.add(s);
                    Vec3 pl = p.location();
                    list.sort((a, b) -> Double.compare(hd(a, pl), hd(b, pl)));
                    if (list.isEmpty()) {
                        say(p, tx("§7Yakinda siginak yok. Bir miknatis tasi (lodestone) yerlestirerek kur.§r", "§7No sanctuary nearby. Found one by placing a lodestone.§r"));
                        return;
                    }
                    Map<String, Object> s = list.get(0);
                    if (m.equals("!dwsanctuary siege")) {
                        say(p, startSiege(s, true) ? tx("§7Kusatma basladi.§r", "§7Siege started.§r") : tx("§7Kusatma baslayamadi.§r", "§7The siege couldn't start.§r"));
                        return;
                    }
                    Integer nextAt = null;
                    for (int n : new int[] { 2, 5, 9 }) if (n > nx(s, "sieges")) { nextAt = n; break; }
                    say(p, tx("§6Siginak§r  sahibi: ", "§6Sanctuary§r  owner: ") + Json.str(s.get("owner"), "") + tx("\n§7Seviye §f", "\n§7Level §f") + levelOf(s)
                        + tx(" §7yaricap §f", " §7radius §f") + radiusOf(s)
                        + tx("\n§7Butunluk §f", "\n§7Integrity §f") + fmt(nx(s, "integrity"))
                        + tx("§7/100  savusturulan kusatma §f", "§7/100  sieges repelled §f") + fmt(nx(s, "sieges"))
                        + (nextAt != null ? tx("§7 (sonraki seviye: ", "§7 (next level: ") + nextAt + ")" : "")
                        + tx("\n§7Uzaklik §f", "\n§7Distance §f") + Math.round(hd(s, pl)));
                } catch (Throwable e) { /* yut */ }
            });
        });
    }
}

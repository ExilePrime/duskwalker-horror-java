package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.*;

import static com.duskwalker.mod.Core.*;

/**
 * DUSKWALKER v12 - PARADOKS KORIDORU (5 ASAMALI SINAV) - paradox.js'in Java karsiligi.
 * Asamalar: 1) KAPI  2) GERI GERI  3) ISIK  4) DURGUNLUK  5) DONGU.
 * Tetikleme: nadir korku olayi (paradox) ya da !paradox. Acil cikis: !paradox exit. Asama atla (test): !paradox skip
 */
public final class Paradox {
    private Paradox() {}

    private static final String STATE_KEY = "duskwalker:v11paradox";
    private static final int YF = 290;            // zemin yuksekligi
    private static final int SEG = 8;             // segment uzunlugu (x ekseninde)
    private static final int KEEP = 8;            // oyuncunun iki yaninda tutulan segment sayisi
    private static final int BUILD_AHEAD = 6;     // her yonde onceden uretilen segment sayisi
    private static final String FOG = "duskwalker:paradox_fog";

    // ---------------------------------------------------------
    //  Asama ve tema tablolari
    // ---------------------------------------------------------
    private record Theme(String plank, String pillar, String lantern) {}

    private static final Theme[] THEMES = {
        new Theme("spruce_planks", "stripped_spruce_log", "soul_lantern"),
        new Theme("dark_oak_planks", "stripped_dark_oak_log", "lantern"),
        new Theme("polished_blackstone_bricks", "chiseled_polished_blackstone", ""),
        new Theme("crimson_planks", "stripped_crimson_stem", "soul_lantern"),
        new Theme("birch_planks", "stripped_birch_log", "lantern")
    };

    private static final class Stage {
        final String id; final String[] name; final String[] sign; final String chestItem; final int chestCount; final int away;
        Stage(String id, String[] name, String[] sign, String chestItem, int chestCount, int away) {
            this.id = id; this.name = name; this.sign = sign; this.chestItem = chestItem; this.chestCount = chestCount; this.away = away;
        }
        boolean hasChest() { return chestItem != null; }
    }

    private static final Stage[] STAGES = {
        new Stage("door", new String[] { "Kapi", "The Door" }, new String[] { "KORIDORUN\nSONU YOK\nBIR KAPI YAP", "THE CORRIDOR\nHAS NO END\nMAKE A DOOR" }, "spruce_planks", 6, 60),
        new Stage("back", new String[] { "Geri Geri", "Backwards" }, new String[] { "NEREYE GITTIGINE\nBAKMA\nGERI GERI YURU", "DON'T LOOK\nWHERE YOU GO\nWALK BACKWARDS" }, null, 0, 0),
        new Stage("light", new String[] { "Isik", "The Light" }, new String[] { "KARANLIK.\nISIK YAK.\n10 MESALE", "IT IS DARK.\nMAKE LIGHT.\n10 TORCHES" }, "torch", 16, 0),
        new Stage("still", new String[] { "Durgunluk", "Stillness" }, new String[] { "DUR.\nKIPIRDAMA.\n90 SANIYE", "STOP.\nBE STILL.\n90 SECONDS" }, null, 0, 0),
        new Stage("loop", new String[] { "Dongu", "The Loop" }, new String[] { "BIR KEZ DAHA.\nIKI KEZ.", "ONE MORE TIME.\nTWICE." }, "birch_planks", 6, 40)
    };

    /** Asama ici ilerleme durumu (JS freshProg()). */
    private static final class Prog {
        String phase = "need";
        int[] door;                 // {x,y,z} ya da null
        Integer wallX;              // duvarin x'i ya da null
        int side = 1;
        double back = 0;
        int torches = 0;
        final Map<String, Boolean> flags = new HashMap<>();
        int cycle = 0;
        boolean hinted = false;
        long lastBar = 0;
        long since = Sys.currentTick();
        Integer lastTorchX;
        Integer firstTorchX;
        int dirSign = 1;
    }

    private static final class State {
        String ownerId;
        Vec3 origin;
        int x0, z0, si;
        final Set<Integer> built = new HashSet<>();
        Prog prog = new Prog();
        Integer startX;
        boolean kitStocked;
        boolean ready;
    }

    private static final Map<String, State> states = new HashMap<>();   // oyuncu id -> durum
    private static final Map<String, Perm[]> doorPerms = new HashMap<>(); // oyuncu id -> { lower, upper }

    private static Dimension overworld() { return World.getDimension("overworld"); }
    private static boolean cmd(Dimension dim, String c) { return safe(() -> dim.runCommand(c), false); }
    private static int segOf(double x) { return (int) Math.floor(x / (double) SEG); }
    private static Stage stageDef(State st) { return STAGES[st.si]; }

    // ---------------------------------------------------------
    //  Koridor segmentleri
    // ---------------------------------------------------------
    private static void buildSegment(State st, Dimension dim, int i) {
        Theme th = THEMES[st.si];
        int x0 = i * SEG, x1 = x0 + SEG - 1, z0 = st.z0;
        cmd(dim, "fill " + x0 + " " + YF + " " + (z0 - 4) + " " + x1 + " " + YF + " " + (z0 + 4) + " " + th.plank());
        cmd(dim, "fill " + x0 + " " + (YF + 1) + " " + (z0 - 4) + " " + x1 + " " + (YF + 5) + " " + (z0 - 4) + " " + th.plank());
        cmd(dim, "fill " + x0 + " " + (YF + 1) + " " + (z0 + 4) + " " + x1 + " " + (YF + 5) + " " + (z0 + 4) + " " + th.plank());
        cmd(dim, "fill " + x0 + " " + (YF + 6) + " " + (z0 - 4) + " " + x1 + " " + (YF + 6) + " " + (z0 + 4) + " " + th.plank());
        cmd(dim, "fill " + x0 + " " + (YF + 1) + " " + (z0 - 3) + " " + x1 + " " + (YF + 5) + " " + (z0 + 3) + " air");
        for (int px : new int[] { x0, x0 + 4 }) {
            cmd(dim, "fill " + px + " " + (YF + 1) + " " + (z0 - 4) + " " + px + " " + (YF + 5) + " " + (z0 - 4) + " " + th.pillar());
            cmd(dim, "fill " + px + " " + (YF + 1) + " " + (z0 + 4) + " " + px + " " + (YF + 5) + " " + (z0 + 4) + " " + th.pillar());
        }
        if (!th.lantern().isEmpty()) {
            cmd(dim, "setblock " + (x0 + 2) + " " + (YF + 1) + " " + ((i % 2 == 0) ? z0 + 3 : z0 - 3) + " " + th.lantern());
        }
        st.built.add(i);
        if (st.prog != null && st.prog.wallX != null && st.prog.wallX >= x0 && st.prog.wallX <= x1) applyWall(st, dim);
        if (st.startX != null && st.startX >= x0 && st.startX <= x1) buildStartKit(st, dim);
    }

    private static void dropSegment(State st, Dimension dim, int i) {
        int x0 = i * SEG, x1 = x0 + SEG - 1;
        cmd(dim, "fill " + x0 + " " + YF + " " + (st.z0 - 4) + " " + x1 + " " + (YF + 6) + " " + (st.z0 + 4) + " air");
        st.built.remove(i);
    }

    private static void dropAll(State st, Dimension dim) {
        for (int i : new ArrayList<>(st.built)) dropSegment(st, dim, i);
    }

    private static void ensureSegments(State st, Dimension dim, Player player) {
        int s = segOf(player.location().x());
        int built = 0;
        for (int d = 0; d <= BUILD_AHEAD && built < 3; d++) {
            int[] idxs = d == 0 ? new int[] { s } : new int[] { s - d, s + d };
            for (int i : idxs) {
                if (!st.built.contains(i)) { buildSegment(st, dim, i); built++; }
            }
        }
        for (int i : new ArrayList<>(st.built)) if (Math.abs(i - s) > KEEP) dropSegment(st, dim, i);
    }

    // ---------------------------------------------------------
    //  Asama baslangic donanimi: crafting table, sandik, tabela
    // ---------------------------------------------------------
    private static void buildStartKit(State st, Dimension dim) {
        Stage def = stageDef(st);
        int x = segOf(st.startX) * SEG + 1, z0 = st.z0;
        cmd(dim, "setblock " + (x + 2) + " " + (YF + 1) + " " + (z0 + 2) + " crafting_table");
        if (def.hasChest()) {
            cmd(dim, "setblock " + (x + 2) + " " + (YF + 1) + " " + (z0 - 2) + " chest");
            if (!st.kitStocked) {
                cmd(dim, "replaceitem block " + (x + 2) + " " + (YF + 1) + " " + (z0 - 2) + " slot.container 0 " + def.chestItem + " " + def.chestCount);
                st.kitStocked = true;
            }
        }
        try {
            Block b = dim.getBlock(new Vec3(x + 3, YF + 1, z0));
            b.setType("minecraft:standing_sign");
            b.setSignText(tx(def.sign));
        } catch (Throwable e) { /* yut */ }
    }

    // ---------------------------------------------------------
    //  Duvar + kapi
    // ---------------------------------------------------------
    private static void applyWall(State st, Dimension dim) {
        if (st.prog == null || st.prog.wallX == null) return;
        int wx = st.prog.wallX;
        cmd(dim, "fill " + wx + " " + (YF + 1) + " " + (st.z0 - 3) + " " + wx + " " + (YF + 5) + " " + (st.z0 + 3) + " bedrock");
        cmd(dim, "fill " + wx + " " + (YF + 1) + " " + st.z0 + " " + wx + " " + (YF + 2) + " " + st.z0 + " air");
        Perm[] perms = doorPerms.get(st.ownerId);
        if (perms != null) {
            safe(() -> dim.getBlock(new Vec3(wx, YF + 1, st.z0)).setPermutation(perms[0]));
            safe(() -> dim.getBlock(new Vec3(wx, YF + 2, st.z0)).setPermutation(perms[1]));
        } else {
            cmd(dim, "setblock " + wx + " " + (YF + 1) + " " + st.z0 + " spruce_door");
            cmd(dim, "setblock " + wx + " " + (YF + 2) + " " + st.z0 + " spruce_door [\"upper_block_bit\"=true]");
        }
    }

    private static void removeWall(State st, Dimension dim) {
        if (st.prog.wallX == null) return;
        int wx = st.prog.wallX;
        cmd(dim, "fill " + wx + " " + (YF + 1) + " " + (st.z0 - 3) + " " + wx + " " + (YF + 5) + " " + (st.z0 + 3) + " air");
        st.prog.wallX = null;
    }

    private static void removeOriginalDoor(State st, Dimension dim) {
        int[] d = st.prog.door;
        if (d == null) return;
        cmd(dim, "fill " + d[0] + " " + d[1] + " " + d[2] + " " + d[0] + " " + (d[1] + 1) + " " + d[2] + " air");
        st.prog.door = null;
    }

    /** kapili duvari oyuncunun 'side' yonunde 'ahead' blok ileriye kurar */
    private static void placeWallAhead(State st, Dimension dim, Player p, int side, int ahead) {
        st.prog.side = side;
        st.prog.wallX = (int) Math.floor(p.location().x() + side * ahead);
        int i = segOf(st.prog.wallX);
        if (!st.built.contains(i)) buildSegment(st, dim, i); else applyWall(st, dim);
        st.prog.phase = "ahead";
        playTo(p, "duskwalker.creak", 0.8, 0.6);
    }

    // ---------------------------------------------------------
    //  Asama baslatma / ilerletme
    // ---------------------------------------------------------
    private static void beginStage(State st, Player p, Dimension dim) {
        st.ready = false;
        Vec3 pl = p.location();
        double posX = pl.x();
        dropAll(st, dim);
        st.prog = new Prog();
        st.startX = (int) Math.floor(posX);
        st.kitStocked = false;
        for (int i = segOf(posX) - 2; i <= segOf(posX) + 2; i++) buildSegment(st, dim, i);
        safe(() -> p.teleport(new Vec3(posX, YF + 1, st.z0 + 0.5), Opts.Teleport.dim(dim)));
        st.ready = true;
        Stage def = stageDef(st);
        title(p, tx("§8Asama " + (st.si + 1) + "/5", "§8Stage " + (st.si + 1) + "/5"), "§7" + tx(def.name), 70);
        say(p, tx("§8[Paradoks] §7Asama " + (st.si + 1) + ": §f", "§8[Paradox] §7Stage " + (st.si + 1) + ": §f") + tx(def.name) + "\n§8" + tx(def.sign).replace("\n", " "));
        playTo(p, "duskwalker.drone", 0.6, 0.8 + st.si * 0.08);
        safe(() -> {
            Map<String, Object> m = Json.newMap();
            m.put("ox", st.origin.x()); m.put("oy", st.origin.y()); m.put("oz", st.origin.z());
            m.put("z0", (double) st.z0); m.put("x0", (double) st.x0); m.put("si", (double) st.si);
            playerSet(p, STATE_KEY, m);
        });
    }

    private static void advanceStage(Player p, State st) {
        Dimension dim = overworld();
        addStat(p, "paradoxStages");
        if (st.si >= STAGES.length - 1) { leaveRealm(p, true); return; }
        st.ready = false;
        fade(p, 0, 0, 0, 0.3, 0.9, 0.8);
        playTo(p, "mob.endermen.portal", 0.9, 0.5);
        Sys.runTimeout(() -> {
            if (!isValid(p) || !states.containsKey(p.id())) return;
            st.si += 1;
            beginStage(st, p, dim);
        }, 10);
    }

    private static void onCrossed(Player p, State st) {
        Stage def = stageDef(st);
        if (def.id.equals("loop") && st.prog.cycle == 0) {
            // ilk gecis: ayni koridora donus (paradoks bir kez daha)
            Dimension dim = overworld();
            st.ready = false;
            fade(p, 0, 0, 0, 0.3, 0.9, 0.8);
            Sys.runTimeout(() -> {
                if (!isValid(p) || !states.containsKey(p.id())) return;
                beginStage(st, p, dim);
                st.prog.cycle = 1;
                say(p, tx("§8(ayni kapi... koridor kendini tekrar etti. bir kez daha.)§r", "§8(the same door... the corridor repeated itself. once more.)§r"));
            }, 10);
            return;
        }
        advanceStage(p, st);
    }

    // ---------------------------------------------------------
    //  Giris / cikis
    // ---------------------------------------------------------
    private static boolean enterRealm(Player player) {
        if (states.containsKey(player.id()) || realm.contains(player.id())) return false;
        Dimension dim = overworld();
        if (!player.dimension().id().equals("minecraft:overworld")) return false;
        Vec3 l = player.location();
        State st = new State();
        st.ownerId = player.id();
        st.origin = new Vec3(l.x(), l.y(), l.z());
        st.x0 = (int) Math.floor(l.x()); st.z0 = (int) Math.floor(l.z()); st.si = 0;
        st.startX = (int) Math.floor(l.x()); st.kitStocked = false; st.ready = false;
        states.put(player.id(), st);
        realm.add(player.id());
        Map<String, Object> m = Json.newMap();
        m.put("ox", l.x()); m.put("oy", l.y()); m.put("oz", l.z()); m.put("z0", (double) st.z0); m.put("x0", (double) st.x0); m.put("si", 0.0);
        playerSet(player, STATE_KEY, m);

        fade(player, 0, 0, 0, 0.25, 1.6, 0.9);
        if (!getPrefs(player).noflash) effect(player, "blindness", 40, 0);
        playTo(player, "mob.endermen.portal", 0.9, 0.4);
        Sys.runTimeout(() -> {
            if (!isValid(player)) return;
            safe(() -> player.fogPush(FOG, "duskwalker_paradox"));
            addStat(player, "paradoxEntered");
            beginStage(st, player, dim);
        }, 12);
        return true;
    }

    private static void leaveRealm(Player player, boolean solved) {
        State st = states.get(player.id());
        Map<String, Object> saved = Json.asMap(playerGet(player, STATE_KEY, null));
        Dimension dim = overworld();
        int px = (int) Math.floor(player.location().x());
        int z0 = st != null ? st.z0 : (saved != null ? (int) Json.num(saved.get("z0"), 0) : (int) Math.floor(player.location().z()));
        cmd(dim, "fill " + (px - 64) + " " + YF + " " + (z0 - 4) + " " + (px + 64) + " " + (YF + 6) + " " + (z0 + 4) + " air");
        if (st != null) dropAll(st, dim);
        states.remove(player.id());
        realm.remove(player.id());
        doorPerms.remove(player.id());
        safe(() -> player.setDynamicProperty(STATE_KEY, null));
        safe(() -> player.fogPop("duskwalker_paradox"));

        Vec3 origin = st != null ? st.origin : (saved != null ? new Vec3(Json.num(saved.get("ox"), 0), Json.num(saved.get("oy"), 0), Json.num(saved.get("oz"), 0)) : null);
        double c = solved ? 1 : 0;
        fade(player, c, c, c, 0.2, 0.5, 1.0);
        Sys.runTimeout(() -> {
            if (!isValid(player)) return;
            if (origin != null) safe(() -> player.teleport(origin, Opts.Teleport.dim(dim)));
            if (solved) {
                gainSanity(player, 40);
                giveItem(player, "minecraft:golden_apple", 2);
                giveItem(player, "minecraft:ender_pearl", 2);
                addStat(player, "paradoxSolved");
                logChronicle(player, "paradox", "");
                title(player, tx("§6Paradoks Cozuldu", "§6Paradox Solved"), tx("§7bes kapi, tek gercek", "§7five doors, one real"), 90);
                playTo(player, "random.levelup", 0.9, 0.7);
            } else {
                actionbar(player, tx("§7Geri dondun.§r", "§7You are back.§r"));
            }
        }, 8);
    }

    // ---------------------------------------------------------
    //  Asama mantiklari (her 5 tikte)
    // ---------------------------------------------------------
    private static String bar(double cur, double max, int width) {
        int n = (int) Math.max(0, Math.min(width, Math.round(cur / max * width)));
        return "§e" + "|".repeat(n) + "§8" + "|".repeat(width - n);
    }

    private static void doorStage(State st, Player p, Dimension dim) {
        Stage def = stageDef(st);
        Prog pr = st.prog;
        Vec3 l = p.location();
        if (pr.phase.equals("need")) {
            if (!pr.hinted && Sys.currentTick() - pr.since > 8 * TICKS_PER_MIN) {
                pr.hinted = true;
                say(p, tx("§4Duskwalker§r: Bir kapi. Yerlestir, sonra arkani don.", "§4Duskwalker§r: A door. Place it, then turn your back."));
            }
            return;
        }
        if (pr.phase.equals("away")) {
            if (pr.door == null) return;
            double dx = l.x() - pr.door[0];
            double dist = Math.abs(dx);
            // uzaklasirken koridor degisir: ipuclari
            Object[][] marks = { { 18, "lights" }, { 32, "whisper" }, { 46, "steps" } };
            for (Object[] mk : marks) {
                int d = (Integer) mk[0];
                String key = (String) mk[1];
                if (dist >= d && !Boolean.TRUE.equals(pr.flags.get(key))) {
                    pr.flags.put(key, true);
                    if (key.equals("lights")) {
                        actionbar(p, tx("§8(fenerler titriyor...)§r", "§8(the lanterns flicker...)§r"));
                        effect(p, "darkness", 80, 0);
                    }
                    if (key.equals("whisper")) {
                        playTo(p, "duskwalker.whisper", 0.9, 0.8);
                        say(p, tx("§8(birisi arkanda adini fisildadi)§r", "§8(someone whispered your name behind you)§r"));
                    }
                    if (key.equals("steps")) {
                        playTo(p, "step.wood", 0.9, 0.6);
                        playTo(p, "duskwalker.breath", 0.7, 0.8);
                        actionbar(p, tx("§8(arkandan adimlar... bakma)§r", "§8(footsteps behind you... don't look)§r"));
                    }
                }
            }
            if (Sys.currentTick() % 20 < 5) actionbar(p, "§8" + Math.round(dist) + "§7/§8" + def.away + " " + bar(dist, def.away, 20));
            if (dist >= def.away) {
                removeOriginalDoor(st, dim);
                placeWallAhead(st, dim, p, dx > 0 ? 1 : -1, 28);
                actionbar(p, tx("§8(kapi... onunde.)§r", "§8(the door... ahead of you.)§r"));
            }
        }
    }

    private static void backStage(State st, Player p, Dimension dim) {
        Prog pr = st.prog;
        if (!pr.phase.equals("need")) return;
        Vec3 v = safe(() -> p.getVelocity(), (Vec3) null);
        Vec3 vd = safe(() -> p.getViewDirection(), (Vec3) null);
        if (v == null || vd == null) return;
        double speed = Math.hypot(v.x(), v.z()) * 20;        // blok/sn
        if (speed < 0.8) return;
        double vl = Math.hypot(v.x(), v.z());
        if (vl == 0) vl = 1;
        double dl = Math.hypot(vd.x(), vd.z());
        if (dl == 0) dl = 1;
        double dot = (v.x() * vd.x() + v.z() * vd.z()) / (vl * dl);
        if (dot < -0.55) {
            pr.back += speed * 0.25;
            pr.dirSign = v.x() >= 0 ? 1 : -1;
            if (Sys.currentTick() - pr.lastBar > 10) {
                pr.lastBar = Sys.currentTick();
                actionbar(p, "§8<< §7" + Math.round(pr.back) + "§8/50 " + bar(pr.back, 50, 20));
            }
            if (pr.back >= 50) {
                placeWallAhead(st, dim, p, pr.dirSign, 16);
                actionbar(p, tx("§8(arkanda bir sey acildi...)§r", "§8(something opened behind you...)§r"));
            }
        } else if (dot > 0.55 && Sys.currentTick() - pr.lastBar > 40) {
            pr.lastBar = Sys.currentTick();
            actionbar(p, tx("§8(ileri bakma. geri yuru.)§r", "§8(don't face forward. walk backwards.)§r"));
        }
    }

    private static void lightStage(State st, Player p, Dimension dim) {
        Prog pr = st.prog;
        if (!pr.phase.equals("need")) return;
        if (Sys.currentTick() % 100 < 5) effect(p, "darkness", 120, 0);
        if (!pr.hinted && Sys.currentTick() - pr.since > 6 * TICKS_PER_MIN) {
            pr.hinted = true;
            say(p, tx("§4Duskwalker§r: Sandik. Mesale. On tane.", "§4Duskwalker§r: The chest. Torches. Ten of them."));
        }
    }

    private static void stillStage(State st, Player p, Dimension dim) {
        Prog pr = st.prog;
        if (!pr.phase.equals("need")) return;
        int s = stillSeconds(p);
        if (Sys.currentTick() - pr.lastBar > 20 && s >= 4) {
            pr.lastBar = Sys.currentTick();
            actionbar(p, "§8" + Math.min(s, 90) + "§7/§890 " + bar(s, 90, 20));
        }
        // zaman icinde cevrede seyler dolasir
        int[][] waves = { { 20, 1 }, { 45, 4 }, { 70, 3 } };
        for (int[] w : waves) {
            int t = w[0];
            final int skin = w[1];
            if (s >= t && !Boolean.TRUE.equals(pr.flags.get("e" + t))) {
                pr.flags.put("e" + t, true);
                int side = Math.random() < 0.5 ? 1 : -1;
                Entity e = spawnEffigy(dim, new Vec3(p.location().x() + side * 16, YF + 1, st.z0 + 0.5), skin);
                if (e != null) {
                    int[] n = { 0 };
                    int[] h = { -1 };
                    h[0] = Sys.runInterval(() -> {
                        try {
                            n[0]++;
                            if (!isValid(e) || !isValid(p) || n[0] > 60) { Sys.clearRun(h[0]); despawn(e, true); return; }
                            double ex = e.location().x() + (p.location().x() - e.location().x()) * 0.05;
                            safe(() -> e.teleport(new Vec3(ex, YF + 1, st.z0 + 0.5), Opts.Teleport.dim(dim).facing(eyePos(p))));
                            if (Math.abs(ex - p.location().x()) < 5) { Sys.clearRun(h[0]); despawn(e, true); }
                        } catch (Throwable er) { safe(() -> Sys.clearRun(h[0])); }
                    }, 10);
                }
                playTo(p, "duskwalker.breath", 0.8, 0.9 - skin * 0.05);
            }
        }
        if (s >= 90) {
            Vec3 vd = safe(() -> p.getViewDirection(), new Vec3(1, 0, 0));
            placeWallAhead(st, dim, p, vd.x() >= 0 ? 1 : -1, 12);
            playTo(p, "duskwalker.tick", 1.0, 0.7);
            actionbar(p, tx("§8(bir kapi... tam karsinda.)§r", "§8(a door... right in front of you.)§r"));
        }
    }

    private static void tickPlayer(Player p, State st) {
        if (!st.ready) return;
        Dimension dim = overworld();
        Vec3 l = p.location();

        if (l.y() < YF - 8 || l.y() > YF + 12) {
            safe(() -> p.teleport(new Vec3(l.x(), YF + 1, st.z0 + 0.5), Opts.Teleport.dim(dim)));
            return;
        }
        ensureSegments(st, dim, p);

        String id = stageDef(st).id;
        if (!st.prog.phase.equals("ahead")) {
            if (id.equals("door") || id.equals("loop")) doorStage(st, p, dim);
            else if (id.equals("back")) backStage(st, p, dim);
            else if (id.equals("light")) lightStage(st, p, dim);
            else if (id.equals("still")) stillStage(st, p, dim);
            return;
        }

        // duvar belirdi: gecis kontrolu
        Integer w = st.prog.wallX;
        if (w == null) return;
        boolean crossed = st.prog.side > 0 ? l.x() >= w + 1 : l.x() <= w;
        if (crossed) { onCrossed(p, st); return; }
        // duvardan cok uzaklasildiysa (diger yone yurudu) kapi yine onunde belirir
        double away = st.prog.side > 0 ? w - l.x() : l.x() - w;
        if (away < -44) {
            removeWall(st, dim);
            placeWallAhead(st, dim, p, -st.prog.side, 24);
            actionbar(p, tx("§8(kapi seni takip ediyor...)§r", "§8(the door follows you...)§r"));
        }
    }

    // =========================================================
    //  Kayit
    // =========================================================
    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        initialized = true;

        // ---- Oyuncu eylemleri: kapi ve mesale yerlestirme ----
        Events.After.playerPlaceBlock.subscribe(ev -> {
            try {
                Player p = ev.player;
                State st = states.get(p.id());
                if (st == null || !st.ready) return;
                String id = ev.block.typeId();
                Stage def = stageDef(st);

                if (def.id.equals("light") && id.contains("torch") && st.prog.phase.equals("need")) {
                    st.prog.torches += 1;
                    st.prog.lastTorchX = ev.block.x();
                    if (st.prog.firstTorchX == null) st.prog.firstTorchX = ev.block.x();
                    actionbar(p, tx("§8mesale ", "§8torch ") + "§e" + st.prog.torches + "§8/10");
                    playTo(p, "random.fizz", 0.4, 1.2);
                    if (st.prog.torches >= 10) {
                        int side = st.prog.lastTorchX >= st.prog.firstTorchX ? 1 : -1;
                        placeWallAhead(st, overworld(), p, side, 14);
                        actionbar(p, tx("§8(yolun sonunda... bir kapi.)§r", "§8(at the end of the way... a door.)§r"));
                    }
                    return;
                }

                if ((def.id.equals("door") || def.id.equals("loop")) && st.prog.phase.equals("need")) {
                    if (!id.contains("door") || id.contains("trapdoor") || id.contains("iron")) return;
                    Block placed = ev.block;
                    Dimension d = placed.dimension();
                    Block lower = placed;
                    Block upper = safe(() -> d.getBlock(new Vec3(placed.x(), placed.y() + 1, placed.z())), (Block) null);
                    boolean isUpper = safe(() -> Boolean.TRUE.equals(placed.permutation().getState("upper_block_bit")), false);
                    if (isUpper) {
                        upper = placed;
                        lower = safe(() -> d.getBlock(new Vec3(placed.x(), placed.y() - 1, placed.z())), (Block) null);
                    }
                    if (lower == null || upper == null) return;
                    doorPerms.put(p.id(), new Perm[] { lower.permutation(), upper.permutation() });
                    st.prog.door = new int[] { lower.x(), lower.y(), lower.z() };
                    st.prog.phase = "away";
                    actionbar(p, tx("§8(simdi ona arkani don ve uzaklas... cok uzaklas)§r", "§8(now turn your back to it and walk away... far away)§r"));
                    playTo(p, "mob.endermen.idle", 0.6, 0.5);
                }
            } catch (Throwable e) { /* yut */ }
        });

        // ---- Ana dongu (her 5 tik) ----
        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("paradox")) return;
            for (Player p : World.getPlayers()) {
                try {
                    State st = states.get(p.id());
                    if (st == null) {
                        Map<String, Object> saved = Json.asMap(playerGet(p, STATE_KEY, null));
                        if (saved != null && p.location().y() > YF - 6 && p.location().y() < YF + 10) {
                            st = new State();
                            st.ownerId = p.id();
                            st.origin = new Vec3(Json.num(saved.get("ox"), 0), Json.num(saved.get("oy"), 0), Json.num(saved.get("oz"), 0));
                            st.x0 = (int) Json.num(saved.get("x0"), 0);
                            st.z0 = (int) Json.num(saved.get("z0"), 0);
                            st.si = (int) Math.min(STAGES.length - 1, Json.num(saved.get("si"), 0));
                            st.startX = st.x0;
                            st.kitStocked = true;
                            st.ready = true;
                            states.put(p.id(), st);
                            realm.add(p.id());
                        } else continue;
                    }
                    if (!isSurvival(p) && !p.dimension().id().equals("minecraft:overworld")) continue;
                    tickPlayer(p, st);
                } catch (Throwable e) { /* yut */ }
            }
        }, 5);

        // ---- Olay + komutlar ----
        registerEvent(EventDef.of("paradox").name("Paradox Corridor").minDay(10).weight(1).cooldown(45L * TICKS_PER_MIN)
            .cond(p -> p.dimension().id().equals("minecraft:overworld") && !realm.contains(p.id()) && getSanity(p) <= 75)
            .run(player -> enterRealm(player)));

        Events.After.playerLeave.subscribe(ev -> {
            states.remove(ev.playerId);
            doorPerms.remove(ev.playerId);
        });

        Events.Before.chatSend.subscribe(ev -> {
            String msg = ev.message;
            if (!"!paradox".equals(msg) && !"!paradox exit".equals(msg) && !"!paradox skip".equals(msg)) return;
            ev.cancel = true;
            Player p = ev.sender;
            Sys.run(() -> {
                try {
                    if (msg.equals("!paradox exit")) {
                        if (realm.contains(p.id()) || states.containsKey(p.id())) leaveRealm(p, false);
                        else say(p, tx("§7Paradoksun icinde degilsin.§r", "§7You are not inside the paradox.§r"));
                        return;
                    }
                    if (msg.equals("!paradox skip")) {
                        State st = states.get(p.id());
                        if (st != null && st.ready) advanceStage(p, st);
                        else say(p, tx("§7Paradoksun icinde degilsin.§r", "§7You are not inside the paradox.§r"));
                        return;
                    }
                    if (!triggerEvent("paradox", p, true)) say(p, tx("§7Paradoks su an baslayamadi.§r", "§7The paradox couldn't start right now.§r"));
                } catch (Throwable e) { /* yut */ }
            });
        });
    }
}

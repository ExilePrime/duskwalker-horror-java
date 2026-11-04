package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.*;

import static com.duskwalker.mod.Core.*;

/**
 * DUSKWALKER v14 - #4 BOS DERINLIKLER (prosedurel zindan) - v10/depths.js portu.
 *
 * Gokyuzunda, her seferinde FARKLI uretilen 3 katli bir zindan. Her katta 3 oda + cikis salonu vardir.
 *   HEYKELLER / KARANLIK / SESSIZ / ZAMANLI / DINLENME
 *
 *  !dwdescend         : bilgi + onay istemi
 *  !dwdescend confirm : zindana in      (gun >= 5 gerekir)
 *  !dwdescend exit    : acil cikis
 */
public final class Depths {
    private Depths() {}

    private static final String STATE_KEY = "duskwalker:v14depths";
    private static final int YD = 200;               // 1. katin zemin yuksekligi
    private static final int FLOOR_STEP = 14;        // katlar arasi dikey aralik
    private static final int FLOORS = 3;
    private static final int ROOM_LEN = 17, PITCH = 18, HALL_LEN = 9;
    private static final String[] THEMES = { "deepslate_bricks", "polished_blackstone_bricks", "blackstone" };
    private static final String[] TYPES = { "statues", "dark", "silent", "timed", "rest" };

    /** Heykel kaydi (rs.eff elemani) */
    private static final class Eff {
        Entity e; double x, z, sx, sz;
        Eff(Entity e, double x, double z) { this.e = e; this.x = x; this.z = z; this.sx = x; this.sz = z; }
    }

    /** Oda durumu (st.roomState[i]) */
    private static final class RoomState {
        boolean solved, healed, hint;
        List<Eff> eff;           // null = henuz olusmadi
        int torches;
        long last;
        long deadline;
        long nextTick;
    }

    private static final class State {
        String ownerId;
        Vec3 origin;
        int x0, z0;
        int floor;
        boolean ready;
        Set<Integer> built = new HashSet<>();
        List<String> rooms = new ArrayList<>();
        List<RoomState> roomState = new ArrayList<>();
    }

    private static final Map<String, State> states = new HashMap<>();

    private static Dimension overworld() { return World.getDimension("overworld"); }
    private static boolean cmd(Dimension dim, String c) { return safe(() -> dim.runCommand(c), false); }
    private static int y0Of(State st) { return YD + st.floor * FLOOR_STEP; }
    private static int roomX(State st, int i) { return st.x0 + 10 + i * PITCH; }
    private static int hallX(State st) { return roomX(st, 3); }

    private static List<RoomState> freshRoomStates() {
        List<RoomState> l = new ArrayList<>();
        for (int i = 0; i < 3; i++) l.add(new RoomState());
        return l;
    }

    // ---------------------------------------------------------
    //  Uretim
    // ---------------------------------------------------------
    private static List<String> pickRooms(int floor) {
        List<String> rooms = new ArrayList<>();
        boolean restUsed = false;
        for (int i = 0; i < 3; i++) {
            String t;
            do { t = pick(TYPES); } while ((i > 0 && t.equals(rooms.get(i - 1))) || (t.equals("rest") && (restUsed || i == 0)));
            if (t.equals("rest")) restUsed = true;
            rooms.add(t);
        }
        return rooms;
    }

    private static void shell(State st, Dimension dim, int x0, int x1) {
        int y0 = y0Of(st), z0 = st.z0;
        String th = THEMES[st.floor % THEMES.length];
        cmd(dim, "fill " + x0 + " " + (y0 - 1) + " " + (z0 - 7) + " " + x1 + " " + (y0 + 7) + " " + (z0 + 7) + " bedrock");
        cmd(dim, "fill " + x0 + " " + y0 + " " + (z0 - 6) + " " + x1 + " " + (y0 + 6) + " " + (z0 + 6) + " " + th);
        cmd(dim, "fill " + x0 + " " + (y0 + 1) + " " + (z0 - 5) + " " + x1 + " " + (y0 + 5) + " " + (z0 + 5) + " air");
    }

    private static void lanterns(State st, Dimension dim, int x0, int x1, int step) {
        int y0 = y0Of(st), z0 = st.z0;
        for (int x = x0 + 2; x <= x1; x += step) {
            cmd(dim, "setblock " + x + " " + (y0 + 1) + " " + (z0 + 5) + " soul_lantern");
            cmd(dim, "setblock " + x + " " + (y0 + 1) + " " + (z0 - 5) + " soul_lantern");
        }
    }

    private static void buildHall(State st, Dimension dim) {
        int x0 = st.x0, x1 = st.x0 + 8;
        shell(st, dim, x0, x1);
        lanterns(st, dim, x0, x1, 4);
        int y0 = y0Of(st);
        cmd(dim, "fill " + (x1 + 1) + " " + y0 + " " + (st.z0 - 6) + " " + (x1 + 1) + " " + (y0 + 6) + " " + (st.z0 + 6) + " bedrock");
        cmd(dim, "fill " + (x1 + 1) + " " + (y0 + 1) + " " + (st.z0 - 1) + " " + (x1 + 1) + " " + (y0 + 3) + " " + (st.z0 + 1) + " air"); // giris bosluk
        try {
            Block b = dim.getBlock(new Vec3(x0 + 2, y0 + 1, st.z0));
            b.setType("minecraft:standing_sign");
            b.setSignText(tx("KAT " + (st.floor + 1) + "/" + FLOORS + "\nHER ODANIN\nBIR KURALI VAR", "FLOOR " + (st.floor + 1) + "/" + FLOORS + "\nEVERY ROOM\nHAS A RULE"));
        } catch (Throwable e) { /* yut */ }
    }

    private static String[] ruleNames(String type) {
        switch (type) {
            case "statues": return new String[] { "BAKMADIKCA\nYAKLASIRLAR", "THEY MOVE WHEN\nYOU DON'T LOOK" };
            case "dark": return new String[] { "KARANLIK\n6 MESALE KOY", "DARKNESS\nPLACE 6 TORCHES" };
            case "silent": return new String[] { "SESSIZ.\nKOSMA.", "SILENCE.\nDO NOT RUN." };
            case "timed": return new String[] { "ZAMANA\nKARSI KOS", "RACE\nTHE CLOCK" };
            default: return new String[] { "DINLEN.", "REST." };
        }
    }

    private static void ruleSign(State st, Dimension dim, int rx, String type) {
        try {
            Block b = dim.getBlock(new Vec3(rx + 1, y0Of(st) + 1, st.z0 + 4));
            b.setType("minecraft:standing_sign");
            b.setSignText(tx(ruleNames(type)));
        } catch (Throwable e) { /* yut */ }
    }

    private static void buildRoom(State st, Dimension dim, int i) {
        if (st.built.contains(i)) return;
        st.built.add(i);
        String type = st.rooms.get(i);
        int rx = roomX(st, i), y0 = y0Of(st), z0 = st.z0;
        shell(st, dim, rx, rx + ROOM_LEN);
        if (!type.equals("dark")) lanterns(st, dim, rx, rx + ROOM_LEN, type.equals("timed") ? 5 : 6);
        // kapi duvari (bitisik oda girisi) : parmakliklarla kapali gecit
        int gx = rx + ROOM_LEN;
        cmd(dim, "fill " + gx + " " + y0 + " " + (z0 - 6) + " " + gx + " " + (y0 + 6) + " " + (z0 + 6) + " bedrock");
        cmd(dim, "fill " + gx + " " + (y0 + 1) + " " + (z0 - 1) + " " + gx + " " + (y0 + 3) + " " + (z0 + 1) + " iron_bars");
        ruleSign(st, dim, rx, type);
        if (type.equals("dark")) {
            cmd(dim, "setblock " + (rx + 2) + " " + (y0 + 1) + " " + (z0 - 3) + " chest");
            cmd(dim, "replaceitem block " + (rx + 2) + " " + (y0 + 1) + " " + (z0 - 3) + " slot.container 0 torch 10");
        }
        if (type.equals("rest")) {
            cmd(dim, "setblock " + (rx + 8) + " " + (y0 + 1) + " " + z0 + " chest");
            cmd(dim, "replaceitem block " + (rx + 8) + " " + (y0 + 1) + " " + z0 + " slot.container 0 golden_carrot 4");
            cmd(dim, "replaceitem block " + (rx + 8) + " " + (y0 + 1) + " " + z0 + " slot.container 1 bread 4");
            cmd(dim, "replaceitem block " + (rx + 8) + " " + (y0 + 1) + " " + z0 + " slot.container 2 torch 8");
            cmd(dim, "setblock " + (rx + 6) + " " + (y0 + 1) + " " + (z0 + 3) + " campfire");
        }
        if (i == 2) {
            // cikis salonu: oda 3'un ardindan
            int hx0 = hallX(st);
            shell(st, dim, hx0, hx0 + HALL_LEN - 1);
            lanterns(st, dim, hx0, hx0 + HALL_LEN - 1, 4);
            if (st.floor == FLOORS - 1) {
                cmd(dim, "setblock " + (hx0 + 4) + " " + (y0 + 1) + " " + z0 + " chest");
                cmd(dim, "replaceitem block " + (hx0 + 4) + " " + (y0 + 1) + " " + z0 + " slot.container 0 golden_apple 3");
                cmd(dim, "replaceitem block " + (hx0 + 4) + " " + (y0 + 1) + " " + z0 + " slot.container 1 ender_pearl 3");
                cmd(dim, "replaceitem block " + (hx0 + 4) + " " + (y0 + 1) + " " + z0 + " slot.container 2 diamond 2");
                cmd(dim, "replaceitem block " + (hx0 + 4) + " " + (y0 + 1) + " " + z0 + " slot.container 3 experience_bottle 6");
            }
            cmd(dim, "fill " + (hx0 + HALL_LEN) + " " + y0 + " " + (z0 - 6) + " " + (hx0 + HALL_LEN) + " " + (y0 + 6) + " " + (z0 + 6) + " bedrock");
        }
    }

    // ---------------------------------------------------------
    //  Kat baslatma / giris / cikis
    // ---------------------------------------------------------
    private static void beginFloor(State st, Player p, Dimension dim) {
        st.ready = false;
        st.built = new HashSet<>();
        st.rooms = pickRooms(st.floor);
        st.roomState = freshRoomStates();
        buildHall(st, dim);
        buildRoom(st, dim, 0);
        int y0 = y0Of(st);
        safe(() -> p.teleport(new Vec3(st.x0 + 2.5, y0 + 1, st.z0 + 0.5), Opts.Teleport.dim(dim).rot(0, -90)));
        st.ready = true;
        title(p, tx("§8Kat " + (st.floor + 1) + "/" + FLOORS, "§8Floor " + (st.floor + 1) + "/" + FLOORS), tx("§7Bos Derinlikler", "§7The Hollow Depths"), 70);
        playTo(p, "duskwalker.drone", 0.7, 0.7 + st.floor * 0.1);
        safe(() -> {
            Map<String, Object> m = Json.newMap();
            m.put("ox", st.origin.x()); m.put("oy", st.origin.y()); m.put("oz", st.origin.z());
            m.put("x0", (double) st.x0); m.put("z0", (double) st.z0);
            playerSet(p, STATE_KEY, m);
        });
    }

    private static boolean enter(Player p) {
        if (states.containsKey(p.id()) || realm.contains(p.id()) || !p.dimension().id().equals("minecraft:overworld")) return false;
        Dimension dim = overworld();
        Vec3 l = p.location();
        State st = new State();
        st.ownerId = p.id();
        st.origin = new Vec3(l.x(), l.y(), l.z());
        st.x0 = (int) Math.floor(l.x());
        st.z0 = (int) Math.floor(l.z());
        st.floor = 0;
        st.ready = false;
        st.roomState = new ArrayList<>();
        states.put(p.id(), st);
        realm.add(p.id());
        addStat(p, "depthsEntered");
        fade(p, 0, 0, 0, 0.25, 1.2, 0.8);
        playTo(p, "mob.endermen.portal", 0.9, 0.4);
        Sys.runTimeout(() -> { if (isValid(p)) beginFloor(st, p, dim); }, 12);
        return true;
    }

    private static void leave(Player p, boolean solved) {
        State st = states.get(p.id());
        Map<String, Object> saved = Json.asMap(playerGet(p, STATE_KEY, null));
        Dimension dim = overworld();
        int x0 = st != null ? st.x0 : (saved != null ? Json.intv(saved.get("x0"), 0) : (int) Math.floor(p.location().x()));
        int z0 = st != null ? st.z0 : (saved != null ? Json.intv(saved.get("z0"), 0) : (int) Math.floor(p.location().z()));
        // yuklu bolgedeki yapilari temizle (tum katlar)
        for (int f = 0; f < FLOORS; f++) {
            int y0 = YD + f * FLOOR_STEP;
            cmd(dim, "fill " + (x0 - 2) + " " + (y0 - 1) + " " + (z0 - 7) + " " + (x0 + 90) + " " + (y0 + 7) + " " + (z0 + 7) + " air");
        }
        if (st != null) for (RoomState rs : st.roomState) if (rs != null && rs.eff != null) for (Eff e : rs.eff) despawn(e.e, false);
        states.remove(p.id());
        realm.remove(p.id());
        // playerSet(p, KEY, undefined) + setDynamicProperty(KEY, undefined) -> ozelligi temizle
        safe(() -> p.setDynamicProperty(STATE_KEY, null));
        Vec3 origin = st != null ? st.origin : (saved != null ? new Vec3(Json.num(saved.get("ox"), 0), Json.num(saved.get("oy"), 0), Json.num(saved.get("oz"), 0)) : null);
        double c = solved ? 1 : 0;
        fade(p, c, c, c, 0.2, 0.5, 1.0);
        Sys.runTimeout(() -> {
            if (!isValid(p)) return;
            if (origin != null) safe(() -> p.teleport(origin, Opts.Teleport.dim(dim)));
            if (solved) {
                gainSanity(p, 35);
                giveItem(p, "minecraft:golden_apple", 2);
                giveItem(p, "minecraft:diamond", 1);
                addStat(p, "depthsCleared");
                logChronicle(p, "depths", "");
                title(p, tx("§6Derinlikler Asildi", "§6Depths Conquered"), tx("§7uc kat, hepsi geride", "§7three floors, all behind you"), 90);
                playTo(p, "random.levelup", 0.9, 0.6);
            } else actionbar(p, tx("§7Yukari ciktin.§r", "§7You climbed back up.§r"));
        }, 8);
    }

    // ---------------------------------------------------------
    //  Oda cozme / mantik
    // ---------------------------------------------------------
    private static void openGate(State st, Dimension dim, int i, Player p) {
        if (st.roomState.get(i).solved) return;
        st.roomState.get(i).solved = true;
        int gx = roomX(st, i) + ROOM_LEN, y0 = y0Of(st), z0 = st.z0;
        cmd(dim, "fill " + gx + " " + (y0 + 1) + " " + (z0 - 1) + " " + gx + " " + (y0 + 3) + " " + (z0 + 1) + " air");
        playAt(dim, "random.anvil_land", new Vec3(gx, y0 + 2, z0), 0.9, 0.6);
        actionbar(p, tx("§a(kapi acildi)§r", "§a(the gate opened)§r"));
        addStat(p, "depthRoomsSolved");
        if (i + 1 < 3) buildRoom(st, dim, i + 1);
    }

    private static void resetToStart(State st, Dimension dim, Player p, int i, String msg, double loss) {
        int y0 = y0Of(st);
        safe(() -> p.teleport(new Vec3(roomX(st, i) + 0.5, y0 + 1, st.z0 + 0.5), Opts.Teleport.dim(dim).rot(0, -90)));
        actionbar(p, msg);
        loseSanity(p, loss);
        shake(p, 0.12, 0.6);
        playTo(p, "duskwalker.creak", 0.8, 0.6);
    }

    private static void roomLogic(State st, Player p, Dimension dim, int i) {
        String type = st.rooms.get(i);
        RoomState rs = st.roomState.get(i);
        int rx = roomX(st, i), y0 = y0Of(st), z0 = st.z0;
        Vec3 l = p.location();
        if (rs.solved) return;

        if (type.equals("rest")) {
            if (!rs.healed && l.x() >= rx + 3) { rs.healed = true; gainSanity(p, 15); actionbar(p, tx("§b(nefes al. burasi guvenli)§r", "§b(breathe. it is safe here)§r")); }
            if (l.x() >= rx + 12) openGate(st, dim, i, p);
            return;
        }

        if (type.equals("statues")) {
            if (rs.eff == null && l.x() >= rx + 2) {
                rs.eff = new ArrayList<>();
                int n = 4 + st.floor;
                for (int k = 0; k < n; k++) {
                    double x = rx + 6 + Math.random() * 9, z = z0 + rand(-4, 4);
                    Entity e = spawnEffigy(dim, new Vec3(x, y0 + 1, z), 1 + (k % 4));
                    if (e != null) rs.eff.add(new Eff(e, x, z));
                }
                actionbar(p, tx("§8(bakmadikca yaklasirlar)§r", "§8(they close in when you look away)§r"));
            }
            if (rs.eff != null) {
                for (Eff w : rs.eff) {
                    if (!isValid(w.e)) continue;
                    Vec3 head = new Vec3(w.x, y0 + 2.7, w.z);
                    if (viewDot(p, head) < 0.6) {
                        double dx = l.x() - w.x, dz = l.z() - w.z, d = Math.hypot(dx, dz);
                        if (d == 0) d = 1;
                        w.x += dx / d * 0.85; w.z += dz / d * 0.85;
                        final double wx = w.x, wz = w.z;
                        safe(() -> w.e.teleport(new Vec3(wx, y0 + 1, wz), Opts.Teleport.dim(dim).facing(eyePos(p))));
                        if (Math.hypot(l.x() - w.x, l.z() - w.z) < 1.9) {
                            for (Eff q : rs.eff) {
                                q.x = q.sx; q.z = q.sz;
                                safe(() -> q.e.teleport(new Vec3(q.sx, y0 + 1, q.sz), Opts.Teleport.dim(dim)));
                            }
                            resetToStart(st, dim, p, i, tx("§4Yakalandin. Basa donuyorsun.§r", "§4Caught. Back to the start.§r"), 8);
                            return;
                        }
                    }
                }
            }
            if (l.x() >= rx + ROOM_LEN - 1) {
                if (rs.eff != null) for (Eff w : rs.eff) despawn(w.e, true);
                openGate(st, dim, i, p);
            }
            return;
        }

        if (type.equals("dark")) {
            if (!rs.hint && l.x() >= rx + 1) { rs.hint = true; actionbar(p, tx("§8(sandiktaki mesaleler... 6 tanesini koy)§r", "§8(the torches in the chest... place 6 of them)§r")); }
            if (rs.torches >= 6) openGate(st, dim, i, p);
            return;
        }

        if (type.equals("silent")) {
            if (p.isSprinting() && l.x() >= rx + 1 && Sys.currentTick() - rs.last > 40) {
                rs.last = Sys.currentTick();
                resetToStart(st, dim, p, i, tx("§4Sesini duydu. Basa donuyorsun.§r", "§4It heard you. Back to the start.§r"), 5);
                return;
            }
            if (l.x() >= rx + ROOM_LEN - 1) openGate(st, dim, i, p);
            return;
        }

        if (type.equals("timed")) {
            if (rs.deadline == 0 && l.x() >= rx + 2) { rs.deadline = Sys.currentTick() + (long) Math.max(18, 50 - st.floor * 7) * TICKS_PER_SEC; rs.nextTick = 0; }
            if (rs.deadline != 0) {
                long left = rs.deadline - Sys.currentTick();
                if (Sys.currentTick() >= rs.nextTick) {
                    playTo(p, "duskwalker.tick", 0.8, 0.9 + (1 - left / (double) (50 * TICKS_PER_SEC)) * 0.8);
                    rs.nextTick = Sys.currentTick() + (left > 200 ? 20 : left > 100 ? 12 : 6);
                    actionbar(p, "§c" + (long) Math.max(0, Math.ceil(left / 20.0)) + "s");
                }
                if (left <= 0) { rs.deadline = 0; resetToStart(st, dim, p, i, tx("§4Sure doldu.§r", "§4Time ran out.§r"), 6); return; }
            }
            if (l.x() >= rx + ROOM_LEN - 1) openGate(st, dim, i, p);
        }
    }

    private static void tickPlayer(Player p, State st) {
        if (!st.ready) return;
        Dimension dim = overworld();
        Vec3 l = p.location();
        int y0 = y0Of(st);
        if (l.y() < y0 - 6 || l.y() > y0 + 12) { leave(p, false); return; }

        // hangi odadayiz?
        int ri = -1;
        for (int i = 0; i < 3; i++) if (l.x() >= roomX(st, i) && l.x() < roomX(st, i) + PITCH) { ri = i; break; }
        if (ri >= 0) { buildRoom(st, dim, ri); roomLogic(st, p, dim, ri); }

        // cikis salonu: en uca ulasinca bir sonraki kat / hazine
        int hx = hallX(st);
        if (l.x() >= hx + HALL_LEN - 2 && st.roomState.size() > 2 && st.roomState.get(2) != null && st.roomState.get(2).solved) {
            if (st.floor >= FLOORS - 1) { leave(p, true); return; }
            st.ready = false;
            gainSanity(p, 8);
            giveItem(p, "minecraft:golden_carrot", 3);
            fade(p, 0, 0, 0, 0.3, 0.9, 0.8);
            playTo(p, "mob.endermen.portal", 0.9, 0.5);
            Sys.runTimeout(() -> {
                if (!isValid(p) || !states.containsKey(p.id())) return;
                for (int i = 0; i < 3; i++) cmd(dim, "fill " + st.x0 + " " + (y0Of(st) - 1) + " " + (st.z0 - 7) + " " + (st.x0 + 90) + " " + (y0Of(st) + 7) + " " + (st.z0 + 7) + " air");
                st.floor += 1;
                beginFloor(st, p, dim);
            }, 10);
        }
    }

    // ---------------------------------------------------------
    //  Kayit + komutlar
    // ---------------------------------------------------------
    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        initialized = true;

        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("depths")) return;
            for (Player p : World.getPlayers()) {
                try {
                    State st = states.get(p.id());
                    if (st == null) {
                        Map<String, Object> saved = Json.asMap(playerGet(p, STATE_KEY, null));
                        double py = p.location().y();
                        if (saved != null && py > YD - 6 && py < YD + FLOORS * FLOOR_STEP + 12) {
                            // yeniden baslatma: durum kayip, guvenli cikis
                            st = new State();
                            st.ownerId = p.id();
                            st.origin = new Vec3(Json.num(saved.get("ox"), 0), Json.num(saved.get("oy"), 0), Json.num(saved.get("oz"), 0));
                            st.x0 = Json.intv(saved.get("x0"), 0);
                            st.z0 = Json.intv(saved.get("z0"), 0);
                            st.floor = 0;
                            st.ready = false;
                            states.put(p.id(), st);
                            realm.add(p.id());
                            leave(p, false);
                        }
                        continue;
                    }
                    tickPlayer(p, st);
                } catch (Throwable e) { /* yut */ }
            }
        }, 5);

        // mesale sayaci (karanlik oda)
        try {
            Events.After.playerPlaceBlock.subscribe(ev -> {
                try {
                    State st = states.get(ev.player.id());
                    if (st == null || !st.ready || !ev.block.typeId().contains("torch")) return;
                    for (int i = 0; i < 3; i++) {
                        if (!"dark".equals(st.rooms.get(i))) continue;
                        int rx = roomX(st, i);
                        if (ev.block.x() >= rx && ev.block.x() <= rx + ROOM_LEN) {
                            RoomState rs = st.roomState.get(i);
                            rs.torches = rs.torches + 1;
                            actionbar(ev.player, tx("§8mesale ", "§8torch ") + "§e" + rs.torches + "§8/6");
                            playTo(ev.player, "random.fizz", 0.4, 1.2);
                        }
                    }
                } catch (Throwable e) { /* yut */ }
            });
            Events.After.playerLeave.subscribe(ev -> { states.remove(ev.playerId); });
        } catch (Throwable e) { /* yut */ }

        // Komutlar
        try {
            Events.Before.chatSend.subscribe(ev -> {
                String m = ev.message;
                if (!m.equals("!dwdescend") && !m.equals("!dwdescend confirm") && !m.equals("!dwdescend exit") && !m.equals("!dwdescend skip")) return;
                ev.cancel = true;
                Player p = ev.sender;
                Sys.run(() -> {
                    try {
                        if (m.equals("!dwdescend exit")) {
                            if (states.containsKey(p.id())) leave(p, false);
                            else say(p, tx("§7Derinliklerde degilsin.§r", "§7You are not in the Depths.§r"));
                            return;
                        }
                        if (m.equals("!dwdescend skip")) {
                            State st = states.get(p.id());
                            if (st != null && st.ready) {
                                for (int i = 0; i < 3; i++) { buildRoom(st, overworld(), i); st.roomState.get(i).solved = true; }
                                safe(() -> p.teleport(new Vec3(hallX(st) + HALL_LEN - 1.5, y0Of(st) + 1, st.z0 + 0.5), Opts.Teleport.dim(overworld())));
                            }
                            return;
                        }
                        if (m.equals("!dwdescend")) {
                            say(p, tx("§6Bos Derinlikler§r\n§73 katli prosedurel zindan: her odanin bir kurali var (heykeller, karanlik, sessizlik, zamana karsi yaris). Esyalarini kaybetmezsin.\n§eGirmek icin: !dwdescend confirm  §7(gun 5+ gerekir)",
                                "§6The Hollow Depths§r\n§7A 3-floor procedural dungeon: every room has a rule (statues, darkness, silence, a race against time). You lose no items.\n§eTo enter: !dwdescend confirm  §7(requires day 5+)"));
                            return;
                        }
                        if (getDay() < 5) { say(p, tx("§7Derinlikler 5. gunden sonra acilir.§r", "§7The Depths open after day 5.§r")); return; }
                        if (!enter(p)) say(p, tx("§7Simdi inemezsin (Overworld'de ve baska bir deneyimde olmamalisin).§r", "§7You can't descend now (be in the Overworld, outside other ordeals).§r"));
                    } catch (Throwable e) { /* yut */ }
                });
            });
        } catch (Throwable e) { /* yut */ }
    }
}

package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.*;
import java.util.function.Consumer;

import static com.duskwalker.mod.MainA.*;

/**
 * main.js bolum B (JS satir 1131-2290): labirent kurulumu/kacis zamanlayicisi, kacirma tetigi, uyum saglayan AI
 * (mesale / kamp), hikaye + kilometre tasi mesajlari, test komutlari (!dwconfig ...), kurt intikami, Sizinti (Seep) ve
 * Pencere Ruhu (Watcher) yaratiklari, kalp atisi, ana olay zamanlayicisi, Stalk / Jumpscare / QuickStrike olaylari ve
 * Hunt bitis geri cagrilari (huntEndCallbacks).
 *
 * Not: 1131-1148 satirlarindaki generateMaze() govdesinin sonu MainA'da (sinirda bolunmustu). Satir 2289-2299'daki
 * entityDie dinleyicisi (huntEndCallbacks'i tuketir) sinirda kalan yari-ifade oldugu icin burada tamamlandi;
 * MainC bu dinleyiciyi TEKRAR kaydetmemeli.
 */
public final class MainB {
    private MainB() {}

    // ------------------------------------------------------------------ kucuk yardimcilar
    /** JS sayi -> metin: tam sayi degerliyse ondaliksiz ("100"), degilse Double.toString */
    public static String numStr(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return Long.toString((long) v);
        return Double.toString(v);
    }

    private static double dn(Object o, double def) { return o instanceof Number n ? n.doubleValue() : def; }

    /** { target: player } gibi creature state ek verisi */
    private static Map<String, Object> tgt(Player p) {
        Map<String, Object> m = new HashMap<>();
        m.put("target", p);
        return m;
    }

    private static boolean inGrid(boolean[][] open, int x, int z) {
        return x >= 0 && z >= 0 && x < open.length && z < open[x].length;
    }
    private static boolean isOpen(boolean[][] open, int x, int z) { return inGrid(open, x, z) && open[x][z]; }

    // ================================================================== LABIRENT (devam)
    /** oyuncunun blogundan cikisa en kisa yol (blok izgarasinda), yoksa null. Anahtar = x * S + z. */
    public static Set<Integer> mazePath(boolean[][] open, int[] from, int[] to) {
        final int S = MAZE_S;
        int startK = from[0] * S + from[1], target = to[0] * S + to[1];
        Map<Integer, Integer> prev = new HashMap<>();
        prev.put(startK, -1);
        ArrayDeque<Integer> q = new ArrayDeque<>();
        q.add(startK);
        while (!q.isEmpty()) {
            int cur = q.poll();
            if (cur == target) break;
            int x = (int) Math.floor(cur / (double) S), z = cur % S;
            for (int[] d : MAZE_DIRS) {
                int nx = x + d[0], nz = z + d[1];
                if (nx < 0 || nz < 0 || nx >= S || nz >= S || !open[nx][nz]) continue;
                int k = nx * S + nz;
                if (prev.containsKey(k)) continue;
                prev.put(k, cur);
                q.add(k);
            }
        }
        if (!prev.containsKey(target)) return null;
        Set<Integer> set = new HashSet<>();
        int k = target;
        while (k != -1) { set.add(k); k = prev.get(k); }
        return set;
    }

    public static void setMazeBlock(Dimension dim, Vec3 o, int x, int y, int z, String type) {
        try {
            Block b = dim.getBlock(new Vec3(o.x() + x, o.y() + y, o.z() + z));
            if (b != null) b.setType(type);
        } catch (Throwable e) { /* yut */ }
    }

    public static void setMazeColumn(Dimension dim, Vec3 o, int x, int z, String type) {
        for (int y = 1; y <= 3; y++) setMazeBlock(dim, o, x, y, z, type);
    }

    public static void buildMaze(Dimension dim, Vec3 o, Maze maze) {
        final int S = MAZE_S;
        for (int x = -1; x <= S; x++) {
            for (int z = -1; z <= S; z++) {
                boolean border = x < 0 || z < 0 || x >= S || z >= S;
                if (border) {
                    for (int y = -1; y <= 5; y++) setMazeBlock(dim, o, x, y, z, "minecraft:bedrock");
                } else {
                    setMazeBlock(dim, o, x, -1, z, "minecraft:bedrock");
                    setMazeBlock(dim, o, x, 0, z, "minecraft:dark_oak_planks");
                    setMazeBlock(dim, o, x, 4, z, "minecraft:bedrock");
                    for (int y = 1; y <= 3; y++) setMazeBlock(dim, o, x, y, z, maze.open[x][z] ? "minecraft:air" : mazeWallBlock());
                }
            }
        }
        setMazeBlock(dim, o, 1, 0, 1, "minecraft:soul_soil");
        for (int[] c : maze.lights) setMazeBlock(dim, o, 2 * c[0] + 1, 1, 2 * c[1] + 1, "minecraft:soul_lantern");
        for (int[] c : maze.decoys) setMazeBlock(dim, o, 2 * c[0] + 1, 0, 2 * c[1] + 1, "minecraft:emerald_block");
        // gercek cikis: zumrut + YANAN ruh feneri
        setMazeBlock(dim, o, S - 2, 0, S - 2, "minecraft:emerald_block");
        setMazeBlock(dim, o, S - 2, 1, S - 2, "minecraft:soul_lantern");
        int si = 0;
        for (int[] c : maze.signs) {
            try {
                Vec3 at = new Vec3(o.x() + 2 * c[0] + 1, o.y() + 1, o.z() + 2 * c[1] + 1);
                Block b = dim.getBlock(at);
                if (b == null) continue;
                b.setType("minecraft:standing_sign");
                Block sb = dim.getBlock(at); // setType sonrasi taze blok
                if (sb != null) sb.setSignText(MAZE_SIGNS[si % MAZE_SIGNS.length]);
                si++;
            } catch (Throwable e) { /* yut */ }
        }
    }

    // ================================================================== KACIRMA
    public static void triggerAbduction(Player player, Entity monster) {
        try {
            try { monster.playAnimation("animation.duskwalker.attack"); } catch (Throwable e) { /* yut */ }
            try { player.playSound("mob.wither.spawn", Opts.Sound.of(1.0, 0.3)); } catch (Throwable e) { /* yut */ }
            try { player.cameraFade(Opts.Fade.of(0, 0, 0, 0.3, 1.0, 0.6)); } catch (Throwable e) { /* yut */ }
            try { player.setTitle("§4YAKALANDIN", Opts.Title.of(0, 20, 10, "")); } catch (Throwable e) { /* yut */ }
            final Vec3 originalLoc = new Vec3(player.location().x(), player.location().y(), player.location().z());
            final String originalDimId = player.dimension().id();
            Sys.runTimeout(() -> {
                try { monster.triggerEvent("duskwalker:vanish"); } catch (Throwable e) { /* yut */ }
                try {
                    Dimension overworld = World.getDimension("overworld");
                    // Odayi oyuncunun SU AN durdugu sutunun tam ustune, gokyuzunun cok yuksegine insa ediyoruz.
                    // O sutun zaten kesinlikle yuklu (oyuncu tam orada duruyor).
                    Vec3 roomOrigin = new Vec3(
                        Math.floor(player.location().x()) - 11,
                        280,
                        Math.floor(player.location().z()) - 11);
                    Maze maze = generateMaze();
                    Vec3 startPos = new Vec3(roomOrigin.x() + 1.5, roomOrigin.y() + 1, roomOrigin.z() + 1.5);
                    buildMaze(overworld, roomOrigin, maze);
                    player.teleport(startPos, Opts.Teleport.dim(overworld));
                    // hafif korluk: karabasandan yeni uyanmis gibi
                    try { player.addEffect("blindness", 70, 0, false); } catch (Throwable e) { /* yut */ }
                    startEscapeTimer(player, roomOrigin, originalLoc, originalDimId, maze);
                } catch (Throwable e) { /* yut */ }
            }, 20);
        } catch (Throwable e) { /* yut */ }
    }

    /** startEscapeTimer'in closure durumu */
    private static final class Escape {
        final Player player; final Vec3 roomOrigin; final Vec3 originalLoc; final String originalDimId; final Maze maze;
        final int S = MAZE_S;
        final Dimension dimension = World.getDimension("overworld");
        long deadline;
        final Map<Integer, int[]> decoyLeft = new HashMap<>();
        final List<Entity> chasers = new ArrayList<>();
        boolean finished = false;
        int handle;
        long nextShift, nextWhisper, nextDark, nextChaser;

        Escape(Player player, Vec3 roomOrigin, Vec3 originalLoc, String originalDimId, Maze maze) {
            this.player = player; this.roomOrigin = roomOrigin; this.originalLoc = originalLoc; this.originalDimId = originalDimId; this.maze = maze;
        }

        void cleanup() {
            finished = true;
            try { Sys.clearRun(handle); } catch (Throwable e) { /* yut */ }
            for (Entity c : chasers) { try { if (MainC.entityOk(c)) c.triggerEvent("duskwalker:vanish"); } catch (Throwable e) { /* yut */ } }
        }

        int[] playerBlock() {
            Vec3 l = player.location();
            return new int[] { (int) Math.floor(l.x() - roomOrigin.x()), (int) Math.floor(l.z() - roomOrigin.z()) };
        }

        void spawnChaser(int[] cell) {
            try {
                if (chasers.size() >= 4) return;
                int bx = 0, bz = 0;
                boolean have = false;
                if (cell != null) { bx = 2 * cell[0] + 1; bz = 2 * cell[1] + 1; have = true; }
                else {
                    int[] pb = playerBlock();
                    for (int t = 0; t < 40; t++) {
                        int cx = randInt(0, MAZE_N - 1), cz = randInt(0, MAZE_N - 1);
                        int x = 2 * cx + 1, z = 2 * cz + 1;
                        if (!maze.open[x][z]) continue;
                        if (Math.abs(x - pb[0]) + Math.abs(z - pb[1]) < 14) continue;
                        bx = x; bz = z; have = true; break;
                    }
                    if (!have) return;
                }
                final Entity c = dimension.spawnEntity("duskwalker:duskwalker", new Vec3(roomOrigin.x() + bx + 0.5, roomOrigin.y() + 1, roomOrigin.z() + bz + 0.5));
                try { c.addEffect("slowness", 20 * 140, 1, false); } catch (Throwable e) { /* yut */ }
                try { c.triggerEvent("duskwalker:hunt_start"); } catch (Throwable e) { /* yut */ }
                chasers.add(c);
                Sys.runTimeout(() -> { try { if (MainC.entityOk(c)) c.triggerEvent("duskwalker:vanish"); } catch (Throwable e) { /* yut */ } }, 20 * 140);
                try { player.playSound("mob.endermen.stare", Opts.Sound.of(1.0, 0.4)); } catch (Throwable e) { /* yut */ }
            } catch (Throwable e) { /* yut */ }
        }

        /** duvarlari degistir: oyuncudan cikisa giden yol HER ZAMAN acik kalir */
        void shiftWalls() {
            boolean[][] open = maze.open;
            int[] pb = playerBlock();
            if (!isOpen(open, pb[0], pb[1])) {
                int[] found = null;
                for (int[] d : MAZE_DIRS) {
                    int nx = pb[0] + d[0], nz = pb[1] + d[1];
                    if (isOpen(open, nx, nz)) { found = new int[] { nx, nz }; break; }
                }
                if (found == null) return;
                pb = found;
            }
            Set<Integer> path = mazePath(open, pb, new int[] { S - 2, S - 2 });
            if (path == null) return;
            List<int[]> closeC = new ArrayList<>(), openC = new ArrayList<>();
            for (int x = 1; x < S - 1; x++) {
                for (int z = 1; z < S - 1; z++) {
                    if ((x % 2) + (z % 2) != 1) continue;
                    double d = Math.hypot(x - pb[0], z - pb[1]);
                    if (d < 3) continue;
                    if (open[x][z]) { if (!path.contains(x * S + z)) closeC.add(new int[] { x, z }); }
                    else openC.add(new int[] { x, z });
                }
            }
            mazeShuffle(closeC); mazeShuffle(openC);
            for (int i = 0; i < Math.min(4, closeC.size()); i++) {
                open[closeC.get(i)[0]][closeC.get(i)[1]] = false;
                setMazeColumn(dimension, roomOrigin, closeC.get(i)[0], closeC.get(i)[1], mazeWallBlock());
            }
            for (int i = 0; i < Math.min(3, openC.size()); i++) {
                open[openC.get(i)[0]][openC.get(i)[1]] = true;
                setMazeColumn(dimension, roomOrigin, openC.get(i)[0], openC.get(i)[1], "minecraft:air");
            }
            try { player.playSound("dig.stone", Opts.Sound.of(1.0, 0.5)); } catch (Throwable e) { /* yut */ }
            try { player.sendMessage("§8(duvarlar yer değiştirdi...)§r"); } catch (Throwable e) { /* yut */ }
        }

        void tickOnce() {
            if (finished) return;
            try {
                Object cur = player.getDynamicProperty(ABDUCTED_UNTIL);
                if (!(cur instanceof Number cn) || cn.doubleValue() != deadline) { cleanup(); return; }
                long tick = Sys.currentTick();
                Vec3 loc = player.location();
                if (Math.abs(loc.y() - roomOrigin.y()) > 20) { player.setDynamicProperty(ABDUCTED_UNTIL, 0.0); cleanup(); return; }
                long remaining = Math.max(0, (long) Math.ceil((deadline - tick) / 20.0));
                try { player.setActionBar("§cKAÇ! " + remaining + "s§r"); } catch (Throwable e) { /* yut */ }

                int[] pb = playerBlock();
                if (pb[0] == S - 2 && pb[1] == S - 2) {
                    player.setDynamicProperty(ABDUCTED_UNTIL, 0.0);
                    cleanup();
                    try { player.teleport(originalLoc, Opts.Teleport.dim(World.getDimension(originalDimId))); } catch (Throwable e) { /* yut */ }
                    try { player.sendMessage("§7Başardın... bu sefer.§r"); } catch (Throwable e) { /* yut */ }
                    return;
                }

                int key = pb[0] * S + pb[1];
                if (decoyLeft.containsKey(key)) {
                    int[] cell = decoyLeft.get(key);
                    decoyLeft.remove(key);
                    setMazeBlock(dimension, roomOrigin, pb[0], 0, pb[1], "minecraft:coal_block");
                    deadline = Math.max(tick + 100, deadline - 200);
                    player.setDynamicProperty(ABDUCTED_UNTIL, (double) deadline);
                    try { player.sendMessage("§4Duskwalker§r: Sahte. Zaman kaybettin."); } catch (Throwable e) { /* yut */ }
                    try { player.addEffect("blindness", 60, 0, false); } catch (Throwable e) { /* yut */ }
                    try { player.playSound("mob.wither.ambient", Opts.Sound.of(1.0, 0.5)); } catch (Throwable e) { /* yut */ }
                    spawnChaser(cell);
                }

                if (tick >= deadline) {
                    player.setDynamicProperty(ABDUCTED_UNTIL, 0.0);
                    cleanup();
                    try { player.kill(); } catch (Throwable e) { try { player.applyDamage(1000); } catch (Throwable e2) { /* yut */ } }
                    return;
                }
                if (tick >= nextShift) { nextShift = tick + 180 + randInt(0, 80); shiftWalls(); }
                if (tick >= nextWhisper) {
                    nextWhisper = tick + 240 + randInt(0, 200);
                    try { player.sendMessage("§4Duskwalker§r: " + MAZE_WHISPERS[randInt(0, MAZE_WHISPERS.length - 1)]); } catch (Throwable e) { /* yut */ }
                }
                if (tick >= nextDark) {
                    nextDark = tick + 360 + randInt(0, 140);
                    try { if (cfgIsTrue(getConfig(), "darkness_fx")) player.addEffect("darkness", 120, 0, false); } catch (Throwable e) { /* yut */ }
                }
                if (tick >= nextChaser && chasers.size() < 2) {
                    nextChaser = tick + 35 * 20;
                    spawnChaser(null);
                }
            } catch (Throwable e) { try { cleanup(); } catch (Throwable e2) { /* yut */ } }
        }
    }

    public static void startEscapeTimer(Player player, Vec3 roomOrigin, Vec3 originalLoc, String originalDimId, Maze maze) {
        final Escape st = new Escape(player, roomOrigin, originalLoc, originalDimId, maze);
        long startTick = Sys.currentTick();
        st.deadline = startTick + 90 * 20;
        player.setDynamicProperty(ABDUCTED_UNTIL, (double) st.deadline);
        try { player.sendMessage("§4Duskwalker§r: Buradan... kaçabilirsen."); } catch (Throwable e) { /* yut */ }
        try { player.sendMessage("§7(Gerçek çıkışta zümrüt zemin üstünde bir ruh feneri yanar. Her zümrüt çıkış değildir.)§r"); } catch (Throwable e) { /* yut */ }

        for (int[] c : maze.decoys) st.decoyLeft.put((2 * c[0] + 1) * st.S + (2 * c[1] + 1), c);
        st.nextShift = startTick + 200; st.nextWhisper = startTick + 240; st.nextDark = startTick + 300; st.nextChaser = startTick + 25 * 20;
        st.handle = Sys.runInterval(st::tickOnce, 10);
    }

    private static void abductionTick() {
        if (isDefeated()) return;
        if (cfgIsFalse(getConfig(), "abduction")) return;
        if (!getUnlockedEventSet(getDay()).contains("abduction")) return;
        for (Player player : World.getPlayers()) {
            try {
                boolean sleeping = isPlayerSleeping(player);
                boolean wasSleeping = truthy(player.getDynamicProperty(WAS_SLEEPING));
                long tick = Sys.currentTick();
                if (sleeping && !wasSleeping) {
                    player.setDynamicProperty(WAS_SLEEPING, true);
                    Object cooldown = player.getDynamicProperty(SLEEP_COOLDOWN);
                    if ((!(cooldown instanceof Number) || tick > ((Number) cooldown).doubleValue()) && Math.random() < 0.4) {
                        player.setDynamicProperty(SLEEP_COOLDOWN, (double) (tick + 24000));
                        spawnSleepWatcher(player);
                    }
                } else if (!sleeping && wasSleeping) {
                    player.setDynamicProperty(WAS_SLEEPING, false);
                    if (sleepWatchers.containsKey(player.id())) {
                        Entity monster = sleepWatchers.get(player.id());
                        sleepWatchers.remove(player.id());
                        triggerAbduction(player, monster);
                    }
                }
            } catch (Throwable e) { /* yut */ }
        }
    }

    // ================================================================== OZELLIK 4: DAVRANISA UYUM SAGLAYAN YAPAY ZEKA
    public static void playerNotifyAnnoyed(Player p) {
        try { p.sendMessage("§7(havadaki isik seni rahatsiz eden birini cekiyor...)§r"); } catch (Throwable e) { /* yut */ }
    }

    private static void campingTick() {
        long tick = Sys.currentTick();
        for (Player player : World.getPlayers()) {
            try {
                Object lx = player.getDynamicProperty(LAST_POS_X);
                Object lz = player.getDynamicProperty(LAST_POS_Z);
                Object lt = player.getDynamicProperty(LAST_POS_TICK);
                if (!(lx instanceof Number) || !(lt instanceof Number) || tick - ((Number) lt).doubleValue() > 6000) {
                    if (lx instanceof Number) {
                        double dist = Math.hypot(player.location().x() - ((Number) lx).doubleValue(), player.location().z() - dn(lz, 0));
                        if (dist < 8) {
                            player.setDynamicProperty(CAMPING_UNTIL, (double) (tick + 3 * TICKS_PER_MIN));
                        }
                    }
                    player.setDynamicProperty(LAST_POS_X, player.location().x());
                    player.setDynamicProperty(LAST_POS_Z, player.location().z());
                    player.setDynamicProperty(LAST_POS_TICK, (double) tick);
                }
            } catch (Throwable e) { /* yut */ }
        }
    }

    /** lines[i]'yi delay tick arayla sirayla world.sendMessage ile yollar (JS: send closure) */
    private static void sendLinesSpaced(String[] lines, int i, int delay) {
        if (i < lines.length) {
            World.sendMessage(lines[i]);
            final int next = i + 1;
            Sys.runTimeout(() -> { try { sendLinesSpaced(lines, next, delay); } catch (Throwable e) { /* yut */ } }, delay);
        }
    }

    private static void milestoneTick() {
        if (isDefeated()) return;
        int day = getDay();
        for (int m : new int[] { 10, 25, 50 }) {
            try {
                if (day >= m && !truthy(World.getDynamicProperty(MILESTONE_KEY + m))) {
                    World.setDynamicProperty(MILESTONE_KEY + m, true);
                    String[] lines = MILESTONE_LINES.get(m);
                    sendLinesSpaced(lines, 0, 50);
                    if (m == 50) {
                        try { World.getDimension("overworld").runCommand("weather thunder 6000"); } catch (Throwable e) { /* yut */ }
                    }
                }
            } catch (Throwable e) { /* yut */ }
        }
    }

    // ================================================================== TEST KOMUTLARI
    public static void handleConfigCommand(Player player, String msg) {
        String[] parts = msg.trim().split("\\s+");
        Map<String, Object> cfg = getConfig();
        Map<String, Object> enabled = cfgEnabled(cfg);
        if (parts.length == 1) {
            player.sendMessage("§7Yogunluk carpani: " + numStr(cfgIntensityMult(cfg)) + "§r");
            for (String k : new ArrayList<>(enabled.keySet())) {
                player.sendMessage("§7 - " + k + ": " + (truthy(enabled.get(k)) ? "acik" : "kapali") + "§r");
            }
            player.sendMessage("§7Kullanim: !dwconfig intensity <0-2> | !dwconfig toggle <ad> | !dwconfig day <sayi|clear> | !dwconfig reset§r");
            return;
        }
        String sub = parts[1];
        boolean hasArg = parts.length > 2;
        if (sub.equals("intensity") && hasArg) {
            double v;
            try { v = Double.parseDouble(parts[2]); } catch (NumberFormatException ex) { player.sendMessage("§cGecersiz deger.§r"); return; }
            if (Double.isNaN(v)) { player.sendMessage("§cGecersiz deger.§r"); return; }
            v = Math.max(0, Math.min(2, v));
            cfg.put("intensityMult", v);
            saveConfig(cfg);
            player.sendMessage("§7Yogunluk carpani " + numStr(v) + " olarak ayarlandi.§r");
        } else if (sub.equals("toggle") && hasArg) {
            String key = parts[2];
            if (!enabled.containsKey(key)) {
                player.sendMessage("§cGecersiz anahtar: " + key + "§r");
                player.sendMessage("§7Gecerli anahtarlar: " + String.join(", ", enabled.keySet()) + "§r");
                return;
            }
            enabled.put(key, !truthy(enabled.get(key)));
            saveConfig(cfg);
            player.sendMessage("§7" + key + ": " + (truthy(enabled.get(key)) ? "acik" : "kapali") + "§r");
        } else if (sub.equals("reset")) {
            saveConfig(cloneDefaultConfig());
            player.sendMessage("§7Ayarlar sifirlandi.§r");
        } else if (sub.equals("day") && hasArg) {
            if (parts[2].equals("clear")) {
                try { World.setDynamicProperty(DAY_OVERRIDE, null); } catch (Throwable e) { /* yut */ }
                player.sendMessage("§7Gun zorlamasi kaldirildi - gercek dunya gunu kullanilacak.§r");
            } else {
                int n;
                try { n = parseIntJs(parts[2]); } catch (NumberFormatException ex) { player.sendMessage("§cGecersiz gun.§r"); return; }
                if (n < 1) { player.sendMessage("§cGecersiz gun.§r"); return; }
                try { World.setDynamicProperty(DAY_OVERRIDE, (double) n); } catch (Throwable e) { /* yut */ }
                Phase phase = PHASES[getCurrentPhaseIndex(n)];
                player.sendMessage("§7Gun " + n + " olarak zorlandi (test amacli). Faz: " + phase.name + "§r");
            }
        } else {
            player.sendMessage("§7Kullanim: !dwconfig intensity <0-2> | !dwconfig toggle <ad> | !dwconfig day <sayi|clear> | !dwconfig reset§r");
        }
    }

    /** JS parseInt(s, 10): bastaki rakamlari okur, hic yoksa NumberFormatException (NaN) */
    private static int parseIntJs(String s) {
        String t = s.trim();
        int i = 0;
        if (i < t.length() && (t.charAt(i) == '+' || t.charAt(i) == '-')) i++;
        int st = i;
        while (i < t.length() && Character.isDigit(t.charAt(i))) i++;
        if (i == st) throw new NumberFormatException(s);
        return Integer.parseInt(t.substring(0, i));
    }

    private static final List<String> CMDS = Arrays.asList("!duskwalker", "!stalk", "!hunt", "!huntfast", "!quickstrike", "!door", "!torch", "!sign", "!whisper", "!chat", "!jumpscare", "!possession", "!journal", "!sanity", "!structure", "!abduct", "!confront", "!twinhunt", "!phase", "!reflection", "!evidence", "!seep", "!watcher");

    private static void runTestCommand(Player player, String msg) {
        try {
            switch (msg) {
                case "!duskwalker" -> {
                    List<Entity> wolves = player.dimension().getEntities(Opts.EntityQuery.type("minecraft:wolf").near(player.location(), 20));
                    Entity wolf = wolves.isEmpty() ? null : wolves.get(0);
                    if (wolf != null) { wolf.setDynamicProperty(TRIGGERED, true); spawnDuskwalkerForWolf(wolf, player.dimension()); }
                    else player.sendMessage("§7Yakinda kurt bulunamadi.§r");
                }
                case "!stalk" -> doStalk(player);
                case "!hunt" -> MainC.doHunt(player, false);
                case "!huntfast" -> MainC.doHunt(player, true);
                case "!quickstrike" -> doQuickStrike(player);
                case "!door" -> MainC.doDoor(player);
                case "!torch" -> MainC.doTorch(player);
                case "!sign" -> MainC.doSign(player);
                case "!whisper" -> MainC.doWhisper(player);
                case "!chat" -> MainC.doChat(player);
                case "!jumpscare" -> doJumpscare(player);
                case "!possession" -> MainC.doPossession(player);
                case "!journal" -> doJournal(player);
                case "!sanity" -> player.sendMessage("§7Saglamlik: " + numStr(getSanity(player)) + "/100§r");
                case "!structure" -> {
                    if (!spawnNextStructure(player)) {
                        try { player.sendMessage(Core.tx("§7Yapi su an yerlestirilemedi (bolge yuklu degil ya da uygun yer yok). 2 dk sonra yeniden denenecek.§r", "§7The structure could not be placed right now (area not loaded or no suitable spot). Retrying in 2 minutes.§r")); } catch (Throwable e) { /* yut */ }
                    }
                }
                case "!abduct" -> spawnSleepWatcher(player);
                case "!confront" -> triggerFinale(player);
                case "!twinhunt" -> MainC.doTwinHunt(player);
                case "!phase" -> {
                    int day = getDay();
                    Phase phase = PHASES[getCurrentPhaseIndex(day)];
                    player.sendMessage("§7Gun " + day + " - Faz: " + phase.name + "§r");
                    player.sendMessage("§7Acik saldiri turleri: " + String.join(", ", getUnlockedEventSet(day)) + "§r");
                }
                case "!reflection" -> MainC.doReflection(player);
                case "!evidence" -> {
                    double count = dn(player.getDynamicProperty(EVIDENCE_COUNT), 0);
                    double trust = dn(player.getDynamicProperty(AYLIN_TRUST), 0);
                    player.sendMessage("§7Kanit: " + numStr(count) + " | Aylin guveni: " + numStr(trust) + "/10" + (truthy(player.getDynamicProperty(AYLIN_BOND)) ? " (bag tamamlandi)" : "") + "§r");
                }
                case "!seep" -> trySpawnSeep(player);
                case "!watcher" -> trySpawnWatcher(player);
                default -> { }
            }
        } catch (Throwable e) { /* yut */ }
    }

    // ================================================================== KURT INTIKAMI
    public static final int WOLF_WARNING_LEAD = 100; // ~5 saniye

    private static final class WolfItem {
        final Entity wolf; final Dimension dimension;
        WolfItem(Entity w, Dimension d) { wolf = w; dimension = d; }
    }

    private static final Runnable wolfScanner = createBudgetedScanner(
        () -> {
            List<WolfItem> items = new ArrayList<>();
            if (isDefeated() || cfgIsFalse(getConfig(), "wolfRevenge")) return items;
            for (String dimId : new String[] { "overworld", "nether", "the_end" }) {
                Dimension dimension;
                try { dimension = World.getDimension(dimId); } catch (Throwable e) { continue; }
                List<Entity> wolves;
                try { wolves = dimension.getEntities(Opts.EntityQuery.type("minecraft:wolf")); } catch (Throwable e) { continue; }
                for (Entity wolf : wolves) items.add(new WolfItem(wolf, dimension));
            }
            return items;
        },
        (WolfItem it) -> {
            Entity wolf = it.wolf; Dimension dimension = it.dimension;
            long tick = Sys.currentTick();
            boolean isTamed = false;
            try { isTamed = wolf.isTamed(); } catch (Throwable e) { isTamed = false; }
            if (!isTamed) return;
            Object triggered;
            try { triggered = wolf.getDynamicProperty(TRIGGERED); } catch (Throwable e) { return; }
            if (truthy(triggered)) return;
            Object tamedAt;
            try { tamedAt = wolf.getDynamicProperty(TAMED_AT); } catch (Throwable e) { return; }
            if (tamedAt == null) {
                try { wolf.setDynamicProperty(TAMED_AT, (double) tick); wolf.setDynamicProperty(DELAY, (double) randInt(2400, 3600)); } catch (Throwable e) { /* yut */ }
                return;
            }
            double delay = 2400;
            try { Object d = wolf.getDynamicProperty(DELAY); if (d != null) delay = dn(d, 2400); } catch (Throwable e) { /* yut */ }
            double remaining = delay - (tick - dn(tamedAt, 0));
            if (remaining <= 0) {
                try { wolf.setDynamicProperty(TRIGGERED, true); } catch (Throwable e) { /* yut */ }
                spawnDuskwalkerForWolf(wolf, dimension);
            } else if (remaining <= WOLF_WARNING_LEAD) {
                boolean warned = false;
                try { warned = truthy(wolf.getDynamicProperty(WOLF_WARNED)); } catch (Throwable e) { /* yut */ }
                if (!warned) {
                    try { wolf.setDynamicProperty(WOLF_WARNED, true); } catch (Throwable e) { /* yut */ }
                    warnWolf(wolf, dimension);
                }
            }
        }
    );

    // ================================================================== COKLU-YARATIK KOORDINASYONU
    public static void nudgeNextVisible(Player player, double reduceFactor) {
        try {
            long tick = Sys.currentTick();
            Object next = player.getDynamicProperty(NEXT_VISIBLE);
            if (next instanceof Number n && n.doubleValue() > tick) {
                double remaining = n.doubleValue() - tick;
                player.setDynamicProperty(NEXT_VISIBLE, (double) (tick + Math.round(remaining * reduceFactor)));
            }
        } catch (Throwable e) { /* yut */ }
    }

    public static boolean isNight() {
        try { int t = World.getTimeOfDay(); return t >= 13000 && t <= 23000; } catch (Throwable e) { return false; }
    }

    public static boolean isUnderground(Player player) {
        try { return player.location().y() < 40; } catch (Throwable e) { return false; }
    }

    /** Oyuncunun yakininda (kucuk bir hacimde) cam blok var mi - "pencere" tetikleyicisi */
    public static boolean hasNearbyGlass(Player player) {
        try {
            Dimension dimension = player.dimension();
            int px = (int) Math.floor(player.location().x()), py = (int) Math.floor(player.location().y()), pz = (int) Math.floor(player.location().z());
            for (int dx = -5; dx <= 5; dx += 2) {
                for (int dz = -5; dz <= 5; dz += 2) {
                    for (int dy = -1; dy <= 2; dy++) {
                        try {
                            Block b = dimension.getBlock(new Vec3(px + dx, py + dy, pz + dz));
                            if (b != null && b.typeId() != null && b.typeId().contains("glass")) return true;
                        } catch (Throwable e) { /* yut */ }
                    }
                }
            }
        } catch (Throwable e) { /* yut */ }
        return false;
    }

    // ---- SIZINTI (Seep) ----
    private static void seepBehavior(Entity entity, CreatureState state, Dimension dimension) {
        Player player = state.get("target") instanceof Player p ? p : null;
        if (player == null || !player.isValid()) {
            try { entity.triggerEvent("duskwalker:vanish"); } catch (Throwable e) { /* yut */ }
            clearCreatureState(entity);
            return;
        }
        if ("emerging".equals(state.mode)) {
            if (state.stateTicks >= 6) enterCreatureState(entity, "lunging", tgt(player));
            return;
        }
        if ("lunging".equals(state.mode)) {
            try {
                Vec3 ex = entity.location(), px = player.location();
                double dx = px.x() - ex.x(), dy = (px.y() + 0.9) - ex.y(), dz = px.z() - ex.z();
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (dist > 1.4) {
                    double step = 0.55;
                    entity.teleport(new Vec3(ex.x() + (dx / dist) * step, ex.y() + (dy / dist) * step * 0.5, ex.z() + (dz / dist) * step), Opts.Teleport.dim(dimension));
                } else {
                    try { player.applyDamage(3, Opts.Damage.of("entityAttack", entity)); } catch (Throwable e) { /* yut */ }
                    try { player.sendMessage("§4Sizinti§r: ..."); } catch (Throwable e) { /* yut */ }
                    enterCreatureState(entity, "retreating", tgt(player));
                    return;
                }
            } catch (Throwable e) { /* yut */ }
            if (state.stateTicks >= 30) enterCreatureState(entity, "retreating", tgt(player));
            return;
        }
        if ("retreating".equals(state.mode)) {
            if (state.stateTicks == 1) { try { entity.playAnimation("animation.duskwalker.attack"); } catch (Throwable e) { /* yut */ } }
            if (state.stateTicks >= 4) {
                try { dimension.playSound("dig.stone", entity.location(), Opts.Sound.of(0.8, 0.6)); } catch (Throwable e) { /* yut */ }
                try { entity.triggerEvent("duskwalker:vanish"); } catch (Throwable e) { /* yut */ }
                nudgeNextVisible(player, 0.75);
                clearCreatureState(entity);
            }
        }
    }

    public static void trySpawnSeep(Player player) {
        try {
            Dimension dimension = player.dimension();
            double angle = Math.random() * Math.PI * 2;
            double dist = 3 + Math.random() * 3;
            Vec3 spawnLoc = new Vec3(player.location().x() + Math.cos(angle) * dist, player.location().y(), player.location().z() + Math.sin(angle) * dist);
            Vec3 pos = findSpawnSpot(dimension, spawnLoc, 0, 2);
            Entity entity;
            try { entity = dimension.spawnEntity("duskwalker:seep", pos); } catch (Throwable e) { return; }
            try { dimension.playSound("dig.stone", pos, Opts.Sound.of(1.0, 0.5)); } catch (Throwable e) { /* yut */ }
            enterCreatureState(entity, "emerging", tgt(player));
            loseSanity(player, 10);
            addEvidence(player, 1);
            try { player.sendMessage("§7(karanlikta bir sey kipirdadi...)§r"); } catch (Throwable e) { /* yut */ }
        } catch (Throwable e) { /* yut */ }
    }

    // ---- PENCERE RUHU (Watcher) ----
    private static void watcherBehavior(Entity entity, CreatureState state, Dimension dimension) {
        Player player = state.get("target") instanceof Player p ? p : null;
        if (player == null || !player.isValid()) {
            try { entity.triggerEvent("duskwalker:vanish"); } catch (Throwable e) { /* yut */ }
            clearCreatureState(entity);
            return;
        }
        if ("watching".equals(state.mode)) {
            boolean observed = false;
            try {
                Vec3 view = player.getViewDirection();
                Vec3 ex = entity.location(), px = player.location();
                double tx = ex.x() - px.x(), ty = (ex.y() + 1) - (px.y() + 1.6), tz = ex.z() - px.z();
                double dist = Math.sqrt(tx * tx + ty * ty + tz * tz);
                if (dist > 0.01) {
                    double nx = tx / dist, ny = ty / dist, nz = tz / dist;
                    double dot = view.x() * nx + view.y() * ny + view.z() * nz;
                    observed = dot > 0.9 && dist < 24;
                }
            } catch (Throwable e) { /* yut */ }

            if (observed) {
                state.put("unobservedTicks", 0.0);
            } else {
                double un = state.num("unobservedTicks", 0) + 1;
                state.put("unobservedTicks", un);
                if (un >= 6) {
                    state.put("unobservedTicks", 0.0);
                    try {
                        Vec3 ex = entity.location(), px = player.location();
                        double dx = px.x() - ex.x(), dy = px.y() - ex.y(), dz = px.z() - ex.z();
                        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        if (dist > 3) {
                            double step = 1.4;
                            entity.teleport(new Vec3(ex.x() + (dx / dist) * step, ex.y(), ex.z() + (dz / dist) * step), Opts.Teleport.dim(dimension));
                            try { dimension.playSound("mob.endermen.stare", entity.location(), Opts.Sound.of(0.4, 0.6)); } catch (Throwable e) { /* yut */ }
                        } else {
                            Map<String, Object> extra = tgt(player);
                            enterCreatureState(entity, "arrived", extra);
                            return;
                        }
                    } catch (Throwable e) { /* yut */ }
                }
            }
            if (state.stateTicks > 20 * 45) {
                try { entity.triggerEvent("duskwalker:vanish"); } catch (Throwable e) { /* yut */ }
                clearCreatureState(entity);
            }
            return;
        }
        if ("arrived".equals(state.mode)) {
            if (state.stateTicks == 1) {
                try { player.cameraFade(Opts.Fade.of(0, 0, 0, 0.05, 0.3, 0.5)); } catch (Throwable e) { /* yut */ }
                try { player.playSound("mob.ghast.scream", Opts.Sound.of(1.0, 1.7)); } catch (Throwable e) { /* yut */ }
                try { player.sendMessage("§4(bakmayi biraktigin an... yanindaydi.)§r"); } catch (Throwable e) { /* yut */ }
                loseSanity(player, 20);
                addEvidence(player, 1);
                nudgeNextVisible(player, 0.7);
            }
            if (state.stateTicks >= 6) {
                try { entity.triggerEvent("duskwalker:vanish"); } catch (Throwable e) { /* yut */ }
                clearCreatureState(entity);
            }
        }
    }

    public static void trySpawnWatcher(Player player) {
        try {
            Dimension dimension = player.dimension();
            Vec3 view = player.getViewDirection();
            double dist = 10 + Math.random() * 8;
            Vec3 spawnLoc = new Vec3(player.location().x() - view.x() * dist, player.location().y(), player.location().z() - view.z() * dist);
            Vec3 pos = findSpawnSpot(dimension, spawnLoc, 0, 3);
            Entity entity;
            try { entity = dimension.spawnEntity("duskwalker:watcher", pos); } catch (Throwable e) { return; }
            Map<String, Object> extra = tgt(player);
            extra.put("unobservedTicks", 0.0);
            enterCreatureState(entity, "watching", extra);
            loseSanity(player, 6);
            try { player.sendMessage("§7(penceren disinda bir seyin durdugunu hissediyorsun...)§r"); } catch (Throwable e) { /* yut */ }
        } catch (Throwable e) { /* yut */ }
    }

    // Tetikleyici kosul taramasi da TICK BUTCESI ile yapiliyor (OZELLIK 2)
    private static final Runnable seepWatcherScanner = createBudgetedScanner(
        () -> {
            List<Player> l = new ArrayList<>();
            if (!isDefeated()) l.addAll(World.getPlayers());
            return l;
        },
        (Player player) -> {
            try {
                Map<String, Object> cfg = getConfig();
                long tick = Sys.currentTick();
                Set<String> unlocked = getUnlockedEventSet(getDay());

                if (!cfgIsFalse(cfg, "seep") && unlocked.contains("seep")) {
                    Object next = player.getDynamicProperty(NEXT_SEEP);
                    if (next == null) player.setDynamicProperty(NEXT_SEEP, (double) (tick + randInt(2 * TICKS_PER_MIN, 5 * TICKS_PER_MIN)));
                    else if (tick >= dn(next, 0) && isUnderground(player)) {
                        player.setDynamicProperty(NEXT_SEEP, (double) (tick + randInt(4 * TICKS_PER_MIN, 9 * TICKS_PER_MIN)));
                        trySpawnSeep(player);
                    }
                }
                if (!cfgIsFalse(cfg, "watcher") && unlocked.contains("watcher")) {
                    Object nextW = player.getDynamicProperty(NEXT_WATCHER);
                    if (nextW == null) player.setDynamicProperty(NEXT_WATCHER, (double) (tick + randInt(3 * TICKS_PER_MIN, 6 * TICKS_PER_MIN)));
                    else if (tick >= dn(nextW, 0) && isNight() && hasNearbyGlass(player)) {
                        player.setDynamicProperty(NEXT_WATCHER, (double) (tick + randInt(6 * TICKS_PER_MIN, 12 * TICKS_PER_MIN)));
                        trySpawnWatcher(player);
                    }
                }
            } catch (Throwable e) { /* yut */ }
        }
    );

    // ================================================================== KURT UYARISI / DONUSUMU
    /** OZELLIK 5: kurdun son uyarisi - ulur, tuyleri urperiyormus gibi parcaciklar cikarir. */
    public static void warnWolf(Entity wolf, Dimension dimension) {
        try {
            Vec3 loc = wolf.location();
            try { dimension.playSound("mob.wolf.growl", loc, Opts.Sound.of(0.8, 0.6)); } catch (Throwable e) { /* yut */ }
            Sys.runTimeout(() -> {
                try { if (wolf.isValid()) dimension.playSound("mob.wolf.whine", wolf.location(), Opts.Sound.of(0.7, 1.1)); } catch (Throwable e) { /* yut */ }
            }, 20);
            for (int i = 0; i < 6; i++) {
                Sys.runTimeout(() -> {
                    try {
                        if (!wolf.isValid()) return;
                        Vec3 l = wolf.location();
                        dimension.spawnParticle("minecraft:large_smoke_particle", new Vec3(l.x() + (Math.random() - 0.5) * 0.6, l.y() + 0.4 + Math.random() * 0.4, l.z() + (Math.random() - 0.5) * 0.6));
                    } catch (Throwable e) { /* yut */ }
                }, i * 8);
            }
            try {
                Player owner = null;
                for (Player p : World.getPlayers()) {
                    if (Math.hypot(p.location().x() - loc.x(), p.location().z() - loc.z()) < 16 && p.dimension().id().equals(dimension.id())) { owner = p; break; }
                }
                if (owner != null) owner.sendMessage("§7Kurdunun tuyleri urperiyor... bir sey yaklasiyor.§r");
            } catch (Throwable e) { /* yut */ }
        } catch (Throwable e) { /* yut */ }
    }

    public static void spawnDuskwalkerForWolf(Entity wolf, Dimension dimension) {
        try {
            Vec3 loc = wolf.location();
            final Vec3 pos = findSpawnSpot(dimension, loc, 4, 7);
            final int variant = 0;
            smokePuff(dimension, pos, 12);
            try { dimension.playSound("mob.wither.spawn", pos, Opts.Sound.of(1.0, 0.6 * getVariantPitch(variant))); } catch (Throwable e) { /* yut */ }
            Sys.runTimeout(() -> {
                Entity monster;
                try { monster = dimension.spawnEntity("duskwalker:duskwalker", pos); } catch (Throwable e) { return; }
                final Entity m = monster;
                applyDominant(m);
                applyVariant(m, variant);
                try { World.sendMessage("§4Duskwalker§r: Kokusunu aldim..."); } catch (Throwable e) { /* yut */ }
                Sys.runTimeout(() -> {
                    try { if (m.isValid()) m.playAnimation("animation.duskwalker.attack"); } catch (Throwable e) { /* yut */ }
                    Sys.runTimeout(() -> {
                        try { if (wolf.isValid()) wolf.kill(); } catch (Throwable e) { /* yut */ }
                        runAftermath(m, dimension);
                    }, 10);
                }, 30);
            }, 26);
        } catch (Throwable e) { /* yut */ }
    }

    public static void runAftermath(Entity monster, Dimension dimension) {
        aftermathStep(monster, dimension, 0);
    }

    private static void aftermathStep(Entity monster, Dimension dimension, int i) {
        if (i < TAUNT_LINES.length) {
            try { World.sendMessage(TAUNT_LINES[i]); } catch (Throwable e) { /* yut */ }
            Sys.runTimeout(() -> aftermathStep(monster, dimension, i + 1), 40);
        } else {
            try { smokePuff(dimension, monster.location(), 10); } catch (Throwable e) { /* yut */ }
            try { monster.triggerEvent("duskwalker:vanish"); } catch (Throwable e) { /* yut */ }
        }
    }

    // ================================================================== ANA ZAMANLAYICI
    private static final class Ev {
        final String type; final double weight; final double min;
        Ev(String type, double weight, double min) { this.type = type; this.weight = weight; this.min = min; }
    }

    private static final List<Ev> VISIBLE_EVENTS = List.of(
        new Ev("stalk", 40, 0),
        new Ev("jumpscare", 22, 0),
        new Ev("quickstrike", 18, 0.15),
        new Ev("hunt", 12, 0.45),
        new Ev("huntfast", 6, 0.7),
        new Ev("twinhunt", 3, 0.78));
    private static final List<Ev> AMBIENT_EVENTS = List.of(
        new Ev("whisper", 28, 0),
        new Ev("chat", 22, 0),
        new Ev("sign", 15, 0.2),
        new Ev("torch", 15, 0.2),
        new Ev("door", 15, 0.25),
        new Ev("reflection", 12, 0.1),
        new Ev("possession", 10, 0.3));

    /** havuzdan agirlikli tur secer; uygun yoksa null */
    public static String pickFromPool(List<Ev> pool, double intensity) {
        List<Ev> avail = new ArrayList<>();
        for (Ev e : pool) if (intensity >= e.min) avail.add(e);
        if (avail.isEmpty()) return null;
        double total = 0;
        for (Ev e : avail) total += e.weight;
        double r = Math.random() * total;
        for (Ev e : avail) { if (r < e.weight) return e.type; r -= e.weight; }
        return avail.get(0).type;
    }

    // ---- Kalp atisi ----
    private static void heartbeatTick() {
        if (isDefeated()) return;
        if (cfgIsFalse(getConfig(), "heartbeat")) return;
        long tick = Sys.currentTick();
        for (Player player : World.getPlayers()) {
            try {
                Dimension dimension = player.dimension();
                List<Entity> mobs;
                try { mobs = dimension.getEntities(Opts.EntityQuery.type("duskwalker:duskwalker").near(player.location(), 40)); } catch (Throwable e) { continue; }
                if (mobs == null || mobs.isEmpty()) continue;
                double nearest = Double.POSITIVE_INFINITY;
                for (Entity m : mobs) {
                    try {
                        Vec3 pl = player.location(), ml = m.location();
                        double d = Math.sqrt((pl.x() - ml.x()) * (pl.x() - ml.x()) + (pl.y() - ml.y()) * (pl.y() - ml.y()) + (pl.z() - ml.z()) * (pl.z() - ml.z()));
                        if (d < nearest) nearest = d;
                    } catch (Throwable e) { /* yut */ }
                }
                if (nearest == Double.POSITIVE_INFINITY) continue;
                double beatGap = Math.max(7, Math.round(nearest * 1.1));
                double last = dn(player.getDynamicProperty(HEARTBEAT_LAST), 0);
                if (tick - last < beatGap) continue;
                player.setDynamicProperty(HEARTBEAT_LAST, (double) tick);
                double vol = Math.max(0.12, Math.min(0.9, 1 - nearest / 40));
                try { player.playSound("note.bass", Opts.Sound.of(vol, 0.4)); } catch (Throwable e) { /* yut */ }
            } catch (Throwable e) { /* yut */ }
        }
    }

    private static void directorTick() {
        if (isDefeated()) return;
        long tick = Sys.currentTick();
        Map<String, Object> cfg = getConfig();
        double intensity = getIntensity(cfg);
        Set<String> unlocked = getUnlockedEventSet(getDay());
        List<Player> players = World.getPlayers();

        for (Player player : players) {
            try {
                // OZELLIK 5: yalniz kalan oyuncu daha sik hedeflenir (coklu oyuncu)
                double isolationMult = 1;
                if (players.size() > 1) {
                    double nearest = Double.POSITIVE_INFINITY;
                    for (Player other : players) {
                        if (other.id().equals(player.id())) continue;
                        try {
                            if (!other.dimension().id().equals(player.dimension().id())) continue;
                            double d = Math.hypot(player.location().x() - other.location().x(), player.location().z() - other.location().z());
                            if (d < nearest) nearest = d;
                        } catch (Throwable e) { /* yut */ }
                    }
                    if (nearest > 32) isolationMult = 0.65; // yalniz -> aralik kisalir (daha sik)
                    else if (nearest < 12) isolationMult = 1.3; // yaninda biri var -> daha nadir
                }

                // GORUNUR olaylar: gun1'de ort. ~15-25 dk, gun50'de ort. ~3-6 dk
                Object nv = player.getDynamicProperty(NEXT_VISIBLE);
                if (nv == null) player.setDynamicProperty(NEXT_VISIBLE, (double) (tick + randInt(6 * TICKS_PER_MIN, 12 * TICKS_PER_MIN)));
                else if (tick >= dn(nv, 0)) {
                    double baseGap = lerp(20 * TICKS_PER_MIN, 4 * TICKS_PER_MIN, intensity) * isolationMult;
                    player.setDynamicProperty(NEXT_VISIBLE, (double) (tick + jitter(baseGap, 0.4)));

                    // Config + FAZ: kapatilan / henuz acilmamis olay turlerini havuzdan cikar
                    List<Ev> pool = new ArrayList<>();
                    for (Ev e : VISIBLE_EVENTS) if (!cfgIsFalse(cfg, e.type) && unlocked.contains(e.type)) pool.add(e);

                    // OZELLIK 4: kamp kuruyorsa kovalama olasiligini gecici artir
                    Object campingUntil = player.getDynamicProperty(CAMPING_UNTIL);
                    if (campingUntil instanceof Number cu && tick < cu.doubleValue()) {
                        List<Ev> boosted = new ArrayList<>();
                        for (Ev e : pool) boosted.add((e.type.equals("hunt") || e.type.equals("huntfast")) ? new Ev(e.type, e.weight * 3, e.min) : e);
                        pool = boosted;
                    }

                    String type = pickFromPool(pool, intensity);
                    if ("stalk".equals(type)) doStalk(player);
                    else if ("jumpscare".equals(type)) doJumpscare(player);
                    else if ("quickstrike".equals(type)) doQuickStrike(player);
                    else if ("hunt".equals(type)) MainC.doHunt(player, false);
                    else if ("huntfast".equals(type)) MainC.doHunt(player, true);
                    else if ("twinhunt".equals(type)) MainC.doTwinHunt(player);
                }

                // GORUNMEZ (ambiyans) olaylar
                Object na = player.getDynamicProperty(NEXT_AMBIENT);
                if (na == null) player.setDynamicProperty(NEXT_AMBIENT, (double) (tick + randInt(3 * TICKS_PER_MIN, 6 * TICKS_PER_MIN)));
                else if (tick >= dn(na, 0)) {
                    double baseGap = lerp(8 * TICKS_PER_MIN, 2 * TICKS_PER_MIN, intensity) * isolationMult;
                    player.setDynamicProperty(NEXT_AMBIENT, (double) (tick + jitter(baseGap, 0.4)));

                    List<Ev> pool = new ArrayList<>();
                    for (Ev e : AMBIENT_EVENTS) if (!cfgIsFalse(cfg, e.type) && unlocked.contains(e.type)) pool.add(e);

                    // OZELLIK 4: cok mesale yakip "sinirlendirdiyse" mesale olasiligini gecici artir
                    Object annoyedUntil = player.getDynamicProperty(ANNOYED_UNTIL);
                    if (annoyedUntil instanceof Number au && tick < au.doubleValue()) {
                        List<Ev> boosted = new ArrayList<>();
                        for (Ev e : pool) boosted.add(e.type.equals("torch") ? new Ev(e.type, e.weight * 3, e.min) : e);
                        pool = boosted;
                    }

                    String type = pickFromPool(pool, intensity);
                    if ("whisper".equals(type)) MainC.doWhisper(player);
                    else if ("chat".equals(type)) MainC.doChat(player);
                    else if ("sign".equals(type)) MainC.doSign(player);
                    else if ("torch".equals(type)) MainC.doTorch(player);
                    else if ("door".equals(type)) MainC.doDoor(player);
                    else if ("possession".equals(type)) MainC.doPossession(player);
                    else if ("reflection".equals(type)) MainC.doReflection(player);
                }
            } catch (Throwable e) { /* yut */ }
        }
    }

    // ---- Stalk: yakin veya uzak, dururken izler, biz ona bakinca kaybolur ----
    private static final class StalkLoop implements Runnable {
        final Player player; final Entity entity; final Dimension dimension;
        int elapsed = 0, noticedTicks = 0;
        static final int MAX_ELAPSED = 500, STEP = 10;
        StalkLoop(Player p, Entity e, Dimension d) { player = p; entity = e; dimension = d; }

        @Override public void run() {
            elapsed += STEP;
            boolean alive = false;
            try { alive = entity.isValid(); } catch (Throwable e) { /* yut */ }
            if (!alive) return;
            try {
                Vec3 eye;
                try { eye = player.getHeadLocation(); } catch (Throwable e) { eye = new Vec3(player.location().x(), player.location().y() + 1.62, player.location().z()); }
                double tx = entity.location().x() - eye.x(), ty = entity.location().y() + 0.9 - eye.y(), tz = entity.location().z() - eye.z();
                double len = Math.sqrt(tx * tx + ty * ty + tz * tz);
                if (len == 0) len = 1;
                double dx = tx / len, dy = ty / len, dz = tz / len;
                Vec3 pv = player.getViewDirection();
                double dot = dx * pv.x() + dy * pv.y() + dz * pv.z();
                if (dot > 0.82) noticedTicks += STEP;
            } catch (Throwable e) { /* yut */ }

            if (noticedTicks >= 60 || elapsed >= MAX_ELAPSED) {
                try { smokePuff(dimension, entity.location(), 8); } catch (Throwable e) { /* yut */ }
                try { entity.triggerEvent("duskwalker:vanish"); } catch (Throwable e) { /* yut */ }
                return;
            }
            Sys.runTimeout(this, STEP);
        }
    }

    public static void doStalk(Player player) {
        try {
            Dimension dimension = player.dimension();
            boolean near = Math.random() < 0.5;
            double minD = near ? 4 : 16, maxD = near ? 9 : 30;
            Vec3 view = player.getViewDirection();
            double dist = minD + Math.random() * (maxD - minD);
            Vec3 center = new Vec3(player.location().x() + view.x() * dist, player.location().y(), player.location().z() + view.z() * dist);
            Vec3 pos = findSpawnSpot(dimension, center, 0, 2);
            Entity entity;
            try { entity = dimension.spawnEntity("duskwalker:duskwalker", pos); } catch (Throwable e) { return; }
            applyDominant(entity);
            int stalkVariant = applyVariant(entity);
            corruptGround(dimension, pos);
            loseSanity(player, 3);
            try { player.playSound("mob.endermen.stare", Opts.Sound.of(near ? 0.7 : 0.4, 0.5 * getVariantPitch(stalkVariant))); } catch (Throwable e) { /* yut */ }

            StalkLoop loop = new StalkLoop(player, entity, dimension);
            Sys.runTimeout(loop, StalkLoop.STEP);
        } catch (Throwable e) { /* yut */ }
    }

    // ---- Jumpscare: cok yakin, ani, gurultulu, ekran flasi + isinlanip kaybolma ----
    public static void doJumpscare(Player player) {
        try {
            Dimension dimension = player.dimension();
            Vec3 eye;
            try { eye = player.getHeadLocation(); } catch (Throwable e) { eye = new Vec3(player.location().x(), player.location().y() + 1.62, player.location().z()); }
            Vec3 view = player.getViewDirection();
            Vec3 center = new Vec3(eye.x() + view.x() * 1.0, player.location().y(), eye.z() + view.z() * 1.0);
            Vec3 pos = findSpawnSpot(dimension, center, 0, 0.4);
            final Entity entity;
            try { entity = dimension.spawnEntity("duskwalker:duskwalker", pos); } catch (Throwable e) { return; }
            applyDominant(entity);
            int jumpVariant = applyVariant(entity);
            corruptGround(dimension, pos);
            loseSanity(player, 18);

            final double jp = getVariantPitch(jumpVariant);
            try { player.playSound("mob.wither.spawn", Opts.Sound.of(1.0, 0.3 * jp)); } catch (Throwable e) { /* yut */ }
            try { player.playSound("mob.endermen.stare", Opts.Sound.of(1.0, 0.35 * jp)); } catch (Throwable e) { /* yut */ }
            try { player.playSound("mob.ghast.scream", Opts.Sound.of(0.7, 1.6 * jp)); } catch (Throwable e) { /* yut */ }

            // Ekran flasi: once kirmizi carpma hissi, sonra siyaha ani gecis (renk degerleri JS'teki gibi birakildi)
            try { player.cameraFade(Opts.Fade.of(120, 5, 5, 0.01, 0.05, 0.05)); } catch (Throwable e) { /* yut */ }
            Sys.runTimeout(() -> {
                try { player.cameraFade(Opts.Fade.of(0, 0, 0, 0.02, 0.18, 0.45)); } catch (Throwable e) { /* yut */ }
            }, 3);

            try { player.addEffect("blindness", 12, 0, false); } catch (Throwable e) { /* yut */ }

            try { player.setTitle("§4§l. . .", Opts.Title.of(0, 10, 8, "§7seni goruyor§r")); } catch (Throwable e) { /* yut */ }

            try { player.cameraShake(1.8, 1.0, "positional"); } catch (Throwable e) { /* yut */ }

            Sys.runTimeout(() -> {
                try { if (entity.isValid()) entity.playAnimation("animation.duskwalker.attack"); } catch (Throwable e) { /* yut */ }
            }, 2);

            Sys.runTimeout(() -> {
                try { player.playSound("mob.endermen.portal", Opts.Sound.of(0.8, 1.3 * jp)); } catch (Throwable e) { /* yut */ }
                try { smokePuff(dimension, entity.location(), 8); } catch (Throwable e) { /* yut */ }
                try { entity.triggerEvent("duskwalker:vanish"); } catch (Throwable e) { /* yut */ }
            }, 22);
        } catch (Throwable e) { /* yut */ }
    }

    // ---- Quick strike: yaklasir, TEK vurur, dumanla kaybolur ----
    public static void doQuickStrike(Player player) {
        try {
            Dimension dimension = player.dimension();
            double angle = Math.random() * Math.PI * 2;
            Vec3 center = new Vec3(player.location().x() + Math.cos(angle) * 6, player.location().y(), player.location().z() + Math.sin(angle) * 6);
            final Vec3 spawnPos = findSpawnSpot(dimension, center, 0, 2);
            final int strikeVariant = 0;
            smokePuff(dimension, spawnPos, 8);
            try { dimension.playSound("mob.wither.spawn", spawnPos, Opts.Sound.of(0.9, 0.7 * getVariantPitch(strikeVariant))); } catch (Throwable e) { /* yut */ }
            Sys.runTimeout(() -> {
                final Entity entity;
                try { entity = dimension.spawnEntity("duskwalker:duskwalker", spawnPos); } catch (Throwable e) { return; }
                applyDominant(entity);
                applyVariant(entity, strikeVariant);
                corruptGround(dimension, spawnPos);
                loseSanity(player, 8);
                addEvidence(player, 1);
                Sys.runTimeout(() -> {
                    try { if (entity.isValid()) entity.playAnimation("animation.duskwalker.attack"); } catch (Throwable e) { /* yut */ }
                    Sys.runTimeout(() -> {
                        try {
                            if (player.isValid()) {
                                player.applyDamage(getVariantStrikeDamage(strikeVariant), Opts.Damage.of("entityAttack", entity));
                            }
                        } catch (Throwable e) { /* yut */ }
                        try { player.sendMessage("§4Duskwalker§r: Bu kadar kolay kacamazsin."); } catch (Throwable e) { /* yut */ }
                        Sys.runTimeout(() -> {
                            try { smokePuff(dimension, entity.location(), 8); } catch (Throwable e) { /* yut */ }
                            try { entity.triggerEvent("duskwalker:vanish"); } catch (Throwable e) { /* yut */ }
                        }, 20);
                    }, 14);
                }, 16);
            }, 20);
        } catch (Throwable e) { /* yut */ }
    }

    // ---- Hunt bitis geri cagrilari: avci id -> geri cagri(oyuncu oldu mu). MainC doldurur/temizler. ----
    public static final Map<String, Consumer<Boolean>> huntEndCallbacks = new HashMap<>();

    // ================================================================== KAYIT
    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        initialized = true;

        // kacirma tetigi (satir 1399)
        Sys.runInterval(MainB::abductionTick, 20);

        // OZELLIK 4: mesale sayaci (satir 1433)
        try {
            Events.After.playerPlaceBlock.subscribe(ev -> {
                try {
                    if (ev.block != null && ev.block.typeId() != null && ev.block.typeId().contains("torch")) {
                        Player p = ev.player;
                        double c = dn(p.getDynamicProperty(TORCH_COUNT), 0) + 1;
                        if (c >= 15) {
                            c = 0;
                            p.setDynamicProperty(ANNOYED_UNTIL, (double) (Sys.currentTick() + 3 * TICKS_PER_MIN));
                            try { playerNotifyAnnoyed(p); } catch (Throwable e) { /* yut */ }
                        }
                        p.setDynamicProperty(TORCH_COUNT, c);
                    }
                } catch (Throwable e) { /* yut */ }
            });
        } catch (Throwable e) { /* yut */ }

        // kamp tespiti (satir 1454)
        Sys.runInterval(MainB::campingTick, 200);

        // hikaye (satir 1477)
        Sys.runTimeout(() -> {
            try {
                if (!truthy(World.getDynamicProperty(STORY_SHOWN))) {
                    World.setDynamicProperty(STORY_SHOWN, true);
                    sendLinesSpaced(STORY_LINES, 0, 70);
                }
            } catch (Throwable e) { /* yut */ }
        }, 60);

        // kilometre tasi mesajlari (satir 1489)
        Sys.runInterval(MainB::milestoneTick, 100);

        // test komutlari (satir 1512)
        try {
            Events.Before.chatSend.subscribe(ev -> {
                String msg = ev.message;
                if (msg.equals("!dwconfig") || msg.indexOf("!dwconfig ") == 0) {
                    ev.cancel = true;
                    Player player = ev.sender;
                    Sys.run(() -> { try { handleConfigCommand(player, msg); } catch (Throwable e) { /* yut */ } });
                    return;
                }
                if (!CMDS.contains(msg)) return;
                ev.cancel = true;
                Player player = ev.sender;
                Sys.run(() -> runTestCommand(player, msg));
            });
        } catch (Throwable e) { /* yut */ }

        // kurt intikami tarayicisi (satir 1677)
        Sys.runInterval(wolfScanner, 20);

        // yaratik davranislari (satir 1738, 1802)
        registerCreatureBehavior("duskwalker:seep", MainB::seepBehavior);
        registerCreatureBehavior("duskwalker:watcher", MainB::watcherBehavior);

        // Seep / Watcher tetikleyici taramasi (satir 1910)
        Sys.runInterval(seepWatcherScanner, 20);

        // kurtla etkilesim: donusumu erteler (satir 1965)
        try {
            Events.After.playerInteractWithEntity.subscribe(ev -> {
                try {
                    Entity target = ev.target;
                    if (target == null || !"minecraft:wolf".equals(target.typeId())) return;
                    boolean warned = false;
                    try { warned = truthy(target.getDynamicProperty(WOLF_WARNED)); } catch (Throwable e) { return; }
                    boolean triggered = false;
                    try { triggered = truthy(target.getDynamicProperty(TRIGGERED)); } catch (Throwable e) { /* yut */ }
                    if (!warned || triggered) return;
                    target.setDynamicProperty(WOLF_WARNED, false);
                    target.setDynamicProperty(TAMED_AT, (double) Sys.currentTick());
                    target.setDynamicProperty(DELAY, (double) randInt(2400, 3600));
                    try { ev.player.sendMessage("§7Kurdunu sakinlestirdin... simdilik.§r"); } catch (Throwable e) { /* yut */ }
                    try { target.dimension().playSound("mob.wolf.growl", target.location(), Opts.Sound.of(0.5, 1.15)); } catch (Throwable e) { /* yut */ }
                } catch (Throwable e) { /* yut */ }
            });
        } catch (Throwable e) { /* yut */ }

        // kalp atisi (satir 2030)
        Sys.runInterval(MainB::heartbeatTick, 5);

        // ana olay zamanlayicisi (satir 2058)
        Sys.runInterval(MainB::directorTick, 20);

        // Hunt sonu: oyuncu bir Duskwalker tarafindan oldurulunce geri cagri (satir 2289)
        try {
            Events.After.entityDie.subscribe(ev -> {
                try {
                    Entity dmg = ev.damageSource != null ? ev.damageSource.damagingEntity : null;
                    if (dmg != null && "duskwalker:duskwalker".equals(dmg.typeId()) && ev.deadEntity != null && "minecraft:player".equals(ev.deadEntity.typeId())) {
                        Consumer<Boolean> cb = huntEndCallbacks.get(dmg.id());
                        if (cb != null) {
                            try { World.sendMessage("§4Duskwalker§r: Sonunda..."); } catch (Throwable e) { /* yut */ }
                            cb.accept(true);
                        }
                    }
                } catch (Throwable e) { /* yut */ }
            });
        } catch (Throwable e) { /* yut */ }
    }
}

package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.*;

import static com.duskwalker.mod.Core.*;

/**
 * DUSKWALKER v11 - BOS EV / The Hollow House (house.js).
 * 12. gunden sonra dunyada BIR TANE buyuk terk edilmis ev belirir; ilk giriste kisa bir korku dizisi yasanir.
 */
public final class House {
    private House() {}

    private static final String HOUSE_KEY = "duskwalker:v11house";
    private static final int W = 11, D = 9, H = 4;
    private static boolean initialized = false;
    private static boolean building = false;

    private static void put(Dimension dim, double x, double y, double z, String type) {
        try { Block b = dim.getBlock(new Vec3(x, y, z)); if (b != null) b.setType(type); } catch (Throwable e) { /* yut */ }
    }

    private static void sign(Dimension dim, double x, double y, double z, String text) {
        try {
            Block b = dim.getBlock(new Vec3(x, y, z));
            b.setType("minecraft:standing_sign");
            b.setSignText(text);
        } catch (Throwable e) { /* yut */ }
    }

    private static ItemStack noteItem(String name, String[] lines) {
        ItemStack it = new ItemStack("minecraft:paper", 1);
        it.nameTag = name;
        it.setLore(Arrays.asList(lines));
        return it;
    }

    private static void chestWith(Dimension dim, double x, double y, double z, List<ItemStack> items) {
        try {
            Block b = dim.getBlock(new Vec3(x, y, z));
            b.setType("minecraft:chest");
            Container inv = b.container();
            if (inv == null) return;
            for (ItemStack it : items) { try { inv.addItem(it); } catch (Throwable e) { /* yut */ } }
        } catch (Throwable e) { /* yut */ }
    }

    private static Integer surfaceY(Dimension dim, int x, int z) {
        Block top = safe(() -> dim.getTopmostBlock(x, z), (Block) null);
        if (top == null) return null;
        String id = top.typeId();
        if (id.contains("water") || id.contains("lava") || id.contains("ice")) return null;
        return top.y();
    }

    /** Ev alani icin uygun zemin: en fazla 3 blok fark. Dondurdugu: zemin yuksekligi (en yuksek nokta + 1); uygun degilse null */
    private static Integer houseSpot(Dimension dim, int x0, int z0) {
        int lo = 9999, hi = -9999;
        for (int x = 0; x < W; x += 2) for (int z = 0; z < D; z += 2) {
            Integer y = surfaceY(dim, x0 + x, z0 + z);
            if (y == null) return null;
            lo = Math.min(lo, y); hi = Math.max(hi, y);
        }
        if (hi - lo > 3) return null;
        return hi + 1;
    }

    // ---------------------------------------------------------
    //  Insa (katman katman, her katman ayri tikte)
    // ---------------------------------------------------------
    private static void build(Dimension dim, int x0, int y0, int z0) {
        List<Runnable> layers = new ArrayList<>();

        // 0: temizlik + temel
        layers.add(() -> {
            for (int x = -1; x <= W; x++) for (int z = -1; z <= D; z++) {
                for (int y = 0; y <= H + 3; y++) put(dim, x0 + x, y0 + y, z0 + z, "minecraft:air");
                put(dim, x0 + x, y0 - 2, z0 + z, "minecraft:cobblestone");
                put(dim, x0 + x, y0 - 3, z0 + z, "minecraft:cobblestone");
            }
        });

        // 1: zemin
        layers.add(() -> {
            for (int x = 0; x < W; x++) for (int z = 0; z < D; z++) put(dim, x0 + x, y0 - 1, z0 + z, "minecraft:spruce_planks");
        });

        // 2: duvarlar + koseler
        layers.add(() -> {
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) for (int z = 0; z < D; z++) {
                    boolean edge = x == 0 || z == 0 || x == W - 1 || z == D - 1;
                    if (!edge) continue;
                    boolean corner = (x == 0 || x == W - 1) && (z == 0 || z == D - 1);
                    put(dim, x0 + x, y0 + y, z0 + z, corner ? "minecraft:stripped_dark_oak_log" : "minecraft:dark_oak_planks");
                }
            }
        });

        // 3: kapi boslugu, pencereler, ara duvar
        layers.add(() -> {
            put(dim, x0 + 5, y0, z0, "minecraft:air");
            put(dim, x0 + 5, y0 + 1, z0, "minecraft:air");
            int[][] windows = { { 2, 0 }, { 8, 0 }, { 0, 3 }, { 0, 6 }, { W - 1, 3 }, { W - 1, 6 }, { 3, D - 1 }, { 7, D - 1 } };
            for (int[] w : windows) { put(dim, x0 + w[0], y0 + 1, z0 + w[1], "minecraft:glass_pane"); put(dim, x0 + w[0], y0 + 2, z0 + w[1], "minecraft:glass_pane"); }
            for (int z = 1; z < D - 1; z++) for (int y = 0; y < H; y++) put(dim, x0 + 6, y0 + y, z0 + z, "minecraft:dark_oak_planks");
            put(dim, x0 + 6, y0, z0 + 4, "minecraft:air");
            put(dim, x0 + 6, y0 + 1, z0 + 4, "minecraft:air");
        });

        // 4: tavan + cati kenari
        layers.add(() -> {
            for (int x = -1; x <= W; x++) for (int z = -1; z <= D; z++) {
                boolean inside = x >= 0 && x < W && z >= 0 && z < D;
                put(dim, x0 + x, y0 + H, z0 + z, inside ? "minecraft:spruce_planks" : "minecraft:dark_oak_slab");
            }
        });

        // 5: icerik
        layers.add(() -> {
            // yatak odasi (sol): hali, sandik, tabela
            for (int x = 2; x <= 4; x++) for (int z = 2; z <= 4; z++) put(dim, x0 + x, y0, z0 + z, "minecraft:white_carpet");
            sign(dim, x0 + 3, y0, z0 + D - 3, tx("BURADA\nUYU", "SLEEP\nHERE"));
            chestWith(dim, x0 + 1, y0, z0 + D - 2, List.of(
                noteItem(tx("Aile Gunlugu - 1", "Family Journal - 1"), new String[] { tx("§7Buraya kacmistik.", "§7We came here to escape."), tx("§7Ama o bizi buldu.", "§7But it found us.") }),
                new ItemStack("minecraft:torch", randInt(10, 16)),
                new ItemStack("minecraft:bread", randInt(3, 6))
            ));
            // calisma odasi (sag): kitapliklar, masa, fener, sandik
            for (int z = 2; z <= 6; z++) { put(dim, x0 + 9, y0, z0 + z, "minecraft:bookshelf"); put(dim, x0 + 9, y0 + 1, z0 + z, "minecraft:bookshelf"); }
            put(dim, x0 + 8, y0, z0 + 1, "minecraft:crafting_table");
            put(dim, x0 + 8, y0 + 1, z0 + 1, "minecraft:soul_lantern");
            chestWith(dim, x0 + 7, y0, z0 + D - 2, List.of(
                noteItem(tx("Aile Gunlugu - 2", "Family Journal - 2"), new String[] { tx("§7Cocuklar artik uyumuyor.", "§7The children have stopped sleeping."), tx("§7Pencereye bakiyorlar.", "§7They stare at the window.") }),
                noteItem(tx("Aile Gunlugu - 3", "Family Journal - 3"), new String[] { tx("§7Bunu okuyan:", "§7Whoever reads this:"), tx("§7Kapi icerden hic kilitlenmedi.", "§7The door was never locked from inside.") }),
                Extras.randomLetter(),
                new ItemStack("minecraft:golden_apple", 1),
                new ItemStack("minecraft:torch", randInt(8, 14))
            ));
            // orumcek aglari
            put(dim, x0 + 1, y0 + 3, z0 + 1, "minecraft:web");
            put(dim, x0 + W - 2, y0 + 3, z0 + D - 2, "minecraft:web");
            put(dim, x0 + 5, y0 + 3, z0 + 3, "minecraft:web");
            // girise tabela
            sign(dim, x0 + 5, y0, z0 - 2, tx("BOS EV\nGIRME", "THE HOLLOW\nHOUSE"));
        });

        for (int i = 0; i < layers.size(); i++) {
            Runnable fn = layers.get(i);
            Sys.runTimeout(() -> safe(fn), 4 + i * 6);
        }
    }

    private static Map<String, Object> getHouse() { return Json.asMap(worldGet(HOUSE_KEY, null)); }

    private static void saveHouse(int x, int y, int z, String d) {
        Map<String, Object> m = Json.newMap();
        m.put("x", (double) x); m.put("y", (double) y); m.put("z", (double) z); m.put("d", d);
        worldSet(HOUSE_KEY, m);
    }

    // ---------------------------------------------------------
    //  Ilk giris dizisi
    // ---------------------------------------------------------
    private static boolean inside(Map<String, Object> h, Vec3 loc) {
        double hx = Json.num(h.get("x"), 0), hy = Json.num(h.get("y"), 0), hz = Json.num(h.get("z"), 0);
        return loc.x() >= hx && loc.x() <= hx + W && loc.z() >= hz && loc.z() <= hz + D && loc.y() >= hy - 1 && loc.y() <= hy + H + 1;
    }

    private static void haunt(Player player, Map<String, Object> h) {
        Dimension dim = player.dimension();
        double hx = Json.num(h.get("x"), 0), hy = Json.num(h.get("y"), 0), hz = Json.num(h.get("z"), 0);
        title(player, tx("§8Bos Ev", "§8The Hollow House"), tx("§7kimse tek basina girmemeli", "§7no one should enter alone"), 70);
        addStat(player, "houseVisits");
        for (int i = 0; i < 3; i++) {
            Sys.runTimeout(() -> {
                if (isValid(player)) playTo(player, pick("ambient.cave", "mob.endermen.idle", "mob.ghast.moan"), 0.7, 0.55);
            }, 40 + i * 50);
        }
        // 6 sn: calisma odasindaki fener soner
        Sys.runTimeout(() -> {
            put(dim, hx + 8, hy + 1, hz + 1, "minecraft:air");
            if (isValid(player)) playTo(player, "random.fizz", 0.7, 0.6);
        }, 120);
        // 10 sn: pencerede siluet
        Sys.runTimeout(() -> {
            if (!isValid(player)) return;
            Entity st = spawnStatue(dim, new Vec3(hx - 1.5, hy, hz + 3.5));
            if (st == null) return;
            faceToward(st, eyePos(player));
            Sys.runTimeout(() -> despawn(st, true), 36);
        }, 200);
        // 14 sn: kapi vurusu
        Sys.runTimeout(() -> {
            if (!isValid(player)) return;
            playAt(dim, "close.wooden_door", new Vec3(hx + 5, hy, hz), 1.6, 0.6);
            loseSanity(player, 12);
            addEvidence(player, 2);
        }, 280);
    }

    public static void init() {
        if (initialized) return;
        initialized = true;

        // Yonetici
        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("house") || getDay() < 12 || building) return;
            if (worldGet(HOUSE_KEY, null) != null) return;
            for (Player p : World.getPlayers()) {
                try {
                    if (!isSurvival(p) || !p.dimension().id().equals("minecraft:overworld") || isUnderground(p) || realm.contains(p.id())) continue;
                    if (!chance(0.4)) continue;
                    Vec3 l = p.location();
                    for (int attempt = 0; attempt < 8; attempt++) {
                        double a = Math.random() * Math.PI * 2;
                        double d = 38 + Math.random() * 14;
                        int x0 = (int) Math.floor(l.x() + Math.cos(a) * d) - (W / 2);
                        int z0 = (int) Math.floor(l.z() + Math.sin(a) * d) - (D / 2);
                        Integer y0 = houseSpot(p.dimension(), x0, z0);
                        if (y0 == null) continue;
                        building = true;
                        build(p.dimension(), x0, y0, z0);
                        saveHouse(x0, y0, z0, p.dimension().id());
                        Sys.runTimeout(() -> { building = false; }, 60);
                        return;
                    }
                } catch (Throwable e) { /* yut */ }
            }
        }, TICKS_PER_MIN);

        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("house")) return;
            Map<String, Object> h = getHouse();
            if (h == null) return;
            for (Player p : World.getPlayers()) {
                try {
                    if (!p.dimension().id().equals(h.get("d")) || !inside(h, p.location())) continue;
                    boolean seen = Boolean.TRUE.equals(playerGet(p, "duskwalker:v11houseSeen", false));
                    if (seen) continue;
                    playerSet(p, "duskwalker:v11houseSeen", true);
                    haunt(p, h);
                } catch (Throwable e) { /* yut */ }
            }
        }, 20);

        // Test komutu: !house -> oyuncunun onune evi kurar
        Events.Before.chatSend.subscribe(ev -> {
            if (!ev.message.equals("!house")) return;
            ev.cancel = true;
            Player p = ev.sender;
            Sys.run(() -> {
                try {
                    Vec3 v = p.getViewDirection();
                    int x0 = (int) Math.floor(p.location().x() + v.x() * 14) - (W / 2);
                    int z0 = (int) Math.floor(p.location().z() + v.z() * 14) - (D / 2);
                    int y0 = (int) Math.floor(p.location().y());
                    build(p.dimension(), x0, y0, z0);
                    saveHouse(x0, y0, z0, p.dimension().id());
                    say(p, "§7house @ " + x0 + " " + y0 + " " + z0 + "§r");
                } catch (Throwable e) { /* yut */ }
            });
        });
    }
}

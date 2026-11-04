package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.*;

import static com.duskwalker.mod.Core.*;

/**
 * DUSKWALKER v11 - TERK EDILMIS YERLER / harabeler (ruins.js).
 * Dunya basina en fazla 10 harabe; bes cesit: camp, shrine, effigies, watchpost, well
 * (Ruins2 alti tane daha ekler). Harabeler dunya ozelligi olarak kaydedilir.
 */
public final class Ruins {
    private Ruins() {}

    public static final String RUIN_KEY = "duskwalker:v11ruins";
    private static final int MAX_RUINS = 10;
    private static boolean initialized = false;

    // ---------------------------------------------------------
    //  Yardimcilar
    // ---------------------------------------------------------
    public static boolean put(Dimension dim, double x, double y, double z, String type) {
        try { Block b = dim.getBlock(new Vec3(x, y, z)); if (b != null) b.setType(type); return true; } catch (Throwable e) { return false; }
    }

    public static void sign(Dimension dim, double x, double y, double z, String text) {
        try {
            Block b = dim.getBlock(new Vec3(x, y, z));
            b.setType("minecraft:standing_sign");
            b.setSignText(text);
        } catch (Throwable e) { /* yut */ }
    }

    public static ItemStack noteItem(String name, List<String> lines) {
        ItemStack it = new ItemStack("minecraft:paper", 1);
        it.nameTag = name;
        it.setLore(lines);
        return it;
    }
    public static ItemStack noteItem(String name, String... lines) { return noteItem(name, Arrays.asList(lines)); }

    public static void chestWith(Dimension dim, double x, double y, double z, List<ItemStack> items) {
        try {
            Block b = dim.getBlock(new Vec3(x, y, z));
            b.setType("minecraft:chest");
            Container inv = b.container();
            if (inv == null) return;
            for (ItemStack it : items) { try { inv.addItem(it); } catch (Throwable e) { /* yut */ } }
        } catch (Throwable e) { /* yut */ }
    }
    public static void chestWith(Dimension dim, double x, double y, double z, ItemStack... items) { chestWith(dim, x, y, z, Arrays.asList(items)); }

    private static Integer surfaceY(Dimension dim, int x, int z) {
        Block top = safe(() -> dim.getTopmostBlock(x, z), (Block) null);
        if (top == null) return null;
        String id = top.typeId();
        if (id.contains("water") || id.contains("lava") || id.contains("leaves") || id.contains("log") || id.contains("ice")) return null;
        return top.y();
    }

    /** Duz bir alan mi? (yaricap r, en fazla 'tol' blok fark). Dondurdugu: zemin ustu y; uygun degilse null */
    private static Integer flatSpot(Dimension dim, int cx, int cz, int r, int tol) {
        int lo = 9999, hi = -9999;
        for (int x = -r; x <= r; x += 2) {
            for (int z = -r; z <= r; z += 2) {
                Integer y = surfaceY(dim, cx + x, cz + z);
                if (y == null) return null;
                lo = Math.min(lo, y); hi = Math.max(hi, y);
            }
        }
        if (hi - lo > tol) return null;
        return hi + 1;
    }

    public static void clearVolume(Dimension dim, double x0, double y0, double z0, int w, int h, int d) {
        for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) for (int z = 0; z < d; z++) put(dim, x0 + x, y0 + y, z0 + z, "minecraft:air");
    }

    // ---------------------------------------------------------
    //  Notlar
    // ---------------------------------------------------------
    private static final String[][] NOTES = {
        { "Hayatta Kalanin Notu", "Survivor's Note", "§7Uc gece dayandik.|§7Dorduncude mesaleler bitti.", "§7We lasted three nights.|§7On the fourth, the torches ran out." },
        { "Yirtik Sayfa", "Torn Page", "§7Sakin kipirdama.|§7Sakin arkana bakma.", "§7Never stand still.|§7Never look behind you." },
        { "Son Mesaj", "Last Message", "§7Isik varsa, kal.|§7Yoksa kos.", "§7If there's light, stay.|§7If not, run." },
        { "Karalanmis Not", "Scribbled Note", "§7O benim adimi biliyor.|§7Nereden?", "§7It knows my name.|§7How?" }
    };

    public static ItemStack randomNote() {
        if (chance(0.5)) return Extras.randomLetter();
        String[] n = pick(NOTES);
        return noteItem(tx(n[0], n[1]), tx(n[2], n[3]).split("\\|"));
    }

    // ---------------------------------------------------------
    //  Uretecler: (dim, x, y, z) = zeminin USTU, merkez nokta
    // ---------------------------------------------------------
    public interface Generator { void generate(Dimension dim, double x, double y, double z, double day); }
    public interface IntGenerator { void generate(Dimension dim, int x, int y, int z, int day); }

    /** Tam sayili uretici lambdasini Generator'a sarar. */
    public static Generator gen(IntGenerator g) {
        return (dim, x, y, z, day) -> g.generate(dim, (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z), (int) day);
    }

    /** Eklenme sirasi korunur (JS Object.keys sirasi). */
    public static final Map<String, Generator> GENERATORS = new LinkedHashMap<>();

    public static final Map<String, String[]> TYPE_NAMES = new LinkedHashMap<>();

    static {
        GENERATORS.put("camp", gen((dim, x, y, z, day) -> {
            clearVolume(dim, x - 4, y, z - 4, 9, 5, 9);
            // cadir (x: 0..3, z: -2..2) : ust kabuk + arka duvar
            int[] profile = { 1, 2, 3, 2, 1 };
            for (int dz = 0; dz < 5; dz++) {
                int h = profile[dz];
                for (int dx = 0; dx < 4; dx++) {
                    put(dim, x + dx, y + h - 1, z - 2 + dz, "minecraft:brown_wool");
                    if (dx == 3) for (int k = 0; k < h; k++) put(dim, x + dx, y + k, z - 2 + dz, "minecraft:brown_wool");
                }
            }
            put(dim, x + 1, y, z, "minecraft:white_carpet");
            chestWith(dim, x + 2, y, z, randomNote(), new ItemStack("minecraft:torch", randInt(6, 14)), new ItemStack("minecraft:bread", randInt(2, 5)));
            put(dim, x - 2, y, z, "minecraft:campfire");
            sign(dim, x - 2, y, z + 2, tx("TERK KAMP\nGUN " + day, "ABANDONED\nCAMP DAY " + day));
        }));

        GENERATORS.put("shrine", gen((dim, x, y, z, day) -> {
            clearVolume(dim, x - 3, y, z - 3, 7, 5, 7);
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) put(dim, x + dx, y - 1, z + dz, "minecraft:stone_bricks");
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) put(dim, x + dx, y, z + dz, "minecraft:stone_bricks");
            put(dim, x, y, z, "minecraft:chiseled_stone_bricks");
            put(dim, x, y + 1, z, "minecraft:soul_lantern");
            for (int[] c : new int[][] { { -2, -2 }, { 2, -2 }, { -2, 2 }, { 2, 2 } }) put(dim, x + c[0], y, z + c[1], "minecraft:candle");
            put(dim, x + 2, y, z, "minecraft:cauldron");
            sign(dim, x, y, z + 3, tx("IZLEYENE\nADAK", "OFFERINGS TO\nTHE WATCHER"));
        }));

        GENERATORS.put("effigies", gen((dim, x, y, z, day) -> {
            clearVolume(dim, x - 5, y, z - 5, 11, 5, 11);
            for (int i = 0; i < 5; i++) {
                double a = (Math.PI * 2 * i) / 5;
                int px = x + (int) Math.round(Math.cos(a) * 3), pz = z + (int) Math.round(Math.sin(a) * 3);
                for (int k = 0; k < 3; k++) put(dim, px, y + k, pz, "minecraft:spruce_fence");
                put(dim, px, y + 3, pz, "minecraft:hay_block");
                put(dim, px, y + 4, pz, "minecraft:carved_pumpkin");
            }
            sign(dim, x, y, z, tx("BAKMA", "DON'T\nLOOK"));
        }));

        GENERATORS.put("watchpost", gen((dim, x, y, z, day) -> {
            clearVolume(dim, x - 2, y, z - 2, 5, 8, 5);
            for (int dy = 0; dy < 5; dy++) {
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    boolean edge = Math.abs(dx) == 1 || Math.abs(dz) == 1;
                    if (edge) put(dim, x + dx, y + dy, z + dz, "minecraft:cobblestone");
                }
            }
            put(dim, x, y, z - 1, "minecraft:air"); put(dim, x, y + 1, z - 1, "minecraft:air"); // kapi boslugu
            for (int dy = 0; dy < 5; dy++) put(dim, x, y + dy, z, "minecraft:scaffolding"); // ici tirmanilir
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                if (!(dx == 0 && dz == 0)) put(dim, x + dx, y + 5, z + dz, "minecraft:spruce_planks");
            }
            put(dim, x, y + 5, z, "minecraft:air");
            put(dim, x + 1, y + 6, z + 1, "minecraft:lantern");
            chestWith(dim, x - 1, y + 6, z, randomNote(), new ItemStack("minecraft:golden_carrot", randInt(2, 4)), new ItemStack("minecraft:torch", randInt(8, 16)));
            sign(dim, x, y, z - 2, tx("GOZETLEME\nKULESI " + day, "WATCH POST\nNO. " + day));
        }));

        GENERATORS.put("well", gen((dim, x, y, z, day) -> {
            clearVolume(dim, x - 3, y - 2, z - 3, 7, 7, 7);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                put(dim, x + dx, y - 1, z + dz, "minecraft:cobblestone");
                if (!(dx == 0 && dz == 0)) put(dim, x + dx, y, z + dz, "minecraft:cobblestone");
            }
            put(dim, x, y - 1, z, "minecraft:water");
            put(dim, x, y, z, "minecraft:water");
            for (int[] c : new int[][] { { -1, -1 }, { 1, -1 }, { -1, 1 }, { 1, 1 } }) {
                put(dim, x + c[0], y + 1, z + c[1], "minecraft:spruce_fence");
                put(dim, x + c[0], y + 2, z + c[1], "minecraft:spruce_fence");
            }
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) put(dim, x + dx, y + 3, z + dz, "minecraft:spruce_planks");
            sign(dim, x, y, z + 2, tx("ICME", "DON'T\nDRINK"));
        }));

        TYPE_NAMES.put("camp", new String[] { "terk edilmis bir kamp", "an abandoned camp" });
        TYPE_NAMES.put("shrine", new String[] { "eski bir siginak", "an old shrine" });
        TYPE_NAMES.put("effigies", new String[] { "korkuluklardan bir daire", "a circle of effigies" });
        TYPE_NAMES.put("watchpost", new String[] { "bir gozetleme kulesi", "a watch post" });
        TYPE_NAMES.put("well", new String[] { "tuhaf bir kuyu", "a strange well" });
    }

    // ---------------------------------------------------------
    //  Yonetici: yeni harabe kurma
    // ---------------------------------------------------------
    private static final Map<String, Long> lastTry = new HashMap<>();

    private static List<Object> ruinList() {
        List<Object> l = Json.asList(worldGet(RUIN_KEY, null));
        return l != null ? l : new ArrayList<>();
    }

    private static boolean tryBuild(Player player) {
        List<Object> ruins = ruinList();
        if (ruins.size() >= MAX_RUINS) return false;
        Dimension dim = player.dimension();
        Vec3 l = player.location();
        for (int attempt = 0; attempt < 6; attempt++) {
            double a = Math.random() * Math.PI * 2;
            double d = 38 + Math.random() * 18;
            int cx = (int) Math.floor(l.x() + Math.cos(a) * d), cz = (int) Math.floor(l.z() + Math.sin(a) * d);
            boolean near = false;
            for (Object o : ruins) {
                Map<String, Object> r = Json.asMap(o);
                if (r != null && dim.id().equals(r.get("d")) && Math.hypot(Json.num(r.get("x"), 0) - cx, Json.num(r.get("z"), 0) - cz) < 70) { near = true; break; }
            }
            if (near) continue;
            Integer y = flatSpot(dim, cx, cz, 4, 1);
            if (y == null) continue;
            String type = pick(new ArrayList<>(GENERATORS.keySet()));
            GENERATORS.get(type).generate(dim, cx, y, cz, getDay());
            Map<String, Object> rec = Json.newMap();
            rec.put("x", (double) cx); rec.put("y", (double) y); rec.put("z", (double) cz);
            rec.put("d", dim.id()); rec.put("t", type); rec.put("day", (double) getDay());
            ruins.add(rec);
            worldSet(RUIN_KEY, ruins);
            return true;
        }
        return false;
    }

    public static void init() {
        if (initialized) return;
        initialized = true;

        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("ruins") || getDay() < 4) return;
            long tick = Sys.currentTick();
            for (Player p : World.getPlayers()) {
                try {
                    if (!isSurvival(p) || !p.dimension().id().equals("minecraft:overworld") || isUnderground(p) || realm.contains(p.id())) continue;
                    if (tick < lastTry.getOrDefault(p.id(), 0L)) continue;
                    lastTry.put(p.id(), tick + 10L * TICKS_PER_MIN);
                    if (chance(0.5)) tryBuild(p);
                } catch (Throwable e) { /* yut */ }
            }
        }, TICKS_PER_MIN);

        // Kesif bildirimi
        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("ruins")) return;
            List<Object> ruins = ruinList();
            if (ruins.isEmpty()) return;
            for (Player p : World.getPlayers()) {
                try {
                    List<Object> found = Json.asList(playerGet(p, "duskwalker:v11found", null));
                    if (found == null) found = new ArrayList<>();
                    boolean dirty = false;
                    for (int i = 0; i < ruins.size(); i++) {
                        Map<String, Object> r = Json.asMap(ruins.get(i));
                        boolean already = false;
                        for (Object o : found) if (o instanceof Number n && n.doubleValue() == i) { already = true; break; }
                        if (already || !p.dimension().id().equals(r.get("d"))) continue;
                        Vec3 pl = p.location();
                        if (Math.hypot(Json.num(r.get("x"), 0) - pl.x(), Json.num(r.get("z"), 0) - pl.z()) < 12 && Math.abs(Json.num(r.get("y"), 0) - pl.y()) < 12) {
                            found.add((double) i); dirty = true;
                            addStat(p, "ruinsFound");
                            say(p, tx("§6[Kesif]§r Bir yer buldun: ", "§6[Discovery]§r You found ") + tx(TYPE_NAMES.get((String) r.get("t"))) + ".");
                            playTo(p, "random.orb", 0.6, 0.8);
                        }
                    }
                    if (dirty) playerSet(p, "duskwalker:v11found", found);
                } catch (Throwable e) { /* yut */ }
            }
        }, 100);

        Events.After.playerLeave.subscribe(ev -> lastTry.remove(ev.playerId));

        // Test komutu: !ruin [tur] -> oyuncunun 9 blok onune kurar
        Events.Before.chatSend.subscribe(ev -> {
            String msg = ev.message;
            if (!msg.equals("!ruin") && msg.indexOf("!ruin ") != 0) return;
            ev.cancel = true;
            Player p = ev.sender;
            Sys.run(() -> {
                try {
                    String[] parts = msg.split("\\s+");
                    String type = parts.length > 1 && !parts[1].isEmpty() ? parts[1] : pick(new ArrayList<>(GENERATORS.keySet()));
                    if (!GENERATORS.containsKey(type)) { say(p, "§c" + String.join(", ", GENERATORS.keySet()) + "§r"); return; }
                    Vec3 v = p.getViewDirection();
                    int cx = (int) Math.floor(p.location().x() + v.x() * 9), cz = (int) Math.floor(p.location().z() + v.z() * 9);
                    int y = (int) Math.floor(p.location().y());
                    GENERATORS.get(type).generate(p.dimension(), cx, y, cz, getDay());
                    say(p, "§7" + type + " @ " + cx + " " + y + " " + cz + "§r");
                } catch (Throwable e) { /* yut */ }
            });
        });
    }
}

package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.*;

import static com.duskwalker.mod.Core.*;
import static com.duskwalker.mod.Ruins.*;

/**
 * DUSKWALKER v12 - 6 YENI HARABE TURU (ruins2.js). Ruins.GENERATORS tablosuna eklenir.
 *  stone_circle, hanging_tree, lighthouse, crossroads, empty_village, cairn
 */
public final class Ruins2 {
    private Ruins2() {}

    private static boolean initialized = false;

    private interface RingFn { void at(int px, int pz, int i); }

    private static void ring(Dimension dim, int x, int y, int z, int r, RingFn fn) {
        for (int i = 0; i < 8; i++) {
            double a = (Math.PI * 2 * i) / 8;
            fn.at(x + (int) Math.round(Math.cos(a) * r), z + (int) Math.round(Math.sin(a) * r), i);
        }
    }

    // cairn: kirilirsa bir sey belirir
    private static final Set<String> touched = new HashSet<>();

    public static void init() {
        if (initialized) return;
        initialized = true;

        GENERATORS.put("stone_circle", gen((dim, x, y, z, day) -> {
            clearVolume(dim, x - 6, y, z - 6, 13, 6, 13);
            ring(dim, x, y, z, 4, (px, pz, i) -> {
                int h = 2 + (i % 3);
                for (int k = 0; k < h; k++) put(dim, px, y + k, pz, i % 2 == 0 ? "minecraft:cobblestone" : "minecraft:mossy_cobblestone");
            });
            put(dim, x, y - 1, z, "minecraft:soul_soil");
            put(dim, x, y, z, "minecraft:soul_campfire");
            sign(dim, x + 2, y, z + 2, tx("DINLIYORLAR", "THEY ARE\nLISTENING"));
        }));

        GENERATORS.put("hanging_tree", gen((dim, x, y, z, day) -> {
            clearVolume(dim, x - 5, y, z - 5, 11, 11, 11);
            for (int k = 0; k < 9; k++) put(dim, x, y + k, z, "minecraft:stripped_spruce_log");
            int[][] branches = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
            for (int i = 0; i < branches.length; i++) {
                int dx = branches[i][0], dz = branches[i][1];
                int by = y + 5 + (i % 2) * 2;
                for (int k = 1; k <= 3; k++) put(dim, x + dx * k, by, z + dz * k, "minecraft:stripped_spruce_log");
                int hx = x + dx * 3, hz = z + dz * 3;
                for (int k = 1; k <= 3; k++) put(dim, hx, by - k, hz, "minecraft:spruce_fence");
                put(dim, hx, by - 4, hz, "minecraft:hay_block");
                put(dim, hx, by - 5, hz, "minecraft:carved_pumpkin");
            }
            sign(dim, x + 1, y, z + 1, tx("HEPSI BURADA", "ALL OF THEM\nARE HERE"));
        }));

        GENERATORS.put("lighthouse", gen((dim, x, y, z, day) -> {
            clearVolume(dim, x - 3, y, z - 3, 7, 18, 7);
            for (int dy = 0; dy < 14; dy++) {
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) == 2 || Math.abs(dz) == 2) put(dim, x + dx, y + dy, z + dz, dy % 5 == 4 ? "minecraft:stone_bricks" : "minecraft:cobblestone");
                }
            }
            put(dim, x, y, z - 2, "minecraft:air"); put(dim, x, y + 1, z - 2, "minecraft:air");
            for (int dy = 0; dy < 14; dy++) put(dim, x, y + dy, z, "minecraft:scaffolding");
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) put(dim, x + dx, y + 14, z + dz, "minecraft:stone_bricks");
            put(dim, x, y + 14, z, "minecraft:air");
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) if (Math.abs(dx) == 2 || Math.abs(dz) == 2) put(dim, x + dx, y + 15, z + dz, "minecraft:glass_pane");
            put(dim, x, y + 15, z, "minecraft:soul_lantern");
            chestWith(dim, x + 1, y + 15, z + 1, randomNote(), new ItemStack("minecraft:torch", randInt(10, 20)), new ItemStack("minecraft:golden_carrot", randInt(2, 5)));
            sign(dim, x, y, z - 3, tx("ISIK\nSONUK", "THE LIGHT\nIS OFF"));
        }));

        GENERATORS.put("crossroads", gen((dim, x, y, z, day) -> {
            clearVolume(dim, x - 9, y, z - 9, 19, 4, 19);
            for (int k = -8; k <= 8; k++) { put(dim, x + k, y - 1, z, "minecraft:gravel"); put(dim, x, y - 1, z + k, "minecraft:gravel"); }
            for (int k = 0; k < 4; k++) put(dim, x, y + k, z, "minecraft:spruce_fence");
            String[][] texts = { { "EV", "HOME" }, { "GUVENLI", "SAFE" }, { "CIKIS", "EXIT" }, { "BU YOL", "THIS WAY" } };
            int[][] offs = { { 2, 0 }, { -2, 0 }, { 0, 2 }, { 0, -2 } };
            for (int i = 0; i < offs.length; i++) sign(dim, x + offs[i][0], y, z + offs[i][1], tx(texts[i]));
        }));

        GENERATORS.put("empty_village", gen((dim, x, y, z, day) -> {
            int[][] spots = { { -9, 0 }, { 9, 0 }, { 0, 10 } };
            for (int[] s : spots) {
                int dx = s[0], dz = s[1];
                for (int fx = -2; fx <= 5; fx++) for (int fz = -3; fz <= 3; fz++) for (int k = 1; k <= 3; k++) put(dim, x + dx + fx, y - k, z + dz + fz, "minecraft:dirt");
                GENERATORS.get("camp").generate(dim, x + dx, y, z + dz, day);
            }
            clearVolume(dim, x - 2, y, z - 2, 5, 3, 5);
            put(dim, x, y, z, "minecraft:campfire");
            sign(dim, x + 2, y, z, tx("ACELEYLE\nGITTILER", "THEY LEFT\nIN A HURRY"));
        }));

        GENERATORS.put("cairn", gen((dim, x, y, z, day) -> {
            clearVolume(dim, x - 2, y, z - 2, 5, 7, 5);
            for (int k = 0; k < 5; k++) put(dim, x, y + k, z, "minecraft:cobblestone");
            sign(dim, x + 1, y, z + 1, tx("DOKUNMA", "DON'T"));
        }));

        TYPE_NAMES.put("stone_circle", new String[] { "bir tas halka", "a stone circle" });
        TYPE_NAMES.put("hanging_tree", new String[] { "asili korkuluklu bir agac", "a tree of hanging effigies" });
        TYPE_NAMES.put("lighthouse", new String[] { "sonuk bir fener kulesi", "a darkened lighthouse" });
        TYPE_NAMES.put("crossroads", new String[] { "yalan tabelali bir kavsak", "a crossroads of lying signs" });
        TYPE_NAMES.put("empty_village", new String[] { "bos bir koy", "an empty village" });
        TYPE_NAMES.put("cairn", new String[] { "bir tas yigini", "a cairn" });

        Events.After.playerBreakBlock.subscribe(ev -> {
            try {
                Player p = ev.player;
                if (!isEnabled("ruins2") || !isSurvival(p)) return;
                List<Object> ruins = Json.asList(worldGet(RUIN_KEY, null));
                if (ruins == null) ruins = new ArrayList<>();
                for (int i = 0; i < ruins.size(); i++) {
                    Map<String, Object> r = Json.asMap(ruins.get(i));
                    if (r == null || !"cairn".equals(r.get("t")) || !p.dimension().id().equals(r.get("d"))
                        || Math.hypot(Json.num(r.get("x"), 0) - ev.block.x(), Json.num(r.get("z"), 0) - ev.block.z()) > 2.5) continue;
                    String key = p.id() + ":" + i;
                    if (touched.contains(key)) return;
                    touched.add(key);
                    Vec3 spot = standSpot(p.dimension(), behindPlayer(p, 4), 0, 2, 8);
                    if (spot != null) {
                        Entity e = spawnEffigy(p.dimension(), spot, 3);
                        if (e != null) { faceToward(e, eyePos(p)); Sys.runTimeout(() -> despawn(e, true), 50); }
                    }
                    playTo(p, "duskwalker.creak", 1.0, 0.5);
                    say(p, tx("§4Duskwalker§r: Dokunma demistim.", "§4Duskwalker§r: I told you not to."));
                    loseSanity(p, 12);
                    addStat(p, "cairnsTouched");
                    return;
                }
            } catch (Throwable e) { /* yut */ }
        });
    }
}

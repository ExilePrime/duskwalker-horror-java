package com.duskwalker.fabric;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Bedrock komutu -> Java komutu. Yalnizca scriptlerde (mod/) gercekten kullanilan komutlar ve yakin akrabalari:
 * fill (blok durumu + "replace" + 32768 sinirina bolme), setblock, replaceitem (block/entity), function, execute (in/positioned/at/as/if block ... run),
 * summon, tp/teleport, weather, gamerule, effect, time. Digerleri aynen gecer.
 * Komutlar sunucu kaynagiyla (izin 4, sessiz) calisir.
 */
public final class CommandTranslator {
    private CommandTranslator() {}

    private static final int FILL_LIMIT = 32768;
    public static final String FUNCTION_NAMESPACE = "duskwalker";

    // ---------------------------------------------------------------- calistirma
    public static CommandSourceStack dimensionSource(ServerLevel level) {
        return level.getServer().createCommandSourceStack().withLevel(level).withPosition(Vec3.ZERO).withPermission(4).withSuppressedOutput();
    }

    public static CommandSourceStack serverSource(MinecraftServer server) {
        return server.createCommandSourceStack().withPermission(4).withSuppressedOutput();
    }

    /** Tum cevrilmis komutlar hatasiz calistiysa true. Hata firlatmaz. */
    public static boolean run(MinecraftServer server, CommandSourceStack source, String bedrockCommand) {
        if (server == null || bedrockCommand == null) return false;
        CommandSourceStack src = source.withPermission(4).withSuppressedOutput();
        CommandDispatcher<CommandSourceStack> d = server.getCommands().getDispatcher();
        boolean all = true;
        List<String> cmds;
        try { cmds = translate(bedrockCommand); } catch (RuntimeException e) { return false; }
        for (String c : cmds) {
            try { d.execute(c, src); } catch (Throwable t) { all = false; }
        }
        return all;
    }

    // ---------------------------------------------------------------- cevirme
    /** Bir Bedrock komutu -> bir ya da daha cok Java komutu (buyuk fill bolunur). */
    public static List<String> translate(String raw) {
        String c = raw.trim();
        if (c.startsWith("/")) c = c.substring(1).trim();
        List<String> out = new ArrayList<>();
        if (c.isEmpty()) return out;
        List<String> t = tokens(c);
        String head = t.get(0).toLowerCase(Locale.ROOT);
        switch (head) {
            case "fill": out.addAll(fill(t)); break;
            case "setblock": out.add(setblock(t)); break;
            case "replaceitem": out.add(replaceitem(t)); break;
            case "function": out.add(function(t)); break;
            case "execute": out.addAll(execute(t)); break;
            case "summon": out.add(summon(t)); break;
            case "gamerule": out.add(gamerule(t)); break;
            case "effect": out.add(effect(t)); break;
            case "time": out.add(time(t)); break;
            default: out.add(c); break;
        }
        return out;
    }

    // ---------------------------------------------------------------- yardimcilar
    /** Bosluklara gore boler; [] ve "" icindeki bosluklar korunur; '[' ile baslayan token oncekiyle birlestirilir. */
    static List<String> tokens(String s) {
        List<String> raw = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int depth = 0;
        boolean quote = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"') quote = !quote;
            if (!quote) {
                if (ch == '[' || ch == '{') depth++;
                else if (ch == ']' || ch == '}') depth--;
            }
            if (Character.isWhitespace(ch) && depth <= 0 && !quote) {
                if (cur.length() > 0) { raw.add(cur.toString()); cur.setLength(0); }
            } else cur.append(ch);
        }
        if (cur.length() > 0) raw.add(cur.toString());
        List<String> out = new ArrayList<>();
        for (String r : raw) {
            if (r.startsWith("[") && !out.isEmpty()) out.set(out.size() - 1, out.get(out.size() - 1) + r);
            else out.add(r);
        }
        return out;
    }

    private static String join(List<String> t, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to && i < t.size(); i++) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(t.get(i));
        }
        return sb.toString();
    }

    private static String join(List<String> t, int from) { return join(t, from, t.size()); }

    private static boolean isInt(String s) {
        if (s == null || s.isEmpty()) return false;
        try { Integer.parseInt(s); return true; } catch (NumberFormatException e) { return false; }
    }

    private static boolean isCoord(String s) {
        if (s == null || s.isEmpty()) return false;
        if (s.startsWith("~") || s.startsWith("^")) return true;
        try { Double.parseDouble(s); return true; } catch (NumberFormatException e) { return false; }
    }

    // ---------------------------------------------------------------- blok + durumlar
    // Bedrock kapi yonu 0..3 = dogu, guney, bati, kuzey; cit kapisi 0..3 = guney, bati, kuzey, dogu;
    // facing_direction 0..5 = asagi, yukari, kuzey, guney, bati, dogu
    private static final String[] DOOR_DIRS = { "east", "south", "west", "north" };
    private static final String[] GATE_DIRS = { "south", "west", "north", "east" };
    private static final String[] FACING_DIRS = { "down", "up", "north", "south", "west", "east" };

    private static String unq(String s) {
        String r = s.trim();
        if (r.length() >= 2 && r.startsWith("\"") && r.endsWith("\"")) r = r.substring(1, r.length() - 1);
        return r;
    }

    private static List<String> splitTop(String s) {
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean q = false;
        for (char ch : s.toCharArray()) {
            if (ch == '"') q = !q;
            if (ch == ',' && !q) { parts.add(cur.toString()); cur.setLength(0); } else cur.append(ch);
        }
        if (cur.length() > 0) parts.add(cur.toString());
        return parts;
    }

    private static String stateToJava(String blockName, String key, String value) {
        String v = unq(value);
        boolean b = v.equals("true") || v.equals("1");
        int n;
        try { n = Integer.parseInt(v); } catch (NumberFormatException e) { n = 0; }
        switch (key) {
            case "upper_block_bit": return "half=" + (b ? "upper" : "lower");
            case "upside_down_bit": return "half=" + (b ? "top" : "bottom");
            case "open_bit": return "open=" + b;
            case "powered_bit": return "powered=" + b;
            case "door_hinge_bit": return "hinge=" + (b ? "right" : "left");
            case "direction": {
                String[] t = blockName.endsWith("fence_gate") ? GATE_DIRS : DOOR_DIRS;
                return "facing=" + t[Math.floorMod(n, 4)];
            }
            case "facing_direction": return "facing=" + FACING_DIRS[Math.floorMod(n, 6)];
            case "ground_sign_direction": return "rotation=" + Math.floorMod(n, 16);
            default: return key + "=" + v;
        }
    }

    /** "spruce_door[\"upper_block_bit\"=true]" -> "minecraft:spruce_door[half=upper]" */
    static String block(String tok) {
        int br = tok.indexOf('[');
        String base = br < 0 ? tok : tok.substring(0, br);
        String javaId = IdMap.blockJavaId(base);
        if (br < 0) return javaId;
        int end = tok.lastIndexOf(']');
        String inner = tok.substring(br + 1, end < 0 ? tok.length() : end);
        String name = IdMap.blockJavaName(base);
        StringBuilder sb = new StringBuilder();
        for (String pair : splitTop(inner)) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                eq = pair.indexOf(':');
                if (eq < 0) continue;
            }
            String k = unq(pair.substring(0, eq));
            String val = pair.substring(eq + 1);
            if (sb.length() > 0) sb.append(',');
            sb.append(stateToJava(name, k, val));
        }
        return sb.length() == 0 ? javaId : javaId + "[" + sb + "]";
    }

    // ---------------------------------------------------------------- fill
    private static List<String> fill(List<String> t) {
        List<String> out = new ArrayList<>();
        if (t.size() < 8) { out.add(join(t, 0)); return out; }
        String blk = block(t.get(7));
        int i = 8;
        while (i < t.size() && isInt(t.get(i))) i++;          // tileData (eski)
        String mode = null;
        String filter = null;
        if (i < t.size()) {
            mode = t.get(i).toLowerCase(Locale.ROOT);
            i++;
            if (mode.equals("replace") && i < t.size()) {
                filter = block(t.get(i));
                i++;
            }
        }
        String tail = "";
        if (mode != null) {
            if (mode.equals("replace")) tail = filter != null ? " replace " + filter : " replace";
            else tail = " " + mode;
        }
        boolean splittable = mode == null || mode.equals("replace") || mode.equals("keep") || mode.equals("destroy");
        boolean abs = true;
        int[] c = new int[6];
        for (int k = 0; k < 6; k++) {
            if (isInt(t.get(1 + k))) c[k] = Integer.parseInt(t.get(1 + k)); else abs = false;
        }
        if (!abs || !splittable) {
            out.add("fill " + join(t, 1, 7) + " " + blk + tail);
            return out;
        }
        int x1 = Math.min(c[0], c[3]), x2 = Math.max(c[0], c[3]);
        int y1 = Math.min(c[1], c[4]), y2 = Math.max(c[1], c[4]);
        int z1 = Math.min(c[2], c[5]), z2 = Math.max(c[2], c[5]);
        splitFill(x1, y1, z1, x2, y2, z2, blk + tail, out);
        return out;
    }

    private static void splitFill(int x1, int y1, int z1, int x2, int y2, int z2, String tail, List<String> out) {
        long dx = (long) x2 - x1 + 1, dy = (long) y2 - y1 + 1, dz = (long) z2 - z1 + 1;
        if (dx * dy * dz <= FILL_LIMIT) {
            out.add("fill " + x1 + " " + y1 + " " + z1 + " " + x2 + " " + y2 + " " + z2 + " " + tail);
            return;
        }
        if (dx >= dy && dx >= dz) {
            int m = x1 + (int) (dx / 2) - 1;
            splitFill(x1, y1, z1, m, y2, z2, tail, out);
            splitFill(m + 1, y1, z1, x2, y2, z2, tail, out);
        } else if (dz >= dy) {
            int m = z1 + (int) (dz / 2) - 1;
            splitFill(x1, y1, z1, x2, y2, m, tail, out);
            splitFill(x1, y1, m + 1, x2, y2, z2, tail, out);
        } else {
            int m = y1 + (int) (dy / 2) - 1;
            splitFill(x1, y1, z1, x2, m, z2, tail, out);
            splitFill(x1, m + 1, z1, x2, y2, z2, tail, out);
        }
    }

    // ---------------------------------------------------------------- setblock
    private static String setblock(List<String> t) {
        if (t.size() < 5) return join(t, 0);
        String s = "setblock " + join(t, 1, 4) + " " + block(t.get(4));
        for (int i = 5; i < t.size(); i++) {
            String m = t.get(i).toLowerCase(Locale.ROOT);
            if (m.equals("replace") || m.equals("destroy") || m.equals("keep")) { s += " " + m; break; }
        }
        return s;
    }

    // ---------------------------------------------------------------- replaceitem
    private static final String INDEXED_SLOTS = " container hotbar inventory enderchest ";

    private static String replaceitem(List<String> t) {
        // replaceitem block x y z slot.container N [keep|destroy] item [count] [data]
        // replaceitem entity <target> slot.x [N] [keep|destroy] item [count] [data]
        if (t.size() < 4) return join(t, 0);
        String kind = t.get(1).toLowerCase(Locale.ROOT);
        String target;
        int p;
        if (kind.equals("block")) {
            if (t.size() < 7) return join(t, 0);
            target = "block " + join(t, 2, 5);
            p = 5;
        } else if (kind.equals("entity")) {
            target = "entity " + t.get(2);
            p = 3;
        } else return join(t, 0);
        String slotName = t.get(p).startsWith("slot.") ? t.get(p).substring(5) : t.get(p);
        p++;
        String slot = slotName;
        if (INDEXED_SLOTS.contains(" " + slotName + " ") && p < t.size() && isInt(t.get(p))) {
            slot = slotName + "." + t.get(p);
            p++;
        }
        if (p < t.size() && (t.get(p).equals("keep") || t.get(p).equals("destroy"))) p++;
        if (p >= t.size()) return join(t, 0);
        String item = IdMap.itemJavaId(t.get(p));
        p++;
        String count = p < t.size() && isInt(t.get(p)) ? " " + t.get(p) : "";
        return "item replace " + target + " " + slot + " with " + item + count;
    }

    // ---------------------------------------------------------------- function
    private static String function(List<String> t) {
        if (t.size() < 2) return join(t, 0);
        String f = t.get(1);
        if (!f.contains(":")) f = FUNCTION_NAMESPACE + ":" + f;
        return "function " + f;
    }

    // ---------------------------------------------------------------- execute
    private static String dimId(String d) {
        String n = IdMap.strip(d);
        switch (n) {
            case "overworld": return "minecraft:overworld";
            case "nether": case "the_nether": return "minecraft:the_nether";
            case "the_end": case "end": return "minecraft:the_end";
            default: return d.contains(":") ? d : "minecraft:" + n;
        }
    }

    private static List<String> execute(List<String> t) {
        List<String> out = new ArrayList<>();
        int run = -1;
        for (int i = 1; i < t.size(); i++) if (t.get(i).equals("run")) { run = i; break; }
        // on ek: ara tokenlari cevir
        List<String> pre = new ArrayList<>();
        int end = run < 0 ? t.size() : run;
        for (int i = 0; i < end; i++) {
            String tok = t.get(i);
            String prev = i > 0 ? t.get(i - 1) : "";
            if (prev.equals("in") && i > 0) { pre.add(dimId(tok)); continue; }
            if (tok.equals("block") && i > 0 && (prev.equals("if") || prev.equals("unless")) && i + 4 < end) {
                pre.add(tok);
                for (int k = 1; k <= 3; k++) pre.add(t.get(i + k));
                pre.add(block(t.get(i + 4)));
                i += 4;
                continue;
            }
            pre.add(tok);
        }
        String prefix = join(pre, 0);
        if (run < 0) { out.add(prefix); return out; }
        String inner = join(t, run + 1);
        for (String c : translate(inner)) out.add(prefix + " run " + c);
        return out;
    }

    // ---------------------------------------------------------------- summon
    private static String summon(List<String> t) {
        if (t.size() < 2) return join(t, 0);
        net.minecraft.world.entity.EntityType<?> et = IdMap.entityType(t.get(1));
        String type = et == null ? t.get(1) : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(et).toString();
        if (t.size() >= 5 && isCoord(t.get(2)) && isCoord(t.get(3)) && isCoord(t.get(4))) {
            return "summon " + type + " " + join(t, 2, 5);
        }
        return "summon " + type + " ~ ~ ~";
    }

    // ---------------------------------------------------------------- gamerule
    private static final Map<String, String> RULES = new HashMap<>();
    static {
        String[] names = { "doDaylightCycle", "doWeatherCycle", "doMobSpawning", "mobGriefing", "keepInventory", "doFireTick",
                "commandBlockOutput", "doInsomnia", "naturalRegeneration", "sendCommandFeedback", "doImmediateRespawn", "doMobLoot",
                "doTileDrops", "doEntityDrops", "showDeathMessages", "randomTickSpeed", "doPatrolSpawning", "doTraderSpawning",
                "fallDamage", "fireDamage", "drowningDamage", "freezeDamage", "announceAdvancements", "spawnRadius", "maxCommandChainLength" };
        for (String n : names) RULES.put(n.toLowerCase(Locale.ROOT), n);
        RULES.put("dodaylightcycle", "doDaylightCycle");
        RULES.put("tntexplodes", "tntExplodes");
    }

    private static String gamerule(List<String> t) {
        if (t.size() < 2) return join(t, 0);
        String n = RULES.getOrDefault(t.get(1).toLowerCase(Locale.ROOT), t.get(1));
        return t.size() >= 3 ? "gamerule " + n + " " + join(t, 2) : "gamerule " + n;
    }

    // ---------------------------------------------------------------- effect
    private static String effect(List<String> t) {
        if (t.size() < 3) return join(t, 0);
        String target = t.get(1);
        String e = t.get(2);
        if (e.equalsIgnoreCase("clear")) return "effect clear " + target;
        net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> h = IdMap.effect(e);
        String id = h == null ? IdMap.strip(e) : h.unwrapKey().map(k -> k.location().getPath()).orElse(IdMap.strip(e));
        return "effect give " + target + " minecraft:" + id + (t.size() > 3 ? " " + join(t, 3) : "");
    }

    // ---------------------------------------------------------------- time
    private static String time(List<String> t) {
        if (t.size() >= 3 && t.get(1).equals("set")) {
            String v = t.get(2).toLowerCase(Locale.ROOT);
            if (v.equals("sunrise")) return "time set 23000";
            if (v.equals("sunset")) return "time set 12000";
        }
        return join(t, 0);
    }
}

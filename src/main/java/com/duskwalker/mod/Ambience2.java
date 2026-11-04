package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static com.duskwalker.mod.Core.*;

/** DUSKWALKER v11 - ORTAM SESLERI, bolgeye gore (ambience2.js) */
public final class Ambience2 {
    private Ambience2() {}

    // [ses, hacim, perde]
    private static final Map<String, Object[][]> POOLS = new HashMap<>();
    static {
        POOLS.put("cave", new Object[][] {{"ambient.cave", 0.6, 0.55}, {"dig.stone", 0.5, 0.5}, {"mob.zombie.say", 0.3, 0.55}, {"mob.skeleton.say", 0.3, 0.6}, {"random.anvil_land", 0.25, 0.6}});
        POOLS.put("snow", new Object[][] {{"mob.wolf.growl", 0.4, 0.6}, {"mob.wolf.whine", 0.4, 0.5}, {"step.snow", 0.6, 0.6}, {"ambient.cave", 0.35, 0.7}});
        POOLS.put("desert", new Object[][] {{"step.sand", 0.6, 0.6}, {"mob.husk.ambient", 0.35, 0.6}, {"ambient.cave", 0.3, 0.8}});
        POOLS.put("forest", new Object[][] {{"step.grass", 0.6, 0.6}, {"dig.grass", 0.5, 0.6}, {"mob.spider.say", 0.3, 0.7}, {"mob.bat.idle", 0.4, 0.7}, {"mob.wolf.growl", 0.3, 0.5}});
        POOLS.put("water", new Object[][] {{"liquid.water", 0.6, 0.5}, {"mob.endermen.idle", 0.3, 0.5}, {"ambient.cave", 0.3, 0.6}});
        POOLS.put("plains", new Object[][] {{"step.grass", 0.5, 0.6}, {"mob.endermen.idle", 0.3, 0.55}, {"ambient.cave", 0.3, 0.7}});
        POOLS.put("nether", new Object[][] {{"mob.ghast.moan", 0.5, 0.6}, {"mob.ghast.scream", 0.35, 0.5}, {"ambient.cave", 0.5, 0.5}});
        POOLS.put("end", new Object[][] {{"mob.endermen.idle", 0.6, 0.5}, {"mob.endermen.stare", 0.4, 0.5}, {"mob.endermen.portal", 0.4, 0.6}});
    }

    private static final Pattern SNOW_RE = Pattern.compile("snow|ice|powder");
    private static final Pattern SAND_RE = Pattern.compile("sand|sandstone");

    private static final class Sample { int leaves, water; String under = ""; }

    private static Sample sampleBlocks(Player player) {
        Dimension dim = player.dimension();
        Vec3 l = player.location();
        int bx = (int) Math.floor(l.x()), by = (int) Math.floor(l.y()), bz = (int) Math.floor(l.z());
        Sample s = new Sample();
        for (int i = 0; i < 14; i++) {
            Vec3 pos = new Vec3(bx + randInt(-5, 5), by + randInt(-1, 4), bz + randInt(-5, 5));
            Block b = safe(() -> dim.getBlock(pos), (Block) null);
            if (b == null) continue;
            String id = b.typeId();
            if (id.contains("leaves") || id.contains("log")) s.leaves++;
            if (id.equals("minecraft:water")) s.water++;
        }
        Block under = safe(() -> dim.getBlock(new Vec3(bx, by - 1, bz)), (Block) null);
        s.under = under != null ? under.typeId() : "";
        return s;
    }

    private static String zoneOf(Player player) {
        String dimId = player.dimension().id();
        if (dimId.equals("minecraft:nether")) return "nether";
        if (dimId.equals("minecraft:the_end")) return "end";
        if (isUnderground(player)) return "cave";
        Sample s = sampleBlocks(player);
        if (s.water >= 3) return "water";
        if (SNOW_RE.matcher(s.under).find()) return "snow";
        if (SAND_RE.matcher(s.under).find()) return "desert";
        if (s.leaves >= 3) return "forest";
        return "plains";
    }

    public static void init() {
        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("ambience2")) return;
            for (Player p : World.getPlayers()) {
                try {
                    if (!isSurvival(p)) continue;
                    String zone = zoneOf(p);
                    double sanity = getSanity(p);
                    double prob = 0.10;
                    if (isNight()) prob += 0.12;
                    if (sanity < 60) prob += 0.12;
                    if (zone.equals("cave") || zone.equals("nether") || zone.equals("end")) prob += 0.14;
                    if (isInRefuge(p)) prob *= 0.5;
                    if (!chance(prob)) continue;
                    Object[] snd = pick(POOLS.get(zone));
                    double a = Math.random() * Math.PI * 2;
                    double d = rand(9, 24);
                    Vec3 l = p.location();
                    playAt(p.dimension(), (String) snd[0], new Vec3(l.x() + Math.cos(a) * d, l.y(), l.z() + Math.sin(a) * d),
                        ((Number) snd[1]).doubleValue(), ((Number) snd[2]).doubleValue() + rand(-0.08, 0.08));
                } catch (Throwable e) { /* yut */ }
            }
        }, TICKS_PER_SEC * 30);
    }
}

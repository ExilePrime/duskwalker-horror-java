package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static com.duskwalker.mod.Core.*;

/**
 * DUSKWALKER v10 - ISIK SIGINAGI (light_refuge.js)
 * Etrafinda (yaricap 5 blok) en az 3 isik kaynagi varsa "siginakta" sayilirsin.
 */
public final class LightRefuge {
    private LightRefuge() {}

    private static final Pattern LIGHT_RE = Pattern.compile(
        "(^minecraft:(torch|soul_torch|lantern|soul_lantern|campfire|soul_campfire|glowstone|sea_lantern|shroomlight|jack_o_lantern|beacon|end_rod|lit_furnace|lit_smoker|lit_blast_furnace|ochre_froglight|verdant_froglight|pearlescent_froglight|lava_cauldron)$|candle$|copper_torch)");

    private static final int RADIUS = 5;
    private static final int VERTICAL = 3;

    private static int countLights(Player player) {
        Dimension dimension = player.dimension();
        Vec3 l = player.location();
        int cx = (int) Math.floor(l.x()), cy = (int) Math.floor(l.y()), cz = (int) Math.floor(l.z());
        int count = 0;
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                if (x * x + z * z > RADIUS * RADIUS) continue;
                for (int y = -1; y <= VERTICAL; y++) {
                    final int fx = cx + x, fy = cy + y, fz = cz + z;
                    Block b = safe(() -> dimension.getBlock(new Vec3(fx, fy, fz)), (Block) null);
                    if (b == null) continue;
                    if (LIGHT_RE.matcher(b.typeId()).find()) {
                        count++;
                        if (count >= 8) return count;
                    }
                }
            }
        }
        return count;
    }

    private static final Map<String, Boolean> announced = new HashMap<>(); // oyuncu id -> siginakta miydi

    public static void init() {
        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("refuge")) return;
            long tick = Sys.currentTick();
            for (Player p : World.getPlayers()) {
                try {
                    if (!isSurvival(p)) { refuge.put(p.id(), 0); continue; }
                    int lights = countLights(p);
                    refuge.put(p.id(), lights);
                    boolean inside = lights >= 3;
                    boolean was = Boolean.TRUE.equals(announced.get(p.id()));

                    if (inside && !was) {
                        announced.put(p.id(), true);
                        actionbar(p, "§eIsik seni sarmaliyor§r");
                        addStat(p, "refugeEntries");
                    } else if (!inside && was) {
                        announced.put(p.id(), false);
                    }

                    if (inside) {
                        addStat(p, "refugeSeconds", 3);
                        Object lastO = safe(() -> p.getDynamicProperty(KEY.LAST_SANITY_EVENT), (Object) null);
                        double last = lastO instanceof Number n ? n.doubleValue() : 0;
                        if (tick - last > 20 * TICKS_PER_SEC && getSanity(p) < 100) {
                            gainSanity(p, (1 + (lights >= 6 ? 1 : 0)) * modifiers.regenMult * perkMult(p, "regen") * pactMult(p, "regen"));
                        }
                    }
                    maxStat(p, "maxLights", lights);
                } catch (Throwable e) { /* yut */ }
            }
        }, 60);

        try {
            Events.After.playerLeave.subscribe(ev -> announced.remove(ev.playerId));
        } catch (Throwable e) { /* yut */ }
    }
}

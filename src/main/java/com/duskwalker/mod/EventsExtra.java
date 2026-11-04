package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static com.duskwalker.mod.Core.*;

/**
 * DUSKWALKER v10 - EK OLAYLAR (events_extra.js)
 * 1) Titreyen Isik 2) Mezar Tasi
 */
public final class EventsExtra {
    private EventsExtra() {}

    private static final Pattern FLICKER_RE = Pattern.compile("(torch|lantern)$");

    private static final class Light {
        int x, y, z;
        Perm perm;
        String id;
    }

    private static List<Light> collectLights(Player player, int radius, int limit) {
        Dimension dimension = player.dimension();
        Vec3 l = player.location();
        int cx = (int) Math.floor(l.x()), cy = (int) Math.floor(l.y()), cz = (int) Math.floor(l.z());
        List<Light> found = new ArrayList<>();
        for (int attempt = 0; attempt < 220 && found.size() < limit; attempt++) {
            int px = cx + randInt(-radius, radius), py = cy + randInt(-3, 4), pz = cz + randInt(-radius, radius);
            Block b = safe(() -> dimension.getBlock(new Vec3(px, py, pz)), (Block) null);
            if (b == null || !FLICKER_RE.matcher(b.typeId()).find()) continue;
            boolean dup = false;
            for (Light f : found) if (f.x == px && f.y == py && f.z == pz) { dup = true; break; }
            if (dup) continue;
            Light f = new Light();
            f.x = px; f.y = py; f.z = pz; f.perm = b.permutation(); f.id = b.typeId();
            found.add(f);
        }
        return found;
    }

    public static void init() {
        registerEvent(EventDef.of("light_flicker").name("Titreyen Isik").minDay(3).weight(4).cooldown(8L * TICKS_PER_MIN)
            .cond(p -> isNight())
            .run(player -> {
                List<Light> lights = collectLights(player, 9, 5);
                if (lights.size() < 2) return false;
                Dimension dimension = player.dimension();

                java.util.function.Consumer<Boolean> setAll = on -> {
                    for (Light f : lights) {
                        safe(() -> {
                            Block b = dimension.getBlock(new Vec3(f.x, f.y, f.z));
                            if (b == null) return;
                            if (on) { if (b.typeId().equals("minecraft:air")) b.setPermutation(f.perm); }
                            else if (b.typeId().equals(f.id)) b.setType("minecraft:air");
                        });
                    }
                };

                // soner - yanar - soner - yanar - soner (3 sn) ... sonra kesin geri gelir
                int[] steps = {0, 12, 20, 34, 40};
                for (int i = 0; i < steps.length; i++) {
                    final boolean on = i % 2 == 1;
                    Sys.runTimeout(() -> setAll.accept(on), steps[i]);
                }
                Sys.runTimeout(() -> setAll.accept(false), 50);
                Sys.runTimeout(() -> {
                    setAll.accept(true);
                    if (isValid(player)) playTo(player, "random.fizz", 0.6, 0.7);
                }, 80);
                // guvenlik: bir sey ters giderse (chunk yuklu degil vb.) 10 sn sonra tekrar dene
                Sys.runTimeout(() -> setAll.accept(true), 200);

                loseSanity(player, 4);
                return true;
            }));

        registerEvent(EventDef.of("grave_marker").name("Mezar Tasi").minDay(9).weight(2).cooldown(30L * TICKS_PER_MIN)
            .cond(p -> getSanity(p) <= 80)
            .run(player -> {
                Dimension dimension = player.dimension();
                Vec3 spot = standSpot(dimension, frontPlayer(player, 8), 0, 3, 16);
                if (spot == null) return false;
                int bx = (int) Math.floor(spot.x()), by = (int) Math.floor(spot.y()), bz = (int) Math.floor(spot.z());
                Vec3 base = new Vec3(bx, by, bz);
                Vec3 signPos = new Vec3(bx, by + 1, bz);

                try {
                    Block stone = dimension.getBlock(base);
                    Block sign = dimension.getBlock(signPos);
                    if (stone == null || sign == null || !stone.typeId().equals("minecraft:air") || !sign.typeId().equals("minecraft:air")) return false;
                    stone.setType("minecraft:mossy_cobblestone");
                    sign.setType("minecraft:standing_sign");
                    String nm = player.name().toUpperCase();
                    sign.setSignText("BURADA YATIYOR\n" + nm.substring(0, Math.min(14, nm.length())));
                } catch (Throwable e) { return false; }

                smoke(dimension, new Vec3(bx + 0.5, by + 0.2, bz + 0.5), 6);
                playAt(dimension, "random.fizz", new Vec3(bx, by, bz), 0.8, 0.5);
                Sys.runTimeout(() -> { if (isValid(player)) actionbar(player, "§8(bir sey tam karsinda belirdi...)§r"); }, 30);

                Sys.runTimeout(() -> {
                    safe(() -> {
                        Block stone = dimension.getBlock(base);
                        Block sign = dimension.getBlock(signPos);
                        if (sign != null && sign.typeId().equals("minecraft:standing_sign")) sign.setType("minecraft:air");
                        if (stone != null && stone.typeId().equals("minecraft:mossy_cobblestone")) stone.setType("minecraft:air");
                    });
                }, 6 * TICKS_PER_MIN);

                loseSanity(player, 7);
                addEvidence(player, 2);
                shake(player, 0.04, 0.6);
                return true;
            }));
    }
}

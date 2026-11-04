package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import static com.duskwalker.mod.Core.*;

/**
 * DUSKWALKER v11 - GORUS OYUNLARI (vision.js)
 *  1) peripheral_figure  2) mirror_behind  3) tunnel_vision  4) water_shadow
 */
public final class Vision {
    private Vision() {}

    public static void init() {
        registerEvent(EventDef.of("peripheral_figure").name("Peripheral Figure").minDay(5).weight(4).cooldown(9L * TICKS_PER_MIN)
            .cond(p -> getSanity(p) <= 70)
            .run(player -> {
                Dimension dimension = player.dimension();
                Vec3 v = player.getViewDirection();
                // bakis yonunun ~65 derece solu/sagi
                int side = Math.random() < 0.5 ? 1 : -1;
                double a = Math.atan2(v.z(), v.x()) + side * (Math.PI * 0.36);
                double d = rand(8, 13);
                Vec3 l = player.location();
                Vec3 spot = standSpot(dimension, new Vec3(l.x() + Math.cos(a) * d, l.y(), l.z() + Math.sin(a) * d), 0, 2, 10);
                if (spot == null) return false;
                Entity st = spawnEffigy(dimension, spot, 1);
                if (st == null) return false;
                faceToward(st, eyePos(player));
                final int[] ticks = { 0 };
                final int[] handle = { 0 };
                handle[0] = Sys.runInterval(() -> {
                    try {
                        ticks[0] += 2;
                        if (!isValid(player) || !isValid(st)) { Sys.clearRun(handle[0]); despawn(st, false); return; }
                        Vec3 head = new Vec3(spot.x(), spot.y() + 1.6, spot.z());
                        // oyuncu dogrudan bakarsa ya da 10 tik gectiyse kaybolur
                        if (viewDot(player, head) > 0.85 || ticks[0] >= 10) {
                            Sys.clearRun(handle[0]);
                            despawn(st, false);
                        }
                    } catch (Throwable e) { safe(() -> Sys.clearRun(handle[0])); }
                }, 2);
                loseSanity(player, 4);
                addStat(player, "peripheralSeen");
                return true;
            }));

        registerEvent(EventDef.of("mirror_behind").name("Mirror Behind You").minDay(10).weight(2).cooldown(22L * TICKS_PER_MIN)
            .cond(p -> stillSeconds(p) >= 8 && getSanity(p) <= 75)
            .run(player -> {
                Dimension dimension = player.dimension();
                Vec3 spot = standSpot(dimension, behindPlayer(player, 3.2), 0, 1.2, 10);
                if (spot == null) return false;
                Entity st = spawnEffigy(dimension, spot, 3);
                if (st == null) return false;
                // oyuncuyla ayni yone bakar (arkasi donuk)
                Vec3 v = player.getViewDirection();
                faceToward(st, new Vec3(spot.x() + v.x() * 5, spot.y() + 1.6, spot.z() + v.z() * 5));
                final int[] ticks = { 0 };
                final int[] handle = { 0 };
                handle[0] = Sys.runInterval(() -> {
                    try {
                        ticks[0] += 5;
                        if (!isValid(player) || !isValid(st)) { Sys.clearRun(handle[0]); despawn(st, false); return; }
                        Vec3 head = new Vec3(spot.x(), spot.y() + 1.6, spot.z());
                        if (viewDot(player, head) > 0.7) {
                            Sys.clearRun(handle[0]);
                            playTo(player, "mob.endermen.portal", 0.6, 0.5);
                            despawn(st, true);
                            loseSanity(player, 12);
                            addEvidence(player, 1);
                            return;
                        }
                        if (ticks[0] > 20 * 14) { Sys.clearRun(handle[0]); despawn(st, true); loseSanity(player, 4); }
                    } catch (Throwable e) { safe(() -> Sys.clearRun(handle[0])); }
                }, 5);
                return true;
            }));

        registerEvent(EventDef.of("tunnel_vision").name("Tunnel Vision").minDay(6).weight(3).cooldown(11L * TICKS_PER_MIN)
            .cond(p -> getSanity(p) <= 60)
            .run(player -> {
                effect(player, "darkness", 160, 0);
                effect(player, "slowness", 80, 0);
                playTo(player, "mob.warden.heartbeat", 0.9, 1.1);
                Sys.runTimeout(() -> { if (isValid(player)) playTo(player, "mob.warden.heartbeat", 0.9, 1.2); }, 14);
                Sys.runTimeout(() -> { if (isValid(player)) playTo(player, "mob.warden.heartbeat", 0.9, 1.3); }, 28);
                actionbar(player, tx("§8(her sey daraliyor...)§r", "§8(everything narrows...)§r"));
                loseSanity(player, 5);
                return true;
            }));

        registerEvent(EventDef.of("water_shadow").name("Shadow in the Water").minDay(4).weight(3).cooldown(14L * TICKS_PER_MIN)
            .cond(p -> isNight())
            .run(player -> {
                Dimension dimension = player.dimension();
                Vec3 l = player.location();
                int bx = (int) Math.floor(l.x()), by = (int) Math.floor(l.y()), bz = (int) Math.floor(l.z());
                Vec3 found = null;
                for (int i = 0; i < 90 && found == null; i++) {
                    Vec3 pos = new Vec3(bx + randInt(-9, 9), by + randInt(-3, 1), bz + randInt(-9, 9));
                    Block b = safe(() -> dimension.getBlock(pos), (Block) null);
                    Block above = safe(() -> dimension.getBlock(new Vec3(pos.x(), pos.y() + 1, pos.z())), (Block) null);
                    if (b != null && above != null && b.typeId().equals("minecraft:water") && above.typeId().equals("minecraft:air")) found = pos;
                }
                if (found == null) return false;
                Vec3 at = new Vec3(found.x() + 0.5, found.y() + 1, found.z() + 0.5);
                // dalgalanma: kabarcik + sicrama parcaciklari, ardindan alcak bir ses
                for (int i = 0; i < 4; i++) {
                    Sys.runTimeout(() -> {
                        particle(dimension, "minecraft:water_splash_particle", new Vec3(at.x() + rand(-0.4, 0.4), at.y(), at.z() + rand(-0.4, 0.4)));
                    }, i * 6);
                }
                playAt(dimension, "liquid.water", at, 0.8, 0.5);
                Sys.runTimeout(() -> {
                    if (!isValid(player)) return;
                    playAt(dimension, "mob.endermen.idle", at, 0.7, 0.5);
                    actionbar(player, tx("§8(suyun altinda biri nefes aldi...)§r", "§8(something breathed beneath the water...)§r"));
                    loseSanity(player, 4);
                }, 30);
                return true;
            }));
    }
}

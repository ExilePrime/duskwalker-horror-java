package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static com.duskwalker.mod.Core.*;

/**
 * DUSKWALKER v12 - ALAMETLER (16 ozgun olay)  [v10/omens.js]
 * lantern_thief, footprint_trail, bat_omen, glitch_hud, circling_bells, music_box, pit_whisper, weightless,
 * sign_turn, shared_breath, numbers_station, moths, echo_mine, tinnitus, fake_loot, the_lull
 */
public final class Omens {
    private Omens() {}

    /** /(torch|lantern)$/ */
    private static boolean isLight(String typeId) { return typeId.endsWith("torch") || typeId.endsWith("lantern"); }

    /** oyuncunun cevresinde predikata uyan bir blok bul; yoksa null */
    private static Block findBlock(Player player, int radius, Predicate<Block> pred, int tries) {
        Dimension dim = player.dimension();
        Vec3 l = player.location();
        int bx = (int) Math.floor(l.x()), by = (int) Math.floor(l.y()), bz = (int) Math.floor(l.z());
        for (int i = 0; i < (tries == 0 ? 140 : tries); i++) {
            Vec3 pos = new Vec3(bx + randInt(-radius, radius), by + randInt(-3, 4), bz + randInt(-radius, radius));
            Block b = safe(() -> dim.getBlock(pos), (Block) null);
            if (b != null && pred.test(b)) return b;
        }
        return null;
    }
    private static Block findBlock(Player player, int radius, Predicate<Block> pred) { return findBlock(player, radius, pred, 0); }

    private static final double[] LULLABY = { 1.0, 0.89, 0.79, 0.89, 1.0, 1.0, 0.89, 0.89, 0.79, 0.7, 0.63 };
    private static final Map<String, Long> awaiting = new HashMap<>(); // oyuncu id -> bitis tiki
    private static final Map<String, Long> echoCd = new HashMap<>();
    private static final String[] FOLLOWUPS = { "nape_breath", "countdown", "eyes_dark", "deja_vu", "phantom_sound", "tinnitus" };

    public static void init() {
        // 1) Fener Hirsizi
        registerEvent(EventDef.of("lantern_thief").name("Lantern Thief").minDay(4).weight(3).cooldown(18L * TICKS_PER_MIN)
            .cond(p -> isNight())
            .run(Omens::lanternThief));
        // 2) Ruh ayak izleri
        registerEvent(EventDef.of("footprint_trail").name("Footprints").minDay(3).weight(4).cooldown(12L * TICKS_PER_MIN)
            .cond(p -> isNight())
            .run(Omens::footprintTrail));
        // 3) Yarasa alameti
        registerEvent(EventDef.of("bat_omen").name("Bat Omen").minDay(3).weight(3).cooldown(15L * TICKS_PER_MIN)
            .cond(p -> isNight() && !isUnderground(p))
            .run(Omens::batOmen));
        // 4) Arayuz arizasi
        registerEvent(EventDef.of("glitch_hud").name("HUD Glitch").minDay(5).weight(3).cooldown(10L * TICKS_PER_MIN)
            .cond(p -> getSanity(p) <= 85)
            .run(Omens::glitchHud));
        // 5) Donen canlar
        registerEvent(EventDef.of("circling_bells").name("Circling Bells").minDay(6).weight(2).cooldown(16L * TICKS_PER_MIN)
            .cond(p -> isNight())
            .run(Omens::circlingBells));
        // 6) Muzik kutusu
        registerEvent(EventDef.of("music_box").name("Music Box").minDay(4).weight(3).cooldown(14L * TICKS_PER_MIN)
            .cond(p -> isNight())
            .run(Omens::musicBox));
        // 7) Yerin altindan fisilti
        registerEvent(EventDef.of("pit_whisper").name("Whisper from Below").minDay(5).weight(3).cooldown(12L * TICKS_PER_MIN)
            .cond(p -> isNight() && !isUnderground(p))
            .run(player -> {
                Vec3 l = player.location();
                playAt(player.dimension(), "duskwalker.whisper", new Vec3(l.x(), l.y() - 12, l.z()), 1.6, 0.6);
                Sys.runTimeout(() -> { if (isValid(player)) playAt(player.dimension(), "duskwalker.drone", new Vec3(l.x(), l.y() - 18, l.z()), 1.2, 0.7); }, 30);
                actionbar(player, tx("§8(altinda biri konusuyor)§r", "§8(someone speaks beneath you)§r"));
                loseSanity(player, 4);
                return true;
            }));
        // 8) Agirliksizlik
        registerEvent(EventDef.of("weightless").name("Weightless").minDay(6).weight(2).cooldown(18L * TICKS_PER_MIN)
            .cond(p -> safe(() -> p.isOnGround(), true) && !isUnderground(p))
            .run(player -> {
                effect(player, "levitation", 24, 1);
                Sys.runTimeout(() -> { if (isValid(player)) effect(player, "slow_falling", 140, 0); }, 22);
                actionbar(player, tx("§8(yer seni biraktı)§r", "§8(the ground let go of you)§r"));
                playTo(player, "duskwalker.drone", 0.5, 1.4);
                loseSanity(player, 3);
                return true;
            }));
        // 9) Donen tabela
        registerEvent(EventDef.of("sign_turn").name("Sign Turn").minDay(5).weight(2).cooldown(20L * TICKS_PER_MIN)
            .run(Omens::signTurn));
        // 10) Paylasilan nefes
        registerEvent(EventDef.of("shared_breath").name("Shared Breath").minDay(4).weight(3).cooldown(14L * TICKS_PER_MIN)
            .run(Omens::sharedBreath));

        // 11) Sayi istasyonu
        try {
            Events.Before.chatSend.subscribe(ev -> {
                try {
                    String m = ev.message;
                    if (m == null || (!m.isEmpty() && m.charAt(0) == '!')) return;
                    Long until = awaiting.get(ev.sender.id());
                    if (until != null && until != 0 && Sys.currentTick() < until) {
                        awaiting.remove(ev.sender.id());
                        Player p = ev.sender;
                        Sys.run(() -> {
                            if (!isValid(p)) return;
                            say(p, tx("§8...duydu.§r", "§8...it heard you.§r"));
                            playTo(p, "duskwalker.whisper", 0.8, 0.6);
                            loseSanity(p, 7);
                        });
                    }
                } catch (Throwable e) { /* yut */ }
            });
        } catch (Throwable e) { /* yut */ }

        registerEvent(EventDef.of("numbers_station").name("Numbers Station").minDay(7).weight(2).cooldown(22L * TICKS_PER_MIN)
            .cond(p -> isNight())
            .run(Omens::numbersStation));

        // 12) Guveler
        registerEvent(EventDef.of("moths").name("Moths").minDay(3).weight(3).cooldown(12L * TICKS_PER_MIN)
            .cond(p -> isNight())
            .run(Omens::moths));

        // 13) Yanki kazma (olay degil: kazma kancasi)
        try {
            Events.After.playerBreakBlock.subscribe(ev -> {
                try {
                    Player p = ev.player;
                    if (!isEnabled("echo_mine") || !isSurvival(p) || !isUnderground(p)) return;
                    long tick = Sys.currentTick();
                    if (tick < echoCd.getOrDefault(p.id(), 0L) || !chance(0.04)) return;
                    echoCd.put(p.id(), tick + 10L * TICKS_PER_MIN);
                    Vec3 v = p.getViewDirection();
                    Vec3 l = p.location();
                    Vec3 at = new Vec3(l.x() + v.x() * 7, l.y() + 1, l.z() + v.z() * 7);
                    for (int i = 0; i < 3; i++) Sys.runTimeout(() -> safe(() -> playAt(p.dimension(), "dig.stone", at, 1.0, 0.8)), 30 + i * 12);
                    Sys.runTimeout(() -> {
                        if (isValid(p)) { playAt(p.dimension(), "duskwalker.whisper", at, 0.9, 0.7); loseSanity(p, 5); addStat(p, "ev_echo_mine"); }
                    }, 90);
                } catch (Throwable e) { /* yut */ }
            });
        } catch (Throwable e) { /* yut */ }

        // 14) Kulak cinlamasi
        registerEvent(EventDef.of("tinnitus").name("Tinnitus").minDay(6).weight(3).cooldown(14L * TICKS_PER_MIN)
            .run(player -> {
                playTo(player, "duskwalker.ring", 0.9, 1.0);
                fade(player, 1, 1, 1, 0.05, 0.15, 1.4);
                effect(player, "slowness", 50, 0);
                actionbar(player, tx("§8(kulaklarin cinliyor...)§r", "§8(your ears ring...)§r"));
                loseSanity(player, 3);
                return true;
            }));

        // 15) Yalan ganimet
        registerEvent(EventDef.of("fake_loot").name("Mirage Loot").minDay(5).weight(3).cooldown(16L * TICKS_PER_MIN)
            .run(Omens::fakeLoot));

        // 16) Yalan sakinlik
        registerEvent(EventDef.of("the_lull").name("The Lull").minDay(8).weight(2).cooldown(28L * TICKS_PER_MIN)
            .cond(p -> isNight())
            .run(Omens::theLull));
    }

    // ---------------------------------------------------------
    private static Boolean lanternThief(Player player) {
        Dimension dim = player.dimension();
        Block b = findBlock(player, 9, x -> isLight(x.typeId()) && hdist(x.location(), player.location()) > 3);
        if (b == null) return false;
        String type = b.typeId();
        Perm perm = b.permutation();
        Vec3 pos = new Vec3(b.x(), b.y(), b.z());
        Vec3 far = standSpot(dim, behindPlayer(player, 26), 0, 6, 16);
        if (far == null) return false;
        safe(() -> b.setType("minecraft:air"));
        smoke(dim, new Vec3(pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5), 5);
        playAt(dim, "random.fizz", pos, 0.8, 0.7);
        Vec3 farPos = new Vec3(Math.floor(far.x()), Math.floor(far.y()), Math.floor(far.z()));
        safe(() -> dim.getBlock(farPos).setType(type));
        say(player, tx("§8(bir isik... tasindi)§r", "§8(a light... was carried away)§r"));
        // 80 sn sonra yerine doner, uzaktaki kaybolur
        Sys.runTimeout(() -> {
            safe(() -> { Block f = dim.getBlock(farPos); if (f != null && f.typeId().equals(type)) f.setType("minecraft:air"); });
            safe(() -> { Block o = dim.getBlock(pos); if (o != null && o.typeId().equals("minecraft:air")) o.setPermutation(perm); });
        }, 80 * TICKS_PER_SEC);
        loseSanity(player, 4);
        return true;
    }

    private static Boolean footprintTrail(Player player) {
        Dimension dim = player.dimension();
        Vec3 l = player.location();
        double a = Math.random() * Math.PI * 2;
        double startX = l.x() + Math.cos(a) * 26, startZ = l.z() + Math.sin(a) * 26;
        double endX = l.x() + Math.cos(a) * 3.2, endZ = l.z() + Math.sin(a) * 3.2;
        int steps = 26;
        int[] i = { 0 };
        int[] handle = { 0 };
        handle[0] = Sys.runInterval(() -> {
            try {
                if (!isValid(player) || i[0] >= steps) {
                    Sys.clearRun(handle[0]);
                    if (isValid(player) && i[0] >= steps) { loseSanity(player, 5); playTo(player, "duskwalker.breath", 0.5, 0.7); }
                    return;
                }
                double f = i[0] / (double) (steps - 1);
                double side = (i[0] % 2 == 0 ? 1 : -1) * 0.22;
                double x = startX + (endX - startX) * f - Math.sin(a) * side;
                double z = startZ + (endZ - startZ) * f + Math.cos(a) * side;
                Integer y = groundY(dim, x, l.y(), z);
                if (y != null) {
                    particle(dim, "minecraft:soul_particle", new Vec3(x, y + 0.1, z));
                    playAt(dim, "step.stone", new Vec3(x, y, z), 0.5, 0.6);
                }
                i[0]++;
            } catch (Throwable e) { safe(() -> Sys.clearRun(handle[0])); }
        }, 8);
        return true;
    }

    private static Boolean batOmen(Player player) {
        Dimension dim = player.dimension();
        Vec3 l = player.location();
        double a = Math.random() * Math.PI * 2;
        double dirx = Math.cos(a), dirz = Math.sin(a);
        List<Entity> bats = new ArrayList<>();
        for (int k = 0; k < 6; k++) {
            try { bats.add(dim.spawnEntity("minecraft:bat", new Vec3(l.x() - dirx * 9 + rand(-2, 2), l.y() + 2 + rand(0, 2), l.z() - dirz * 9 + rand(-2, 2)))); } catch (Throwable e) { /* yut */ }
        }
        if (bats.isEmpty()) return false;
        int[] n = { 0 };
        int[] handle = { 0 };
        handle[0] = Sys.runInterval(() -> {
            try {
                n[0]++;
                for (Entity b : bats) if (isValid(b)) safe(() -> b.applyImpulse(new Vec3(dirx * 0.12, 0.015, dirz * 0.12)));
                if (n[0] >= 40) {
                    Sys.clearRun(handle[0]);
                    for (Entity b : bats) if (isValid(b)) safe(() -> b.kill());
                }
            } catch (Throwable e) { safe(() -> Sys.clearRun(handle[0])); }
        }, 10);
        playTo(player, "duskwalker.whisper", 0.5, 1.2);
        actionbar(player, tx("§8(hepsi ayni yone ucuyor...)§r", "§8(they all fly the same way...)§r"));
        loseSanity(player, 3);
        return true;
    }

    private static Boolean glitchHud(Player player) {
        String chars = "#%&@?!*/\\|<>=+";
        int[] n = { 0 };
        int[] handle = { 0 };
        handle[0] = Sys.runInterval(() -> {
            try {
                n[0]++;
                if (!isValid(player) || n[0] > 14) {
                    Sys.clearRun(handle[0]);
                    if (isValid(player)) actionbar(player, "");
                    return;
                }
                StringBuilder s = new StringBuilder("§8");
                for (int i = 0; i < 24; i++) s.append(Math.random() < 0.25 ? "§4" : "§8").append(chars.charAt(randInt(0, chars.length() - 1)));
                actionbar(player, s.toString());
                if (n[0] % 4 == 0) playTo(player, "duskwalker.tick", 0.4, rand(0.6, 1.6));
            } catch (Throwable e) { safe(() -> Sys.clearRun(handle[0])); }
        }, 3);
        loseSanity(player, 3);
        return true;
    }

    private static Boolean circlingBells(Player player) {
        Dimension dim = player.dimension();
        int[] k = { 0 };
        int[] handle = { 0 };
        handle[0] = Sys.runInterval(() -> {
            try {
                if (!isValid(player) || k[0] >= 14) {
                    Sys.clearRun(handle[0]);
                    if (isValid(player)) { playTo(player, "duskwalker.whisper", 0.7, 0.8); loseSanity(player, 4); }
                    return;
                }
                Vec3 l = player.location();
                double ang = (Math.PI * 2 * k[0]) / 14 * 1.0;
                double r = 9 - k[0] * 0.3;
                playAt(dim, "block.bell.hit", new Vec3(l.x() + Math.cos(ang) * r, l.y() + 1, l.z() + Math.sin(ang) * r), 1.4, 0.5 + k[0] * 0.02);
                k[0]++;
            } catch (Throwable e) { safe(() -> Sys.clearRun(handle[0])); }
        }, 10);
        return true;
    }

    private static Boolean musicBox(Player player) {
        Dimension dim = player.dimension();
        Vec3 l = player.location();
        double a = Math.random() * Math.PI * 2;
        Vec3 at = new Vec3(l.x() + Math.cos(a) * 18, l.y() + 1, l.z() + Math.sin(a) * 18);
        for (int i = 0; i < LULLABY.length; i++) {
            final int idx = i;
            double pitch = LULLABY[i];
            boolean last = i == LULLABY.length - 1;
            Sys.runTimeout(() -> {
                playAt(dim, "note.harp", at, last ? 0.4 : 0.9, last ? pitch * 0.9 : pitch);
                if (idx % 3 == 0) particle(dim, "minecraft:note_particle", at);
            }, i * 15 + (i > 6 ? 6 : 0));
        }
        Sys.runTimeout(() -> {
            if (!isValid(player)) return;
            playAt(dim, "duskwalker.creak", at, 0.8, 0.7);
            actionbar(player, tx("§8(ninni yarim kaldi...)§r", "§8(the lullaby ended halfway...)§r"));
            loseSanity(player, 3);
        }, LULLABY.length * 15 + 20);
        return true;
    }

    private static Boolean signTurn(Player player) {
        Block b = findBlock(player, 14, x -> x.typeId().indexOf("sign") >= 0, 200);
        if (b == null) return false;
        String original = safe(() -> b.getSignText(), (String) null);
        if (original == null) return false;
        Vec3 pos = new Vec3(b.x(), b.y(), b.z());
        safe(() -> b.setSignText(tx("YUKARI BAK", "LOOK UP")));
        Dimension dim = player.dimension();
        Sys.runTimeout(() -> safe(() -> dim.getBlock(pos).setSignText(original)), 35 * TICKS_PER_SEC);
        playTo(player, "dig.wood", 0.6, 0.6);
        loseSanity(player, 3);
        return true;
    }

    private static Boolean sharedBreath(Player player) {
        Dimension dim = player.dimension();
        int[] n = { 0 };
        int[] handle = { 0 };
        handle[0] = Sys.runInterval(() -> {
            try {
                n[0]++;
                if (!isValid(player) || n[0] > 7) {
                    Sys.clearRun(handle[0]);
                    if (isValid(player)) {
                        actionbar(player, tx("§8(o nefeslerden biri senin degildi)§r", "§8(one of those breaths wasn't yours)§r"));
                        loseSanity(player, 5);
                    }
                    return;
                }
                Vec3 e = eyePos(player), v = player.getViewDirection();
                particle(dim, "minecraft:basic_smoke_particle", new Vec3(e.x() + v.x() * 0.45, e.y() - 0.1, e.z() + v.z() * 0.45));
                Sys.runTimeout(() -> {
                    if (!isValid(player)) return;
                    Vec3 b = behindPlayer(player, 0.5);
                    particle(dim, "minecraft:basic_smoke_particle", new Vec3(b.x(), b.y() + 1.5, b.z()));
                    playAt(dim, "duskwalker.breath", new Vec3(b.x(), b.y() + 1.5, b.z()), 0.35, 1.0);
                }, 12);
            } catch (Throwable e) { safe(() -> Sys.clearRun(handle[0])); }
        }, 60);
        return true;
    }

    private static Boolean numbersStation(Player player) {
        int[] nums = new int[6];
        for (int i = 0; i < 6; i++) nums[i] = randInt(10, 99);
        for (int i = 0; i < nums.length; i++) {
            int n = nums[i];
            int idx = i;
            Sys.runTimeout(() -> {
                if (!isValid(player)) return;
                say(player, "§8" + n);
                playTo(player, "duskwalker.whisper", 0.45, 0.9 + (idx % 3) * 0.1);
            }, 30 + i * 40);
        }
        Sys.runTimeout(() -> {
            if (!isValid(player)) return;
            say(player, tx("§4Duskwalker§r: ...sira sende. (cevap verme)", "§4Duskwalker§r: ...your turn. (do not answer)"));
            awaiting.put(player.id(), Sys.currentTick() + 12L * TICKS_PER_SEC);
            Sys.runTimeout(() -> {
                if (!isValid(player)) return;
                Long w = awaiting.get(player.id());
                if (w != null && w != 0) {
                    awaiting.remove(player.id());
                    gainSanity(player, 3);
                    actionbar(player, tx("§7(sustun. iyi.)§r", "§7(you stayed silent. good.)§r"));
                    addStat(player, "numbersSilent");
                }
            }, 12 * TICKS_PER_SEC + 4);
        }, 30 + nums.length * 40);
        return true;
    }

    private static Boolean moths(Player player) {
        Dimension dim = player.dimension();
        Block lamp = findBlock(player, 10, x -> isLight(x.typeId()));
        if (lamp == null) return false;
        Vec3 c = new Vec3(lamp.x() + 0.5, lamp.y() + 0.6, lamp.z() + 0.5);
        int[] n = { 0 };
        int[] handle = { 0 };
        handle[0] = Sys.runInterval(() -> {
            try {
                n[0]++;
                if (!isValid(player)) { Sys.clearRun(handle[0]); return; }
                double t = n[0] * 0.5;
                boolean swirling = n[0] <= 36;
                for (int k = 0; k < 4; k++) {
                    double ph = t + k * 1.57;
                    if (swirling) {
                        particle(dim, "minecraft:basic_smoke_particle", new Vec3(c.x() + Math.cos(ph) * 0.9, c.y() + Math.sin(ph * 1.7) * 0.4, c.z() + Math.sin(ph) * 0.9));
                    } else {
                        double f = Math.min(1, (n[0] - 36) / 14.0);
                        Vec3 e = eyePos(player);
                        particle(dim, "minecraft:basic_smoke_particle", new Vec3(
                            c.x() + (e.x() - c.x()) * f + Math.cos(ph) * 0.3 * (1 - f),
                            c.y() + (e.y() - c.y()) * f,
                            c.z() + (e.z() - c.z()) * f + Math.sin(ph) * 0.3 * (1 - f)));
                    }
                }
                if (n[0] == 50) { Sys.clearRun(handle[0]); playTo(player, "duskwalker.whisper", 0.6, 1.3); loseSanity(player, 4); }
            } catch (Throwable e) { safe(() -> Sys.clearRun(handle[0])); }
        }, 3);
        return true;
    }

    private static Boolean fakeLoot(Player player) {
        Dimension dim = player.dimension();
        Vec3 spot = standSpot(dim, frontPlayer(player, 15), 0, 4, 18);
        if (spot == null) return false;
        Entity item;
        try { item = dim.spawnItem(new ItemStack("minecraft:diamond", 1), new Vec3(spot.x(), spot.y() + 0.3, spot.z())); } catch (Throwable e) { return false; }
        if (item == null) return false;
        Entity fitem = item;
        int[] n = { 0 };
        int[] handle = { 0 };
        handle[0] = Sys.runInterval(() -> {
            try {
                n[0] += 5;
                if (!isValid(player) || !isValid(fitem)) { Sys.clearRun(handle[0]); return; }
                if (dist3(fitem.location(), player.location()) < 6 || n[0] > 20 * 60) {
                    Sys.clearRun(handle[0]);
                    Vec3 at = fitem.location();
                    safe(() -> fitem.kill());
                    smoke(dim, at, 6);
                    playTo(player, "duskwalker.whisper", 0.7, 0.9);
                    actionbar(player, tx("§8(hic orada degildi)§r", "§8(it was never there)§r"));
                    loseSanity(player, 4);
                    addStat(player, "mirageSeen");
                }
            } catch (Throwable e) { safe(() -> Sys.clearRun(handle[0])); }
        }, 5);
        return true;
    }

    private static Boolean theLull(Player player) {
        lullUntil.put(player.id(), Sys.currentTick() + 75L * TICKS_PER_SEC);
        actionbar(player, tx("§7(her sey sakin...)§r", "§7(everything is calm...)§r"));
        gainSanity(player, 4);
        Sys.runTimeout(() -> {
            if (!isValid(player)) return;
            lullUntil.remove(player.id());
            List<String> order = new ArrayList<>();
            for (String id : FOLLOWUPS) {
                EventDef d = getEventDef(id);
                if (d != null && d.minDay <= 30) order.add(id);
            }
            Collections.shuffle(order);
            for (String id : order) if (triggerEvent(id, player, true)) break;
            addStat(player, "lullBroken");
        }, 60 * TICKS_PER_SEC);
        return true;
    }
}

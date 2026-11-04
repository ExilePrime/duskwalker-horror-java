package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.duskwalker.mod.Core.*;

/**
 * DUSKWALKER v11 - MUSKA / LANET OLAYLARI (haunt.js)
 *  1) haunted_item  2) chest_bite  3) system_glitch  4) candle_gamble
 */
public final class Haunt {
    private Haunt() {}

    // ---------------------------------------------------------
    //  1) LANETLI ESYA
    // ---------------------------------------------------------
    private static final String HAUNT_MARK = "§8​"; // gorunmez isaret: ayni esya iki kez lanetlenmesin

    private static final String[][] HAUNT_LORE = {
        { "§8Onu en son tutan hala bekliyor.", "§8Whoever held it last is still waiting." },
        { "§8Sicak olmasi gerekirdi.", "§8It should have been warm." },
        { "§8Gece boyunca agirlasiyor.", "§8It grows heavier through the night." },
        { "§8Birinin adi kazinmis, silinmis.", "§8A name was scratched into it, then erased." },
        { "§8Sana ait degildi.", "§8It was never yours." },
        { "§8Fisildiyor... dinleme.", "§8It whispers... don't listen." },
        { "§8Onu kaybetmeyi dusunme.", "§8Don't think about losing it." }
    };

    private static String prettyName(String typeId) {
        String raw = typeId.replace("minecraft:", "").replace("_", " ");
        if (raw.isEmpty()) return raw;
        return raw.substring(0, 1).toUpperCase() + raw.substring(1);
    }

    // ---------------------------------------------------------
    //  2) SANDIK ISIRIGI
    // ---------------------------------------------------------
    private static final Map<String, Long> biteCooldown = new HashMap<>(); // oyuncu id -> bitis tiki

    // ---------------------------------------------------------
    //  4) MUM KUMARI
    // ---------------------------------------------------------
    private static final int RING_RADIUS = 3;

    private static List<int[]> ringPoints(Vec3 center) {
        List<int[]> pts = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            double a = (Math.PI * 2 * i) / 8;
            pts.add(new int[] {
                (int) Math.floor(center.x() + Math.cos(a) * RING_RADIUS),
                (int) Math.floor(center.z() + Math.sin(a) * RING_RADIUS)
            });
        }
        return pts;
    }

    private static boolean canHoldTorch(Dimension dimension, int x, int y, int z) {
        return safe(() -> {
            Block b = dimension.getBlock(new Vec3(x, y, z));
            Block below = dimension.getBlock(new Vec3(x, y - 1, z));
            return b != null && b.typeId().equals("minecraft:air") && below != null && !below.typeId().equals("minecraft:air") &&
                !below.typeId().contains("water") && !below.typeId().contains("lava") && !below.typeId().contains("leaves");
        }, false);
    }

    private static void resolveGamble(Player player, Dimension dimension, Vec3 center) {
        double roll = Math.max(0, Math.random() - (hasPerk(player, "lucky_hand") ? 0.1 : 0));
        if (roll < 0.40) {
            gainSanity(player, 25);
            effect(player, "regeneration", 200, 0);
            title(player, tx("§bNimet", "§bBlessing"), tx("§7mumlar sicak yandi", "§7the candles burned warm"), 50);
            playTo(player, "random.levelup", 0.7, 0.8);
            addStat(player, "gambleBless");
        } else if (roll < 0.75) {
            loseSanity(player, 20);
            effect(player, "darkness", 200, 0);
            effect(player, "slowness", 160, 1);
            title(player, tx("§4Lanet", "§4Curse"), tx("§7mumlar soguk yandi", "§7the candles burned cold"), 50);
            playTo(player, "mob.wither.ambient", 0.8, 0.5);
            addStat(player, "gambleCurse");
        } else if (roll < 0.90) {
            addEvidence(player, 3);
            Vec3 spot = standSpot(dimension, center, 10, 14, 12);
            if (spot != null) {
                Entity st = spawnEffigy(dimension, spot, 3);
                if (st != null) {
                    safe(() -> st.teleport(spot, Opts.Teleport.dim(dimension).facing(eyePos(player))));
                    Sys.runTimeout(() -> despawn(st, true), 120);
                }
            }
            title(player, tx("§8Alamet", "§8Omen"), tx("§7biri seni izliyor", "§7something watches you"), 50);
            playTo(player, "mob.endermen.stare", 0.8, 0.5);
            addStat(player, "gambleOmen");
        } else {
            giveItem(player, "minecraft:golden_apple", 1);
            title(player, tx("§6Hediye", "§6Gift"), tx("§7karanlik bile cimri degil", "§7even the dark is generous"), 50);
            playTo(player, "random.orb", 0.8, 1.2);
            addStat(player, "gambleGift");
        }
    }

    public static void init() {
        // ---- 1) LANETLI ESYA ----
        registerEvent(EventDef.of("haunted_item").name("Haunted Item").minDay(5).weight(3).cooldown(16L * TICKS_PER_MIN)
            .cond(p -> isNight())
            .run(player -> {
                Container container = safe(() -> player.inventory(), (Container) null);
                if (container == null) return false;
                List<Integer> slots = new ArrayList<>();
                for (int i = 0; i < container.size(); i++) {
                    final int idx = i;
                    ItemStack it = safe(() -> container.getItem(idx), (ItemStack) null);
                    if (it == null) continue;
                    List<String> lore = safe(() -> it.getLore(), new ArrayList<String>());
                    if (lore == null) lore = new ArrayList<>();
                    boolean marked = false;
                    for (String l : lore) if (l.indexOf(HAUNT_MARK) == 0) { marked = true; break; }
                    if (marked) continue;
                    slots.add(i);
                }
                if (slots.isEmpty()) return false;
                int slot = pick(slots);
                try {
                    ItemStack it = container.getItem(slot);
                    it.nameTag = tx("§8Soguk ", "§8Cold ") + prettyName(it.typeId);
                    it.setLore(List.of(HAUNT_MARK + tx(pick(HAUNT_LORE))));
                    container.setItem(slot, it);
                } catch (Throwable e) { return false; }
                playTo(player, "mob.endermen.idle", 0.5, 0.5);
                actionbar(player, tx("§8(cebinde bir sey sogudu...)§r", "§8(something in your pocket went cold...)§r"));
                loseSanity(player, 2);
                return true;
            }));

        // ---- 2) SANDIK ISIRIGI ----
        try {
            Events.After.playerInteractWithBlock.subscribe(ev -> {
                try {
                    if (!isEnabled("chest_bite") || !isNight() || getDay() < 8) return;
                    Player p = ev.player;
                    if (!isSurvival(p)) return;
                    String id = ev.block.typeId();
                    if (!id.equals("minecraft:chest") && !id.equals("minecraft:trapped_chest") && !id.equals("minecraft:barrel")) return;
                    long tick = Sys.currentTick();
                    if (tick < biteCooldown.getOrDefault(p.id(), 0L)) return;
                    if (!chance(0.05)) return;
                    biteCooldown.put(p.id(), tick + 15L * TICKS_PER_MIN);
                    Sys.runTimeout(() -> {
                        if (!isValid(p)) return;
                        playTo(p, "random.chestclosed", 1.0, 0.5);
                        playTo(p, "mob.wolf.growl", 0.9, 0.6);
                        shake(p, 0.16, 0.7);
                        safe(() -> p.applyDamage(3));
                        loseSanity(p, 8);
                        addEvidence(p, 1);
                        addStat(p, "ev_chest_bite");
                        say(p, tx("§c(sandik seni isirdi)§r", "§c(the chest bit you)§r"));
                    }, 4);
                } catch (Throwable e) { /* yut */ }
            });
        } catch (Throwable e) { /* yut */ }

        try {
            Events.After.playerLeave.subscribe(ev -> { biteCooldown.remove(ev.playerId); });
        } catch (Throwable e) { /* yut */ }

        // ---- 3) SISTEM ARIZASI ----
        registerEvent(EventDef.of("system_glitch").name("System Glitch").minDay(6).weight(2).cooldown(20L * TICKS_PER_MIN)
            .cond(p -> !getPrefs(p).nochat)
            .run(player -> {
                Vec3 l = player.location();
                long cx = (long) Math.floor(l.x() / 16), cz = (long) Math.floor(l.z() / 16);
                int online = World.getPlayers().size();
                String[][] lines = {
                    { "§7[Server] Autosave: 0 / 1 chunks", "§7[Server] Autosave: 0 / 1 chunks" },
                    { "§7[Server] Hata: chunk (" + cx + ", " + cz + ") icinde bilinmeyen varlik", "§7[Server] Error: unknown entity in chunk (" + cx + ", " + cz + ")" },
                    { "§7[Server] Varlik 'duskwalker' silinemiyor", "§7[Server] Entity 'duskwalker' cannot be removed" },
                    { "§c[Server] Oyuncu sayisi uyusmuyor: beklenen " + online + ", bulunan " + (online + 1), "§c[Server] Player count mismatch: expected " + online + ", found " + (online + 1) },
                    { "§7[Server] Dunya yeniden yukleniyor...", "§7[Server] Reloading world..." },
                    { "§7[Server] ...basarisiz. Devam ediliyor.", "§7[Server] ...failed. Continuing." }
                };
                int delay = 0;
                for (String[] pair : lines) {
                    String text = tx(pair);
                    delay += randInt(25, 60);
                    Sys.runTimeout(() -> {
                        if (!isValid(player)) return;
                        say(player, text);
                        playTo(player, "random.click", 0.5, 0.8);
                    }, delay);
                }
                Sys.runTimeout(() -> {
                    if (!isValid(player)) return;
                    loseSanity(player, 5);
                    playTo(player, "ambient.cave", 0.7, 0.5);
                }, delay + 30);
                return true;
            }));

        // ---- 4) MUM KUMARI ----
        registerEvent(EventDef.of("candle_gamble").name("Candle Gamble").minDay(5).weight(2).cooldown(28L * TICKS_PER_MIN)
            .cond(p -> isNight())
            .run(player -> {
                Dimension dimension = player.dimension();
                Vec3 spot = standSpot(dimension, frontPlayer(player, 10), 0, 4, 20);
                if (spot == null) return false;
                Vec3 center = new Vec3(spot.x(), Math.floor(spot.y()), spot.z());
                int cy = (int) center.y();
                List<Vec3> placed = new ArrayList<>();
                for (int[] p : ringPoints(center)) {
                    if (!canHoldTorch(dimension, p[0], cy, p[1])) continue;
                    safe(() -> {
                        dimension.getBlock(new Vec3(p[0], cy, p[1])).setType("minecraft:soul_torch");
                        placed.add(new Vec3(p[0], cy, p[1]));
                    });
                }
                if (placed.size() < 5) {
                    for (Vec3 q : placed) safe(() -> dimension.getBlock(q).setType("minecraft:air"));
                    return false;
                }
                playAt(dimension, "random.fizz", center, 0.8, 0.6);
                say(player, tx("§8(uzakta bir mum halkasi yandi... icine girmek senin kararin)§r", "§8(a ring of candles lit in the distance... entering is your choice)§r"));

                java.util.function.Consumer<Boolean> cleanup = withSmoke -> {
                    for (Vec3 q : placed) {
                        safe(() -> {
                            Block b = dimension.getBlock(q);
                            if (b != null && b.typeId().equals("minecraft:soul_torch")) b.setType("minecraft:air");
                        });
                    }
                    if (withSmoke) smoke(dimension, new Vec3(center.x(), center.y() + 0.5, center.z()), 10);
                };

                final int[] ticks = { 0 };
                final int[] handle = { 0 };
                handle[0] = Sys.runInterval(() -> {
                    try {
                        ticks[0] += 10;
                        if (!isValid(player)) { Sys.clearRun(handle[0]); cleanup.accept(false); return; }
                        Vec3 l = player.location();
                        if (player.dimension().id().equals(dimension.id()) && hdist(l, center) < 1.4 && Math.abs(l.y() - center.y()) < 2.2) {
                            Sys.clearRun(handle[0]);
                            resolveGamble(player, dimension, center);
                            cleanup.accept(true);
                            return;
                        }
                        // yanma efekti
                        if (ticks[0] % 40 == 0) particle(dimension, "minecraft:soul_particle", new Vec3(center.x(), center.y() + 1, center.z()));
                        if (ticks[0] > 20 * 60 * 4) { Sys.clearRun(handle[0]); cleanup.accept(true); }
                    } catch (Throwable e) { safe(() -> Sys.clearRun(handle[0])); cleanup.accept(false); }
                }, 10);
                return true;
            }));
    }
}

package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static com.duskwalker.mod.Core.*;

/**
 * DUSKWALKER v10 - SES TABANLI OLAYLAR (events_audio.js)
 * 1) Gecikmeli Adimlar 2) Hayalet Sesler 3) Sahte Hasar 4) Kapi Vurusu 5) Uzak Can
 */
public final class EventsAudio {
    private EventsAudio() {}

    // ---------------------------------------------------------
    //  1) GECIKMELI ADIMLAR
    // ---------------------------------------------------------
    private static final Pattern[] STEP_RE = {
        Pattern.compile("grass|dirt|podzol|moss|mycelium|farmland"),
        Pattern.compile("stone|deepslate|cobble|brick|andesite|diorite|granite|tuff|basalt|blackstone|obsidian"),
        Pattern.compile("gravel|clay|mud"),
        Pattern.compile("sand"),
        Pattern.compile("plank|log|wood|stem|hyphae|door|bamboo"),
        Pattern.compile("snow|powder"),
        Pattern.compile("wool|carpet|moss_carpet")
    };
    private static final String[] STEP_SND = {
        "step.grass", "step.stone", "step.gravel", "step.sand", "step.wood", "step.snow", "step.cloth"
    };

    private static String stepSoundFor(Player player) {
        String id = safe(() -> {
            Vec3 l = player.location();
            return player.dimension().getBlock(new Vec3(Math.floor(l.x()), Math.floor(l.y()) - 1, Math.floor(l.z()))).typeId();
        }, "minecraft:stone");
        for (int i = 0; i < STEP_RE.length; i++) if (STEP_RE[i].matcher(id).find()) return STEP_SND[i];
        return "step.stone";
    }

    // ---------------------------------------------------------
    //  2) HAYALET SESLER
    // ---------------------------------------------------------
    private static final Object[][] PHANTOM_SOUNDS = {
        {"mob.creeper.say", 0.9, 1.0},
        {"mob.zombie.say", 0.8, 0.7},
        {"mob.skeleton.say", 0.8, 0.8},
        {"mob.spider.say", 0.8, 0.9},
        {"mob.endermen.idle", 0.9, 0.6},
        {"mob.wolf.growl", 0.8, 0.8},
        {"mob.ghast.moan", 0.6, 0.7},
        {"random.chestopen", 1.0, 0.9},
        {"random.chestclosed", 1.0, 0.9},
        {"random.anvil_land", 0.7, 0.8},
        {"open.wooden_door", 1.0, 0.8},
        {"close.wooden_door", 1.0, 0.8},
        {"ambient.cave", 0.8, 1.0}
    };

    // ---------------------------------------------------------
    //  4) KAPI VURUSU
    // ---------------------------------------------------------
    private static Vec3 findNearbyDoor(Player player, int radius) {
        Dimension dimension = player.dimension();
        Vec3 base = player.location();
        int bx = (int) Math.floor(base.x()), by = (int) Math.floor(base.y()), bz = (int) Math.floor(base.z());
        List<Vec3> found = new ArrayList<>();
        for (int attempt = 0; attempt < 140; attempt++) {
            Vec3 pos = new Vec3(bx + randInt(-radius, radius), by + randInt(-2, 3), bz + randInt(-radius, radius));
            Block block = safe(() -> dimension.getBlock(pos), (Block) null);
            if (block == null) continue;
            String t = block.typeId();
            if (t.contains("door") && !t.contains("trapdoor") && !t.contains("iron")) {
                if (hdist(pos, base) >= 4) found.add(pos);
                if (found.size() >= 3) break;
            }
        }
        return found.isEmpty() ? null : pick(found);
    }

    public static void init() {
        // 1) Gecikmeli Adimlar
        registerEvent(EventDef.of("delayed_steps").name("Gecikmeli Adimlar").minDay(3).weight(3).cooldown(9L * TICKS_PER_MIN)
            .cond(p -> safe(() -> p.isOnGround(), true))
            .run(player -> {
                Dimension dimension = player.dimension();
                String sound = stepSoundFor(player);
                List<Vec3> queue = new ArrayList<>();
                int[] ticks = {0};
                int[] stillCount = {0};
                double[] last = {player.location().x(), player.location().z()};
                int[] handle = {0};

                handle[0] = Sys.runInterval(() -> {
                    try {
                        if (!isValid(player) || !player.dimension().id().equals(dimension.id())) { Sys.clearRun(handle[0]); return; }
                        Vec3 l = player.location();
                        double moved = Math.hypot(l.x() - last[0], l.z() - last[1]);
                        last[0] = l.x(); last[1] = l.z();
                        stillCount[0] = moved < 0.15 ? stillCount[0] + 1 : 0;
                        queue.add(new Vec3(l.x(), l.y(), l.z()));
                        ticks[0]++;

                        // 3 adim geriden (~1.2 sn) oynat; oyuncu durduktan sonra da kuyruk bosalir
                        if (queue.size() > 3) {
                            Vec3 at = queue.remove(0);
                            playAt(dimension, sound, at, 1.1, rand(0.75, 0.9));
                        }
                        if (ticks[0] > 26 || (stillCount[0] >= 5 && queue.size() <= 3)) {
                            Sys.clearRun(handle[0]);
                            // son adim tam yaninda: oyuncu durmus ama adimlar sana ulasti
                            Sys.runTimeout(() -> {
                                if (!isValid(player)) return;
                                playAt(dimension, sound, behindPlayer(player, 1.2), 1.4, 0.7);
                                loseSanity(player, 4);
                            }, 10);
                        }
                    } catch (Throwable e) { safe(() -> Sys.clearRun(handle[0])); }
                }, 8);
                return true;
            }));

        // 2) Hayalet Sesler
        registerEvent(EventDef.of("phantom_sound").name("Hayalet Sesler").minDay(2).weight(5).cooldown(5L * TICKS_PER_MIN)
            .run(player -> {
                Dimension dimension = player.dimension();
                int count = randInt(1, 3);
                for (int i = 0; i < count; i++) {
                    Sys.runTimeout(() -> {
                        if (!isValid(player)) return;
                        double a = Math.random() * Math.PI * 2;
                        double d = rand(7, 16);
                        Vec3 l = player.location();
                        Object[] s = pick(PHANTOM_SOUNDS);
                        playAt(dimension, (String) s[0], new Vec3(l.x() + Math.cos(a) * d, l.y(), l.z() + Math.sin(a) * d),
                            ((Number) s[1]).doubleValue(), ((Number) s[2]).doubleValue());
                    }, i * randInt(30, 90));
                }
                loseSanity(player, 2);
                return true;
            }));

        // 3) Sahte Hasar
        registerEvent(EventDef.of("fake_damage").name("Sahte Hasar").minDay(6).weight(2).cooldown(14L * TICKS_PER_MIN)
            .cond(p -> getSanity(p) <= 92)
            .run(player -> {
                playTo(player, "game.player.hurt", 1.0, rand(0.9, 1.05));
                fade(player, 0.55, 0, 0, 0.05, 0.1, 0.45);
                shake(player, 0.12, 0.8);
                Sys.runTimeout(() -> {
                    if (isValid(player)) actionbar(player, "§8...");
                }, 10);
                loseSanity(player, 5);
                addEvidence(player, 1);
                return true;
            }));

        // 4) Kapi Vurusu
        registerEvent(EventDef.of("door_knock").name("Kapi Vurusu").minDay(4).weight(3).cooldown(10L * TICKS_PER_MIN)
            .cond(p -> isNight())
            .run(player -> {
                Vec3 door = findNearbyDoor(player, 14);
                if (door == null) return false;
                Dimension dimension = player.dimension();
                int[] knocks = {0, 14, 28};
                for (int i = 0; i < knocks.length; i++) {
                    final int ii = i;
                    Sys.runTimeout(() -> {
                        playAt(dimension, "dig.wood", new Vec3(door.x() + 0.5, door.y() + 0.5, door.z() + 0.5), 1.6, 0.7 + ii * 0.05);
                    }, knocks[i]);
                }
                // 4. vurus: icerden, yani sanki bir sey disarida degil iceride
                Sys.runTimeout(() -> {
                    if (isValid(player)) playTo(player, "dig.wood", 0.9, 0.6);
                }, 80);
                loseSanity(player, 4);
                return true;
            }));

        // 5) Uzak Can
        registerEvent(EventDef.of("far_bell").name("Uzak Can").minDay(8).weight(2).cooldown(16L * TICKS_PER_MIN)
            .cond(p -> isNight())
            .run(player -> {
                Dimension dimension = player.dimension();
                double a = Math.random() * Math.PI * 2;
                Vec3 l = player.location();
                Vec3 at = new Vec3(l.x() + Math.cos(a) * 34, l.y() + 4, l.z() + Math.sin(a) * 34);
                int strikes = randInt(2, 4);
                for (int i = 0; i < strikes; i++) {
                    Sys.runTimeout(() -> {
                        playAt(dimension, "block.bell.hit", at, 2.0, 0.55);
                        particle(dimension, "minecraft:soul_particle", at);
                    }, i * 70);
                }
                Sys.runTimeout(() -> {
                    if (!isValid(player)) return;
                    say(player, "§8(can sesi kesildi... yaklasmak yerine uzaklasti)§r");
                    loseSanity(player, 5);
                }, strikes * 70 + 40);
                return true;
            }));

        // Yer alti: sessizlikte nefes sesi (kucuk, bagimsiz ambiyans)
        Sys.runInterval(() -> {
            for (Player p : World.getPlayers()) {
                try {
                    if (getSanity(p) > 60 || !isUnderground(p) || !chance(0.12)) continue;
                    playTo(p, "mob.warden.heartbeat", 0.5, 0.9);
                } catch (Throwable e) { /* yut */ }
            }
        }, 400);
    }
}

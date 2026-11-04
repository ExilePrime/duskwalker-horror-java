package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import static com.duskwalker.mod.Core.*;

/**
 * DUSKWALKER v11 - ZIHIN OYUNLARI (mind.js)
 * 1) fake_coords 2) slot_drift 3) whisper_title 4) false_dawn
 */
public final class Mind {
    private Mind() {}

    private static final String[][] SUBLIMINAL = {
        {"ARKANDA", "BEHIND YOU"},
        {"BAK", "LOOK"},
        {"BURADA", "HERE"},
        {"GITME", "DON'T GO"},
        {"YALNIZSIN", "ALONE"},
        {"SENI GORDUM", "I SAW YOU"},
        {"KACMA", "DON'T RUN"}
    };

    public static void init() {
        // 1) SAHTE KOORDINAT
        registerEvent(EventDef.of("fake_coords").name("Fake Coordinates").minDay(6).weight(2).cooldown(18L * TICKS_PER_MIN)
            .cond(p -> getSanity(p) <= 80)
            .run(player -> {
                int offX = randInt(120, 900) * (Math.random() < 0.5 ? -1 : 1);
                int offZ = randInt(120, 900) * (Math.random() < 0.5 ? -1 : 1);
                int[] ticks = {0};
                int[] handle = {0};
                handle[0] = Sys.runInterval(() -> {
                    try {
                        ticks[0] += 5;
                        if (!isValid(player)) { Sys.clearRun(handle[0]); return; }
                        Vec3 l = player.location();
                        // sayilar "titrer": gercek konuma bagli ama yanlis
                        int jitter = randInt(-3, 3);
                        int x = (int) Math.floor(l.x()) + offX + jitter, y = (int) Math.floor(l.y()), z = (int) Math.floor(l.z()) + offZ - jitter;
                        actionbar(player, "§7XYZ: §f" + x + " " + y + " " + z);
                        if (ticks[0] >= 20 * 7) {
                            Sys.clearRun(handle[0]);
                            int rx = (int) Math.floor(l.x()), rz = (int) Math.floor(l.z());
                            int ry = (int) Math.floor(l.y());
                            actionbar(player, tx("§7XYZ: §f" + rx + " " + ry + " " + rz + " §8(emin misin?)", "§7XYZ: §f" + rx + " " + ry + " " + rz + " §8(are you sure?)"));
                        }
                    } catch (Throwable e) { safe(() -> Sys.clearRun(handle[0])); }
                }, 5);
                loseSanity(player, 4);
                return true;
            }));

        // 2) KAYAN SLOT
        registerEvent(EventDef.of("slot_drift").name("Slot Drift").minDay(7).weight(2).cooldown(15L * TICKS_PER_MIN)
            .cond(p -> getSanity(p) <= 35)
            .run(player -> {
                try {
                    int cur = player.selectedSlotIndex();
                    player.setSelectedSlotIndex((cur + 1) % 9);
                } catch (Throwable e) { return false; }
                playTo(player, "random.click", 0.5, 0.7);
                loseSanity(player, 2);
                return true;
            }));

        // 3) ALTLIK MESAJ
        registerEvent(EventDef.of("whisper_title").name("Subliminal Message").minDay(5).weight(3).cooldown(9L * TICKS_PER_MIN)
            .cond(p -> getSanity(p) <= 65 && !getPrefs(p).noflash)
            .run(player -> {
                String word = tx(pick(SUBLIMINAL));
                safe(() -> player.setTitle("§4" + word, Opts.Title.of(0, 3, 0, "")));
                loseSanity(player, 3);
                addStat(player, "subliminal");
                return true;
            }));

        // 4) YALAN SAFAK
        registerEvent(EventDef.of("false_dawn").name("False Dawn").minDay(8).weight(2).cooldown(30L * TICKS_PER_MIN)
            .cond(p -> isNight())
            .run(player -> {
                actionbar(player, tx("§eSabah oldu. Bir geceyi daha atlattin.§r", "§eMorning has come. You survived another night.§r"));
                playTo(player, "random.orb", 0.5, 1.4);
                Sys.runTimeout(() -> {
                    if (!isValid(player)) return;
                    actionbar(player, tx("§8Yanlis alarm.§r", "§8False alarm.§r"));
                    playTo(player, "ambient.cave", 0.6, 0.5);
                    loseSanity(player, 6);
                }, 20 * 20);
                return true;
            }));
    }
}

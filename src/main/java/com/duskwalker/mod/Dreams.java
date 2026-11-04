package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.HashMap;
import java.util.Map;

import static com.duskwalker.mod.Core.*;

/**
 * DUSKWALKER v11 - RUYALAR (dreams.js)
 * Yatakta uyurken (3 saniye sonra) bir ruya gorursun; yataktan kalkinca sonuc uygulanir.
 */
public final class Dreams {
    private Dreams() {}

    private static final String[][] PEACEFUL = {
        { "Sicak bir evin kapisindasin. Biri soba yakmis, cay kokusu geliyor.", "You stand at the door of a warm house. Someone lit the stove; it smells of tea." },
        { "Gokyuzunde tek bir yildiz var ve sana bakiyor, ama kotuluk yok.", "A single star hangs in the sky and watches you, without malice." },
        { "Aylin uzaktan el sallıyor. Gulumsuyor.", "Aylin waves from far away. She is smiling." },
        { "Bir golun kenarinda oturuyorsun. Su hic kipirdamiyor.", "You sit by a lake. The water doesn't move at all." },
        { "Butun mesaleler ayni anda yaniyor. Karanlik geri cekiliyor.", "All the torches burn at once. The dark withdraws." },
        { "Eski bir koy, cocuklar ates basinda sarki soyluyor.", "An old village; children sing around a fire." }
    };

    private static final String[][] NEUTRAL = {
        { "Bir kapidan gecip ayni odaya geri dondun. Defalarca.", "You walked through a door and returned to the same room. Over and over." },
        { "Ayna yok, ama yansiman var.", "There is no mirror, but there is a reflection." },
        { "Hic tanimadigin biri adini soyledi. Dogru telaffuz etti.", "A stranger said your name. They pronounced it correctly." },
        { "Merdivenler asagi iniyor ama hep ayni yukseklikte kaliyorsun.", "The stairs go down, but you stay at the same height." },
        { "Yagmur yagiyor ama kimse islanmiyor.", "It rains, but no one gets wet." }
    };

    private static final String[][] NIGHTMARE = {
        { "Kapi hala kapali. Ama icerden biri seni cagiriyor, senin sesinle.", "The door is still closed. Someone calls you from inside, in your own voice." },
        { "Yataginin basinda bir siluet duruyor. Gozlerini actiginda hala orada.", "A silhouette stands at your bedside. When you open your eyes it is still there." },
        { "Ayak seslerin senden once geliyor. Hepsi ayni yone gidiyor: eve.", "Your footsteps arrive before you. All of them head the same way: home." },
        { "Butun mesaleler sonduruldu. Sadece nefes sesi var, bir de senin degil.", "All the torches are out. Only breathing, and it isn't yours." },
        { "Kendi mezar tasini okuyorsun. Tarih yarin.", "You read your own gravestone. The date is tomorrow." },
        { "Duskwalker pencerede, ama bu sefer iceride duruyor.", "Duskwalker is at the window, but this time it stands inside." },
        { "Bir labirentte kosuyorsun. Duvarlar nefes aliyor.", "You run through a maze. The walls are breathing." }
    };

    private static final class Dream {
        final String kind;      // "peace" | "neutral" | "nightmare" | "prophecy"
        final String[] text;    // prophecy icin null
        Dream(String kind, String[] text) { this.kind = kind; this.text = text; }
    }

    private static final class SleepState {
        long since;
        Dream dream;
        SleepState(long since) { this.since = since; }
    }

    private static final Map<String, SleepState> sleeping = new HashMap<>(); // oyuncu id -> { since, dream }

    private static Dream chooseDream(double sanity, boolean weaver) {
        double pPeace, pNeutral;
        if (sanity >= 70) { pPeace = 0.6; pNeutral = 0.3; }
        else if (sanity >= 35) { pPeace = 0.25; pNeutral = 0.35; }
        else { pPeace = 0.08; pNeutral = 0.17; }
        if (weaver) { pPeace += 0.2; pNeutral = Math.max(0.1, pNeutral - 0.1); }
        double r = Math.random();
        if (r < pPeace) return new Dream("peace", pick(PEACEFUL));
        if (r < pPeace + pNeutral) return new Dream("neutral", pick(NEUTRAL));
        if (chance(0.12)) return new Dream("prophecy", null);
        return new Dream("nightmare", pick(NIGHTMARE));
    }

    private static final String[][] MOON_NAMES = {
        { "Dolunay", "Full Moon" }, { "Ikiz Aylar", "Twin Moons" }, { "Ucte Uc", "Third Quarter" }, { "Aysiz Gece", "Moonless Night" },
        { "Tutulma", "Eclipse" }, { "Ikiz Hilal", "Twin Crescents" }, { "Ilk Dordun", "First Quarter" }, { "Kan Ayi", "Blood Moon" }
    };

    private static void showDream(Player player, Dream dream) {
        if (dream.kind.equals("prophecy")) {
            String[] next = MOON_NAMES[(moonPhase() + 1) % 8];
            actionbar(player, tx("§8Ruyanda yarinin ayini gordun: §f" + next[0], "§8In your dream you saw tomorrow's moon: §f" + next[1]));
            return;
        }
        String color = dream.kind.equals("nightmare") ? "§4" : dream.kind.equals("peace") ? "§b" : "§7";
        actionbar(player, color + tx(dream.text) + "§r");
    }

    private static void applyWake(Player player, Dream dream) {
        addStat(player, "dreams");
        if (dream.kind.equals("peace")) {
            gainSanity(player, 12);
            effect(player, "regeneration", 120, 0);
            addStat(player, "dreamsPeace");
            say(player, tx("§b(huzurlu bir ruyadan uyandin)§r", "§b(you woke from a peaceful dream)§r"));
        } else if (dream.kind.equals("nightmare")) {
            loseSanity(player, hasPerk(player, "quick_recovery") ? 6 : 12);
            effect(player, "nausea", 100, 0);
            effect(player, "darkness", 120, 0);
            addStat(player, "dreamsNightmare");
            // 12) kabus yankisi: uyandiktan sonra 2 dakika boyunca uc yanki
            if (isEnabled("nightmare_residue")) {
                String[] ids = { "phantom_sound", "tinnitus", "nape_breath" };
                for (int i = 0; i < ids.length; i++) {
                    final String id = ids[i];
                    Sys.runTimeout(() -> { if (isValid(player)) triggerEvent(id, player, true); }, (30 + i * 40) * TICKS_PER_SEC);
                }
            }
            title(player, tx("§4Kabus", "§4Nightmare"), "", 40);
            playTo(player, "mob.wither.ambient", 0.6, 0.5);
        } else if (dream.kind.equals("prophecy")) {
            addStat(player, "dreamsProphecy");
        } else {
            say(player, tx("§7(garip bir ruyaydi)§r", "§7(it was a strange dream)§r"));
        }
    }

    public static void init() {
        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("dreams")) return;
            long tick = Sys.currentTick();
            for (Player p : World.getPlayers()) {
                try {
                    SleepState st = sleeping.get(p.id());
                    if (p.isSleeping()) {
                        if (st == null) { sleeping.put(p.id(), new SleepState(tick)); continue; }
                        if (st.dream == null && tick - st.since >= 60) {
                            st.dream = chooseDream(getSanity(p), hasPerk(p, "dream_weaver"));
                            showDream(p, st.dream);
                        }
                    } else if (st != null) {
                        sleeping.remove(p.id());
                        if (st.dream != null) applyWake(p, st.dream);
                    }
                } catch (Throwable e) { /* yut */ }
            }
        }, 10);

        try {
            Events.After.playerLeave.subscribe(ev -> { sleeping.remove(ev.playerId); });
        } catch (Throwable e) { /* yut */ }
    }
}

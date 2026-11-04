package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.duskwalker.mod.Core.*;

/** DUSKWALKER v11 - ARKADAS SISTEMI "yalniz kalma" (buddy.js): 10 blok yakinlikta iki oyuncu = arkadas */
public final class Buddy {
    private Buddy() {}

    private static final Set<String> announced = new HashSet<>();
    private static final int RADIUS = 10;

    public static void init() {
        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("buddy")) { buddies.clear(); return; }
            long tick = Sys.currentTick();
            List<Player> players = new ArrayList<>();
            for (Player p : World.getPlayers()) if (isSurvival(p)) players.add(p);
            Set<String> nowBuddies = new HashSet<>();
            for (int i = 0; i < players.size(); i++) {
                for (int j = i + 1; j < players.size(); j++) {
                    Player a = players.get(i), b = players.get(j);
                    if (!a.dimension().id().equals(b.dimension().id()) || hdist(a.location(), b.location()) > RADIUS) continue;
                    nowBuddies.add(a.id()); nowBuddies.add(b.id());
                    // korku bulasmasi (30 sn'de bir)
                    if (tick % (TICKS_PER_SEC * 30L) < TICKS_PER_SEC * 5) {
                        double sa = getSanity(a), sb = getSanity(b);
                        if (sa < 20 && sb >= 20) loseSanity(b, 1);
                        if (sb < 20 && sa >= 20) loseSanity(a, 1);
                    }
                }
            }
            buddies.clear();
            buddies.addAll(nowBuddies);

            for (Player p : players) {
                try {
                    if (nowBuddies.contains(p.id())) {
                        addStat(p, "buddySeconds", 5);
                        Object lastO = safe(() -> p.getDynamicProperty(KEY.LAST_SANITY_EVENT), (Object) null);
                        double last = lastO instanceof Number n ? n.doubleValue() : 0;
                        if (tick - last > 15 * TICKS_PER_SEC && getSanity(p) < 100) gainSanity(p, 1);
                        if (!announced.contains(p.id())) {
                            announced.add(p.id());
                            actionbar(p, tx("§bYalniz degilsin§r", "§bYou're not alone§r"));
                        }
                    } else {
                        announced.remove(p.id());
                    }
                } catch (Throwable e) { /* yut */ }
            }
        }, TICKS_PER_SEC * 5);

        try {
            Events.After.playerLeave.subscribe(ev -> announced.remove(ev.playerId));
        } catch (Throwable e) { /* yut */ }
    }
}

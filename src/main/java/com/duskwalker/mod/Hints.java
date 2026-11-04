package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.*;

import static com.duskwalker.mod.Core.*;

/** DUSKWALKER v11 - IPUCLARI (ilk kez yasanan olaylar icin). hints.js portu. */
public final class Hints {
    private Hints() {}

    private static final String HINT_KEY = "duskwalker:v11hints";
    private static final Map<String, Long> nextAllowed = new HashMap<>();

    public static void init() {
        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("hints")) return;
            long tick = Sys.currentTick();
            for (Player p : World.getPlayers()) {
                try {
                    if (tick < nextAllowed.getOrDefault(p.id(), 0L)) continue;
                    Object raw = playerGet(p, HINT_KEY, null);
                    List<Object> shown = raw instanceof List<?> ? Json.asList(raw) : new ArrayList<>();
                    for (Codex.Entry e : Codex.ENTRIES) {
                        if (!e.key.startsWith("ev_") || shown.contains(e.key) || getStat(p, e.key, 0) <= 0) continue;
                        shown.add(e.key);
                        playerSet(p, HINT_KEY, shown);
                        nextAllowed.put(p.id(), tick + 25L * TICKS_PER_SEC);
                        Sys.runTimeout(() -> safe(() -> say(p, "§8[" + tx("Ipucu", "Tip") + "] §7" + tx(e.name) + ": §f" + tx(e.tip) + "§r")), 100);
                        break;
                    }
                } catch (Throwable e) { /* yut */ }
            }
        }, TICKS_PER_SEC * 5);

        try { Events.After.playerLeave.subscribe(ev -> nextAllowed.remove(ev.playerId)); } catch (Throwable e) { /* yut */ }
    }
}

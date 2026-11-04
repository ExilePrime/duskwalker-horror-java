package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.List;
import java.util.Locale;

import static com.duskwalker.mod.Core.*;

/** DUSKWALKER v10 - TANI (!dwdiag) (diag.js) */
public final class Diag {
    private Diag() {}

    public static final String VERSION = "10.1.0";

    private static void report(Player player) {
        List<EventDef> events = listEvents();
        int on = 0;
        for (EventDef e : events) if (isEnabled(e.id)) on++;
        String[] lines = {
            "§6Duskwalker " + VERSION + " tani§r",
            "§7Gun: §f" + getDay() + "§7 | Gece: §f" + (isNight() ? "evet" : "hayir") + "§7 | Yogunluk: §fx" + String.format(Locale.ROOT, "%.2f", getIntensity()),
            "§7Saglamlik: §f" + Math.round(getSanity(player)) + "§7 | Isik kaynagi: §f" + refuge.getOrDefault(player.id(), 0) + (isInRefuge(player) ? " §e(siginakta)" : ""),
            "§7Kipirdamadan: §f" + stillSeconds(player) + " sn§7 | Yeni olaylar: §f" + on + "/" + events.size() + " acik",
            "§7Yasanan olay: §f" + (long) getStat(player, "v10events", 0) + "§7 | Oyuncu sayisi: §f" + World.getPlayers().size(),
            "§7Final yuzlesmesi: §f" + (isDefeated() ? "tamamlandi" : "bekliyor")
        };
        say(player, String.join("\n", lines));
    }

    public static void init() {
        try {
            Events.Before.chatSend.subscribe(ev -> {
                if (!"!dwdiag".equals(ev.message)) return;
                ev.cancel = true;
                Player player = ev.sender;
                Sys.run(() -> safe(() -> report(player)));
            });
        } catch (Throwable e) { /* yut */ }
    }
}

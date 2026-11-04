package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.ArrayList;
import java.util.List;

import static com.duskwalker.mod.Core.*;

/**
 * DUSKWALKER v10 - KOMUTLAR (commands.js)
 *   !dwstats        istatistik formu
 *   !dwach          basarim listesi
 *   !v10            yeni olaylarin listesi
 *   !v10 olay       bir olayi hemen tetikler (test)
 *   !v10 all        butun olaylarin acik/kapali durumunu gosterir
 */
public final class Commands {
    private Commands() {}

    private static boolean initialized = false;

    private static void handle(Player player, String msg) {
        if (msg.equals("!dwstats")) {
            say(player, "§7Istatistik formu aciliyor... sohbeti kapat.§r");
            Stats.openStatsForm(player);
            return;
        }
        if (msg.equals("!dwach")) {
            say(player, "§7Basarim listesi aciliyor... sohbeti kapat.§r");
            Stats.openAchievementsForm(player);
            return;
        }
        String[] parts = msg.split("\\s+");
        String arg = parts.length > 1 ? parts[1] : null;
        if (arg == null || arg.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (EventDef d : listEvents()) names.add("§e" + d.id + "§7 (" + d.name + ", gun " + d.minDay + "+)");
            say(player, "§6Yeni korku olaylari:§r\n" + String.join("\n", names) + "\n§7Tetikle: !v10 <olay> | Durum: !v10 all§r");
            return;
        }
        if (arg.equals("all")) {
            List<String> lines = new ArrayList<>();
            for (EventDef d : listEvents()) lines.add((isEnabled(d.id) ? "§a[acik] " : "§c[kapali] ") + "§f" + d.id);
            say(player, "§6Olay durumu:§r\n" + String.join("\n", lines));
            return;
        }
        if (getEventDef(arg) == null) {
            say(player, "§cBilinmeyen olay: " + arg + "§r (liste icin !v10)");
            return;
        }
        boolean ok = triggerEvent(arg, player, true);
        if (!ok) say(player, "§7Olay su an gerceklesemedi (kosul saglanmadi: gece, kapi, hayvan vb.).§r");
    }

    public static void init() {
        if (initialized) return;
        initialized = true;
        Events.Before.chatSend.subscribe(ev -> {
            String msg = ev.message;
            if (!msg.equals("!dwstats") && !msg.equals("!dwach") && !msg.equals("!v10") && msg.indexOf("!v10 ") != 0) return;
            ev.cancel = true;
            Player player = ev.sender;
            Sys.run(() -> safe(() -> handle(player, msg.trim())));
        });
    }
}

package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.duskwalker.mod.Core.*;

/** DUSKWALKER v11 - GECE TAHMINI (forecast.js): sabah otomatik tahmin ve !dwforecast */
public final class Forecast {
    private Forecast() {}

    private static final String[][] NAMES = {
        {"Dolunay", "Full Moon"}, {"Ikiz Aylar", "Twin Moons"}, {"Ucte Uc", "Third Quarter"}, {"Aysiz Gece", "Moonless Night"},
        {"Tutulma", "Eclipse"}, {"Ikiz Hilal", "Twin Crescents"}, {"Ilk Dordun", "First Quarter"}, {"Kan Ayi", "Blood Moon"}
    };
    private static final String[][] THREAT = {
        {"yuksek", "high"}, {"yuksek", "high"}, {"dusuk", "low"}, {"orta", "medium"},
        {"yuksek", "high"}, {"orta", "medium"}, {"dusuk", "low"}, {"cok yuksek", "very high"}
    };

    private static int minutesToDusk() {
        int t = timeOfDay();
        if (t >= 12500) return 0;
        return (int) Math.max(0, Math.round((12500 - t) / 20.0 / 60.0));
    }

    private static String forecastText(Player player) {
        // aksam ay evresi: sabah ise ayni gunun evresi, gece ise bugunku
        int phase = moonPhase();
        double m = playerMult.getOrDefault(player.id(), 1.0);
        List<String> lines = new ArrayList<>();
        lines.add(tx("§6Gece Tahmini§r", "§6Night Forecast§r"));
        lines.add(tx("§7Ay: §f", "§7Moon: §f") + tx(NAMES[phase]) + "§7  |  " + tx("tehlike: §f", "threat: §f") + tx(THREAT[phase]));
        lines.add(tx("§7Gece tipi: §f", "§7Night type: §f") + (isNight() ? Nights.getNightType() : tx("aksam belli olur", "revealed at dusk")));
        lines.add(tx("§7Korku carpani: §f", "§7Dread multiplier: §f") + String.format(Locale.ROOT, "%.2f", m));
        lines.add(isNight() ? tx("§7Su an gece.", "§7It is night now.") : tx("§7Aksama kalan: §f", "§7Until dusk: §f") + minutesToDusk() + tx(" dk", " min"));
        Object q = playerGet(player, "duskwalker:v11quest", null);
        if (q != null) lines.add(tx("§7Aktif nobet: §f(!quest)", "§7Active watch: §f(!quest)"));
        return String.join("\n", lines);
    }

    private static int lastT = -1;

    public static void init() {
        // sabah otomatik tahmin
        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("forecast")) { lastT = -1; return; }
            int t = timeOfDay();
            if (lastT > 18000 && t < 4000) {
                for (Player p : World.getPlayers()) if (isSurvival(p)) safe(() -> say(p, forecastText(p)));
            }
            lastT = t;
        }, 40);

        try {
            Events.Before.chatSend.subscribe(ev -> {
                if (!"!dwforecast".equals(ev.message)) return;
                ev.cancel = true;
                Player p = ev.sender;
                Sys.run(() -> safe(() -> say(p, forecastText(p))));
            });
        } catch (Throwable e) { /* yut */ }
    }
}

package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.Locale;
import java.util.Map;

import static com.duskwalker.mod.Core.*;

/**
 * DUSKWALKER v11 - UYUMLU ZORLUK "Korku Seviyesi" (adaptive.js)
 * Oyuncu basina korku carpani 0.6 (en sik) ... 2.0 (en seyrek). !dwdread ile gorulur.
 */
public final class Adaptive {
    private Adaptive() {}

    private static final String ADAPT_KEY = "duskwalker:v11adapt";

    /** Kalici durum: { mult, calm, rough, lastDeath } haritasi */
    private static Map<String, Object> state(Player p) {
        Object o = playerGet(p, ADAPT_KEY, null);
        Map<String, Object> s = Json.asMap(o);
        if (s == null) {
            s = Json.newMap();
            s.put("mult", 1.0);
            s.put("calm", 0.0);
            s.put("rough", 0.0);
            s.put("lastDeath", -1.0);
        }
        return s;
    }

    private static void apply(Player p, Map<String, Object> s) {
        double mult = clamp(Json.num(s.get("mult"), 1.0), 0.6, 2.0);
        s.put("mult", mult);
        playerMult.put(p.id(), mult);
        playerSet(p, ADAPT_KEY, s);
    }

    public static void init() {
        try {
            Events.After.entityDie.subscribe(ev -> {
                try {
                    Entity d = ev.deadEntity;
                    if (!(d instanceof Player dp) || !d.typeId().equals("minecraft:player")) return;
                    Map<String, Object> s = state(dp);
                    long tick = Sys.currentTick();
                    double lastDeath = Json.num(s.get("lastDeath"), -1);
                    // 30 dk icinde ikinci olum: merhamet kuvvetlenir
                    s.put("mult", Json.num(s.get("mult"), 1.0) + ((lastDeath >= 0 && tick - lastDeath < 30L * TICKS_PER_MIN) ? 0.35 : 0.2));
                    s.put("lastDeath", (double) tick);
                    s.put("calm", 0.0);
                    apply(dp, s);
                } catch (Throwable e) { /* yut */ }
            });
        } catch (Throwable e) { /* yut */ }

        try {
            Events.After.playerSpawn.subscribe(ev -> {
                try { Map<String, Object> s = state(ev.player); apply(ev.player, s); } catch (Throwable e) { /* yut */ }
            });
        } catch (Throwable e) { /* yut */ }

        // her dakika: saglamliga gore sayac guncelle
        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("adaptive")) { for (Player p : World.getPlayers()) playerMult.put(p.id(), 1.0); return; }
            for (Player p : World.getPlayers()) {
                try {
                    if (!isSurvival(p)) continue;
                    Map<String, Object> s = state(p);
                    double sanity = getSanity(p);
                    double calm = Json.num(s.get("calm"), 0), rough = Json.num(s.get("rough"), 0), mult = Json.num(s.get("mult"), 1.0);
                    if (sanity >= 85) { calm += 1; rough = Math.max(0, rough - 1); }
                    else if (sanity <= 30) { rough += 1; calm = Math.max(0, calm - 2); }
                    else { calm = Math.max(0, calm - 1); rough = Math.max(0, rough - 1); }
                    if (calm >= 10) { mult -= 0.1; calm = 0; }          // 10 dk rahat -> daha sik
                    if (rough >= 3) { mult += 0.15; rough = 0; }        // 3 dk dusuk -> daha seyrek
                    // zamanla 1.0'a yavasca don
                    if (mult > 1) mult = Math.max(1, mult - 0.01);
                    s.put("calm", calm); s.put("rough", rough); s.put("mult", mult);
                    apply(p, s);
                } catch (Throwable e) { /* yut */ }
            }
        }, TICKS_PER_MIN);

        try {
            Events.Before.chatSend.subscribe(ev -> {
                if (!"!dwdread".equals(ev.message)) return;
                ev.cancel = true;
                Player p = ev.sender;
                Sys.run(() -> {
                    double m = playerMult.getOrDefault(p.id(), 1.0);
                    String label = m < 0.85 ? tx("yuksek baski", "high pressure") : m > 1.25 ? tx("merhamet", "mercy") : tx("normal", "normal");
                    say(p, tx("§7Korku carpani: §f", "§7Dread multiplier: §f") + String.format(Locale.ROOT, "%.2f", m) + " §8(" + label + ")§r");
                });
            });
        } catch (Throwable e) { /* yut */ }
    }
}

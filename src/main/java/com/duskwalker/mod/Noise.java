package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.duskwalker.mod.Core.*;

/**
 * DUSKWALKER v11 - GURULTU SISTEMI (noise.js): her oyuncunun 0-100 "gurultu" degeri; 70'i gecince bir korku olayi tetiklenir.
 * !noise ile deger gorulur.
 */
public final class Noise {
    private Noise() {}

    private static final Map<String, Double> noise = new HashMap<>();   // oyuncu id -> deger
    private static final Map<String, Boolean> warned = new HashMap<>(); // oyuncu id -> uyari verildi mi

    public static double getNoise(Player player) { return noise.getOrDefault(player.id(), 0.0); }

    private static void addNoise(Player player, double amount) {
        if (!isEnabled("noise")) return;
        double nm = modifiers.noiseMult == 0 ? 1 : modifiers.noiseMult;
        double scaled = amount > 0 ? amount * perkMult(player, "noise") * pactMult(player, "noise") * nm : amount;
        noise.put(player.id(), clamp(noise.getOrDefault(player.id(), 0.0) + scaled, 0, 100));
    }

    private static final String[] LURES = {"eyes_dark", "delayed_steps", "phantom_sound", "dont_blink", "door_knock", "stand_still"};

    public static void init() {
        try {
            Events.After.playerBreakBlock.subscribe(ev -> safe(() -> addNoise(ev.player, 3)));
        } catch (Throwable e) { /* yut */ }

        try {
            Events.After.playerPlaceBlock.subscribe(ev -> safe(() -> addNoise(ev.player, 1.5)));
        } catch (Throwable e) { /* yut */ }

        try {
            Events.After.entityHurt.subscribe(ev -> {
                try {
                    Entity hurt = ev.hurtEntity;
                    if (hurt instanceof Player hp && hurt.typeId().equals("minecraft:player")) addNoise(hp, 4);
                    Entity src = ev.damageSource != null ? ev.damageSource.damagingEntity : null;
                    if (src instanceof Player sp && src.typeId().equals("minecraft:player")) addNoise(sp, 3);
                } catch (Throwable e) { /* yut */ }
            });
        } catch (Throwable e) { /* yut */ }

        try {
            Events.After.explosion.subscribe(ev -> {
                try {
                    Entity src = ev.source;
                    Vec3 at = src != null ? src.location() : null;
                    if (at == null) return;
                    for (Player p : World.getPlayers()) {
                        if (p.dimension().id().equals(ev.dimension.id()) && hdist(p.location(), at) < 40) addNoise(p, 25);
                    }
                } catch (Throwable e) { /* yut */ }
            });
        } catch (Throwable e) { /* yut */ }

        // kosma / egilme / azalma (1 sn)
        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("noise")) return;
            for (Player p : World.getPlayers()) {
                try {
                    if (!isSurvival(p)) { noise.put(p.id(), 0.0); continue; }
                    double delta = -1.5;
                    if (p.isSprinting()) delta = 4;
                    else if (p.isSneaking()) delta = -3.5;
                    if (isInRefuge(p) && delta < 0) delta *= 1.5;
                    addNoise(p, delta);
                } catch (Throwable e) { /* yut */ }
            }
        }, TICKS_PER_SEC);

        // tepki (2 sn)
        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("noise")) return;
            int day = getDay();
            for (Player p : World.getPlayers()) {
                try {
                    double v = getNoise(p);
                    if (v >= 55 && !Boolean.TRUE.equals(warned.get(p.id()))) {
                        warned.put(p.id(), true);
                        actionbar(p, tx("§8(cok gurultu yapiyorsun...)§r", "§8(you're being loud...)§r"));
                    } else if (v < 40) {
                        warned.put(p.id(), false);
                    }
                    if (v < 70 || !isNight() || !chance(0.5)) continue;
                    addStat(p, "noiseSpikes");
                    List<String> options = new ArrayList<>();
                    for (String id : LURES) {
                        EventDef def = getEventDef(id);
                        if (def != null && def.minDay <= day) options.add(id);
                    }
                    if (options.isEmpty()) continue;
                    // birkac deneme: kosulu saglamayan olay false doner
                    boolean fired = false;
                    for (int i = 0; i < 3 && !fired; i++) fired = triggerEvent(pick(options), p, false);
                    if (fired) noise.put(p.id(), 40.0);
                } catch (Throwable e) { /* yut */ }
            }
        }, TICKS_PER_SEC * 2);

        try {
            Events.After.playerLeave.subscribe(ev -> { noise.remove(ev.playerId); warned.remove(ev.playerId); });
        } catch (Throwable e) { /* yut */ }

        try {
            Events.Before.chatSend.subscribe(ev -> {
                if (!"!noise".equals(ev.message)) return;
                ev.cancel = true;
                Player p = ev.sender;
                Sys.run(() -> {
                    long v = Math.round(getNoise(p));
                    int n = (int) Math.round(v / 5.0);
                    String bar = "§c" + "|".repeat(Math.max(0, n)) + "§8" + "|".repeat(Math.max(0, 20 - n));
                    say(p, tx("§7Gurultu: §f", "§7Noise: §f") + v + " §8[" + bar + "§8]§r");
                });
            });
        } catch (Throwable e) { /* yut */ }
    }
}

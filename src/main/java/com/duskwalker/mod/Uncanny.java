package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.*;

import static com.duskwalker.mod.Core.*;

/**
 * DUSKWALKER v12 - "TUHAF" OLAYLAR (uncanny.js'in Java karsiligi)
 *  1) DEJA VU (deja_vu)  2) GERI SAYIM (countdown)  3) ENSE NEFESI (nape_breath)
 *  4) AYNA IKIZ (mirror_twin)  5) YAZILMAMIS (unwritten)  6) DINLEME SAATI (listening_hour)
 */
public final class Uncanny {
    private Uncanny() {}

    // ---------------------------------------------------------
    //  Ortak: oyuncu izi + son sohbet satiri
    // ---------------------------------------------------------
    private static final class Pt {
        final double x, y, z; final String d;
        Pt(double x, double y, double z, String d) { this.x = x; this.y = y; this.z = z; this.d = d; }
        Vec3 vec() { return new Vec3(x, y, z); }
    }

    private static final Map<String, List<Pt>> trails = new HashMap<>();
    private static final Map<String, String> lastChat = new HashMap<>();

    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        initialized = true;

        Sys.runInterval(() -> {
            for (Player p : World.getPlayers()) {
                try {
                    List<Pt> t = trails.computeIfAbsent(p.id(), k -> new ArrayList<>());
                    Vec3 l = p.location();
                    if (!t.isEmpty() && !t.get(t.size() - 1).d.equals(p.dimension().id())) t.clear();
                    t.add(new Pt(l.x(), l.y(), l.z(), p.dimension().id()));
                    while (t.size() > 32) t.remove(0);
                } catch (Throwable e) { /* yut */ }
            }
        }, 5);

        Events.Before.chatSend.subscribe(ev -> {
            try {
                String m = ev.message;
                if (m != null && m.length() >= 3 && m.charAt(0) != '!') lastChat.put(ev.sender.id(), m.substring(0, Math.min(80, m.length())));
            } catch (Throwable e) { /* yut */ }
        });

        Events.After.playerLeave.subscribe(ev -> { trails.remove(ev.playerId); lastChat.remove(ev.playerId); });

        // ---------------------------------------------------------
        //  1) DEJA VU
        // ---------------------------------------------------------
        registerEvent(EventDef.of("deja_vu").name("Deja Vu").minDay(7).weight(2).cooldown(20L * TICKS_PER_MIN)
            .cond(p -> trails.getOrDefault(p.id(), new ArrayList<>()).size() >= 28)
            .run(player -> {
                List<Pt> t = trails.get(player.id());
                if (t == null || t.size() < 28) return false;
                Pt past = t.get(t.size() - 25);
                if (!past.d.equals(player.dimension().id()) || dist3(past.vec(), player.location()) < 6) return false;
                fade(player, 0, 0, 0, 0.1, 0.2, 0.5);
                safe(() -> player.teleport(past.vec(), Opts.Teleport.dim(player.dimension())));
                playTo(player, "duskwalker.drone", 0.6, 1.3);
                actionbar(player, tx("§8(bunu daha once yasadin...)§r", "§8(you have lived this before...)§r"));
                String line = lastChat.get(player.id());
                if (line != null && !line.isEmpty()) {
                    Sys.runTimeout(() -> { if (isValid(player)) say(player, "<" + player.name() + "> " + line); }, 24);
                }
                loseSanity(player, 6);
                addStat(player, "dejaVu");
                return true;
            }));

        // ---------------------------------------------------------
        //  2) GERI SAYIM
        // ---------------------------------------------------------
        registerEvent(EventDef.of("countdown").name("Countdown").minDay(8).weight(2).cooldown(24L * TICKS_PER_MIN)
            .cond(p -> isNight())
            .run(player -> {
                int[] delays = { 40, 40, 40, 36, 32, 28, 24, 20, 16, 12, 8, 6, 4, 4, 4 };
                int at = 0;
                say(player, tx("§8(tik... tik...)§r", "§8(tick... tick...)§r"));
                for (int i = 0; i < delays.length; i++) {
                    at += delays[i];
                    final int fi = i;
                    Sys.runTimeout(() -> { if (isValid(player)) playTo(player, "duskwalker.tick", 0.9, 0.9 + fi * 0.035); }, at);
                }
                Sys.runTimeout(() -> {
                    if (!isValid(player)) return;
                    if (isInRefuge(player)) {
                        gainSanity(player, 3);
                        actionbar(player, tx("§7(tik sesi durdu. isik dayandi.)§r", "§7(the ticking stopped. the light held.)§r"));
                        addStat(player, "countdownSaved");
                        return;
                    }
                    Vec3 spot = standSpot(player.dimension(), frontPlayer(player, 3), 0, 1, 10);
                    if (spot != null) {
                        Entity e = spawnEffigy(player.dimension(), spot, 1);
                        if (e != null) { faceToward(e, eyePos(player)); Sys.runTimeout(() -> despawn(e, true), 24); }
                    }
                    playTo(player, "duskwalker.creak", 1.0, 0.6);
                    shake(player, 0.18, 1.0);
                    loseSanity(player, 14);
                    addEvidence(player, 2);
                }, at + 12);
                return true;
            }));

        // ---------------------------------------------------------
        //  3) ENSE NEFESI
        // ---------------------------------------------------------
        registerEvent(EventDef.of("nape_breath").name("Breath on Your Neck").minDay(4).weight(4).cooldown(10L * TICKS_PER_MIN)
            .cond(p -> stillSeconds(p) >= 4)
            .run(player -> {
                Dimension dim = player.dimension();
                java.util.function.Supplier<Vec3> at = () -> { Vec3 b = behindPlayer(player, 0.6); return new Vec3(b.x(), b.y() + 1.5, b.z()); };
                for (int k = 0; k < 3; k++) {
                    final int fk = k;
                    Sys.runTimeout(() -> { if (isValid(player)) playAt(dim, "duskwalker.breath", at.get(), 1.2, 0.95 - fk * 0.05); }, k * 34);
                }
                Sys.runTimeout(() -> {
                    if (!isValid(player)) return;
                    if (viewDot(player, at.get()) > 0.5) { addStat(player, "napeTurned"); return; } // dondu: hicbir sey yok
                    shake(player, 0.07, 0.6);
                    loseSanity(player, 8);
                    addEvidence(player, 1);
                }, 110);
                return true;
            }));

        // ---------------------------------------------------------
        //  4) AYNA IKIZ (nokta yansimasi)
        // ---------------------------------------------------------
        registerEvent(EventDef.of("mirror_twin").name("Mirror Twin").minDay(9).weight(2).cooldown(26L * TICKS_PER_MIN)
            .cond(p -> getSanity(p) <= 80)
            .run(player -> {
                Dimension dim = player.dimension();
                Vec3 l0 = player.location();
                Vec3 pivot = frontPlayer(player, 10);
                double firstX = 2 * pivot.x() - l0.x(), firstZ = 2 * pivot.z() - l0.z();
                Integer gy = groundY(dim, firstX, l0.y(), firstZ);
                if (gy == null) return false;
                Entity twin = spawnEffigy(dim, new Vec3(firstX, gy, firstZ), 1);
                if (twin == null) return false;
                double[] y = { gy };
                int[] ticks = { 0 };
                say(player, tx("§8(karsinda biri... seninle ayni hareket ediyor)§r", "§8(someone across from you... moving exactly as you do)§r"));
                int[] handle = { -1 };
                handle[0] = Sys.runInterval(() -> {
                    try {
                        ticks[0] += 5;
                        if (!isValid(player) || !isValid(twin) || !player.dimension().id().equals(dim.id())) {
                            Sys.clearRun(handle[0]); despawn(twin, false); return;
                        }
                        Vec3 l = player.location();
                        double txx = 2 * pivot.x() - l.x(), tz = 2 * pivot.z() - l.z();
                        Integer ny = groundY(dim, txx, y[0], tz);
                        if (ny != null) y[0] = ny;
                        safe(() -> twin.teleport(new Vec3(txx, y[0], tz), Opts.Teleport.dim(dim).facing(eyePos(player))));
                        if (hdist(new Vec3(txx, 0, tz), l) < 3) {
                            Sys.clearRun(handle[0]);
                            playTo(player, "duskwalker.whisper", 1.0, 0.8);
                            fade(player, 0, 0, 0, 0.05, 0.15, 0.5);
                            loseSanity(player, 10);
                            addEvidence(player, 1);
                            addStat(player, "twinMet");
                            Sys.runTimeout(() -> despawn(twin, true), 6);
                            return;
                        }
                        if (ticks[0] > 20 * 45) { Sys.clearRun(handle[0]); despawn(twin, true); }
                    } catch (Throwable e) { safe(() -> Sys.clearRun(handle[0])); }
                }, 5);
                return true;
            }));

        // ---------------------------------------------------------
        //  5) YAZILMAMIS (kendini yazan tabela)
        // ---------------------------------------------------------
        registerEvent(EventDef.of("unwritten").name("Unwritten").minDay(6).weight(3).cooldown(18L * TICKS_PER_MIN)
            .run(player -> {
                Dimension dim = player.dimension();
                Vec3 spot = standSpot(dim, frontPlayer(player, 6), 0, 2, 12);
                if (spot == null) return false;
                Vec3 pos = new Vec3(Math.floor(spot.x()), Math.floor(spot.y()), Math.floor(spot.z()));
                String nm = player.name().toUpperCase(Locale.ROOT);
                nm = nm.substring(0, Math.min(14, nm.length()));
                String full = tx("HALA SENIN\nADINI YAZIYORUM\n" + nm, "STILL WRITING\nYOUR NAME\n" + nm);
                try {
                    Block b = dim.getBlock(pos);
                    if (b == null || !b.typeId().equals("minecraft:air")) return false;
                    b.setType("minecraft:standing_sign");
                } catch (Throwable e) { return false; }
                int[] n = { 0 };
                int[] handle = { -1 };
                handle[0] = Sys.runInterval(() -> {
                    try {
                        if (!isValid(player) || hdist(pos, player.location()) > 40) { Sys.clearRun(handle[0]); return; }
                        n[0]++;
                        final String part = full.substring(0, Math.min(n[0], full.length()));
                        safe(() -> { Block sb = dim.getBlock(pos); if (sb != null) sb.setSignText(part); });
                        if (n[0] % 3 == 0) playAt(dim, "dig.wood", new Vec3(pos.x(), pos.y(), pos.z()), 0.4, 1.4);
                        if (n[0] >= full.length()) {
                            Sys.clearRun(handle[0]);
                            loseSanity(player, 5);
                            addEvidence(player, 1);
                        }
                    } catch (Throwable e) { safe(() -> Sys.clearRun(handle[0])); }
                }, 6);
                Sys.runTimeout(() -> safe(() -> {
                    Block b = dim.getBlock(pos);
                    if (b != null && b.typeId().equals("minecraft:standing_sign")) b.setType("minecraft:air");
                }), 3 * TICKS_PER_MIN);
                return true;
            }));

        // ---------------------------------------------------------
        //  6) DINLEME SAATI
        // ---------------------------------------------------------
        registerEvent(EventDef.of("listening_hour").name("The Listening Hour").minDay(5).weight(2).cooldown(30L * TICKS_PER_MIN)
            .cond(p -> isNight() && modifiers.noiseMult == 1)
            .run(player -> {
                modifiers.noiseMult = 3;
                for (Player p : World.getPlayers()) say(p, tx("§8(dunya dinliyor... sessiz ol)§r", "§8(the world is listening... be quiet)§r"));
                playTo(player, "duskwalker.drone", 0.8, 0.8);
                Sys.runTimeout(() -> {
                    modifiers.noiseMult = 1;
                    for (Player p : World.getPlayers()) actionbar(p, tx("§7(dinleme bitti)§r", "§7(it has stopped listening)§r"));
                }, 2 * TICKS_PER_MIN);
                addStat(player, "listeningHours");
                return true;
            }));
    }
}

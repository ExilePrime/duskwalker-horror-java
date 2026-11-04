package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.duskwalker.mod.Core.*;

/**
 * DUSKWALKER v10 - AY TAKVIMI VE GECE TIPLERI (nights.js)
 * 8 gecelik ay dongusu (profiller), gece tipleri (clear/fog/ash/long), zaman yavaslatma, !moon / !night komutlari.
 * Not: Bedrock'ta gokyuzu betikle degistirilemez; gorsel ay evreye bagli oldugundan 8 gecelik donguyle degisir.
 */
public final class Nights {
    private Nights() {}

    private static final class Text {
        static final String tonight = "§7Bu gece: §f";
        static final String nightType = "§7Gece: §f";
        static final String none = "normal";
        static final String commandHeader = "§6Ay takvimi (8 gece):§r";
        static final String commandNow = "§7Bu geceki ay: §f";
        static final String commandNight = "§7Gece tipi: §f";
        static final String forced = "§7Gece tipi zorlandi: §f";
        static final String badType = "§cGecerli tipler: clear, fog, ash, long, dusk§r";
        static final String longStart = "§8Gece uzun olacak...§r";
        static final String duskLinger = "§8Gunes batmak istemiyor...§r";
    }

    // ---------------------------------------------------------
    //  Ay profilleri
    // ---------------------------------------------------------
    public static final class Profile {
        public final String id, name;
        public final double gap, regen, drain;
        public final boolean dark, red;
        Profile(String id, String name, double gap, double regen, double drain, boolean dark, boolean red) {
            this.id = id; this.name = name; this.gap = gap; this.regen = regen; this.drain = drain; this.dark = dark; this.red = red;
        }
    }

    public static final Profile[] PROFILES = {
        new Profile("full", "Dolunay", 0.8, 1.0, 0, false, false),
        new Profile("twin", "Ikiz Aylar", 0.7, 1.0, 1, false, false),
        new Profile("third", "Ucte Uc", 1.0, 1.0, 0, false, false),
        new Profile("none", "Aysiz Gece", 0.8, 2.0, 0, true, false),
        new Profile("eclipse", "Tutulma", 0.6, 1.0, 1, true, false),
        new Profile("crescent", "Ikiz Hilal", 0.75, 1.0, 0, false, false),
        new Profile("first", "Ilk Dordun", 1.0, 1.0, 0, false, false),
        new Profile("blood", "Kan Ayi", 0.5, 1.0, 2, false, true)
    };

    public static Profile currentProfile() { return PROFILES[moonPhase()]; }

    // ---------------------------------------------------------
    //  Gece tipi (dunya ozelligi olarak saklanir: yeniden baslatmaya dayanikli)
    // ---------------------------------------------------------
    private static final String NIGHT_KEY = "duskwalker:nightType";    // { d: gunIndeksi, t: tip }
    private static final String ORIG_RULE = "duskwalker:origDaylight";  // gamerule'un asil degeri
    private static final String TIME_HOLD = "duskwalker:timeHold";      // zaman yavaslatmasi acik mi

    public static final String[] NIGHT_TYPES = {"clear", "fog", "ash", "long"};

    private static int dayIndex() { return (int) Math.floor(safe(() -> (double) World.getAbsoluteTime(), 0.0) / 24000); }

    /** { d, t } haritasi veya null */
    private static Map<String, Object> readNight() {
        Object raw = safe(() -> World.getDynamicProperty(NIGHT_KEY), (Object) null);
        if (!(raw instanceof String s)) return null;
        return safe(() -> Json.asMap(Json.parse(s)), (Map<String, Object>) null);
    }

    private static void writeNight(String type) {
        Map<String, Object> m = Json.newMap();
        m.put("d", (double) dayIndex());
        m.put("t", type);
        safe(() -> World.setDynamicProperty(NIGHT_KEY, Json.stringify(m)));
    }

    private static String rollNightType() {
        // clear %48, fog %24, ash %12, long %16
        double r = Math.random();
        if (r < 0.48) return "clear";
        if (r < 0.72) return "fog";
        if (r < 0.84) return "ash";
        return "long";
    }

    public static String getNightType() {
        Map<String, Object> n = readNight();
        return n != null && n.get("d") instanceof Number d && (int) d.doubleValue() == dayIndex() ? Json.str(n.get("t"), "clear") : "clear";
    }

    // ---------------------------------------------------------
    //  Sis (komut: fog @s push <tanim> <etiket>)
    // ---------------------------------------------------------
    private static final Map<String, String> fogged = new HashMap<>(); // oyuncu id -> "fog" | "ash"

    private static String fogIdFor(String type) { return type.equals("ash") ? "duskwalker:ash_fog" : "duskwalker:night_fog"; }

    private static void pushFog(Player player, String type) {
        String cur = fogged.get(player.id());
        if (type.equals(cur)) return;
        if (cur != null) popFog(player);
        safe(() -> player.fogPush(fogIdFor(type), "duskwalker_night"));
        fogged.put(player.id(), type);
    }

    private static void popFog(Player player) {
        if (!fogged.containsKey(player.id())) return;
        safe(() -> player.fogPop("duskwalker_night"));
        fogged.remove(player.id());
    }

    // ---------------------------------------------------------
    //  Zaman yavaslatma motoru (Uzun Gece / Uzayan Alacakaranlik)
    //  gamerule doDayLightCycle kapatilir; her 10 tikte zaman elle "advance" tik ilerletilir.
    // ---------------------------------------------------------
    private static final class Slow { final int from, to, advance; Slow(int f, int t, int a) { from = f; to = t; advance = a; } }
    private static Slow slow = null;

    private static void setDaylightRule(boolean value) {
        safe(() -> World.setGameRule("doDayLightCycle", value));
    }

    private static void startSlow(int from, int to, int advance) {
        if (slow != null) return;
        boolean orig = safe(() -> World.gameRule("doDayLightCycle"), true);
        if (!orig) return; // yonetici gunduz dongusunu zaten kapatmis: karismayalim
        safe(() -> World.setDynamicProperty(ORIG_RULE, true));
        safe(() -> World.setDynamicProperty(TIME_HOLD, true));
        setDaylightRule(false);
        slow = new Slow(from, to, advance);
    }

    private static void stopSlow() {
        if (slow == null && !Boolean.TRUE.equals(safe(() -> World.getDynamicProperty(TIME_HOLD), (Object) false))) return;
        slow = null;
        setDaylightRule(true);
        safe(() -> World.setDynamicProperty(TIME_HOLD, false));
    }

    // ---------------------------------------------------------
    //  Ana dongu (2 sn): gece baslangici, profil, gece tipi
    // ---------------------------------------------------------
    private static int lastT = -1;
    private static int announcedDay = -1;

    private static void announceNight(String type) {
        Profile p = currentProfile();
        String typeName = switch (type) {
            case "fog" -> "Sisli Gece";
            case "ash" -> "Kul Gecesi";
            case "long" -> "Uzun Gece";
            default -> Text.none;
        };
        for (Player pl : World.getPlayers()) {
            if (!isSurvival(pl)) continue;
            Sys.runTimeout(() -> {
                actionbar(pl, Text.tonight + p.name + (type.equals("clear") ? "" : "§7  |  " + Text.nightType + typeName) + "§r");
            }, 100);
            addStat(pl, "moon_" + p.id);
        }
    }

    // ---------------------------------------------------------
    //  Komutlar: !moon  |  !night [clear|fog|ash|long|dusk]
    // ---------------------------------------------------------
    private static String moonCalendarText() {
        int now = moonPhase();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < PROFILES.length; i++) {
            if (i > 0) sb.append("\n");
            sb.append(i == now ? "§e> " : "§7  ").append(i + 1).append(". ").append(PROFILES[i].name).append(i == now ? " §8(bu gece)" : "");
        }
        return sb.toString();
    }

    public static void init() {
        Sys.runInterval(() -> {
            if (slow == null) return;
            try {
                int t = timeOfDay();
                // pencere disina ciktiysa (uyuyarak atlandi, komutla degisti) bitir
                if (t < slow.from - 300 || t >= slow.to) { stopSlow(); return; }
                World.setTimeOfDay(t + slow.advance);
            } catch (Throwable e) { stopSlow(); }
        }, 10);

        // Yeniden baslatma guvenligi: kural kapali kalmis ve zaman penceresi bitmisse geri ac
        Sys.runTimeout(() -> {
            try {
                if (Boolean.TRUE.equals(safe(() -> World.getDynamicProperty(TIME_HOLD), (Object) false))) {
                    setDaylightRule(true);
                    World.setDynamicProperty(TIME_HOLD, false);
                }
            } catch (Throwable e) { /* yut */ }
        }, 100);

        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("moonsky")) {
                modifiers.gapMult = 1; modifiers.regenMult = 1;
                lastT = -1;
                return;
            }
            int t = timeOfDay();
            boolean night = t >= 12500 && t <= 23500;

            // gece basladi
            if (lastT >= 0 && lastT < 12500 && t >= 12500 && announcedDay != dayIndex()) {
                announcedDay = dayIndex();
                Map<String, Object> rn = readNight();
                String type = null;
                if (rn != null && rn.get("d") instanceof Number d && (int) d.doubleValue() == dayIndex()) type = Json.str(rn.get("t"), null);
                if (type == null) { type = isEnabled("nightweather") ? rollNightType() : "clear"; writeNight(type); }
                announceNight(type);
                if (type.equals("long")) {
                    for (Player pl : World.getPlayers()) say(pl, Text.longStart);
                    startSlow(12500, 23500, 5);
                }
            }
            // gunbatimi: bazen uzar (Uzayan Alacakaranlik) - gunun %12'si
            if (lastT >= 0 && lastT < 11800 && t >= 11800 && t < 12100 && isEnabled("nightweather") && chance(0.12)) {
                for (Player pl : World.getPlayers()) say(pl, Text.duskLinger);
                startSlow(11800, 13000, 2);
            }
            lastT = t;

            // profil carpanlari (sadece gece gecerli)
            if (night) {
                Profile prof = currentProfile();
                modifiers.gapMult = prof.gap;
                modifiers.regenMult = prof.regen;
            } else {
                modifiers.gapMult = 1; modifiers.regenMult = 1;
            }

            // sis: gece boyunca; sabah kalkar
            String type = night ? getNightType() : "clear";
            for (Player pl : World.getPlayers()) {
                try {
                    if (night && (type.equals("fog") || type.equals("ash")) && pl.dimension().id().equals("minecraft:overworld")) pushFog(pl, type);
                    else popFog(pl);
                } catch (Throwable e) { /* yut */ }
            }
        }, 40);

        // Gece etkileri (30 sn): saglamlik kaybi, karanlik, kan ayi, kul
        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("moonsky") || !isNight()) return;
            Profile prof = currentProfile();
            String type = getNightType();
            for (Player p : World.getPlayers()) {
                try {
                    if (!isSurvival(p) || !p.dimension().id().equals("minecraft:overworld")) continue;
                    boolean covered = isUnderground(p) || isInRefuge(p);
                    if (prof.drain > 0 && !covered) loseSanity(p, prof.drain);
                    if (prof.dark && !covered && chance(0.6)) effect(p, "darkness", 140, 0);
                    if (prof.red && chance(0.5)) {
                        fade(p, 0.45, 0, 0, 0.4, 0.2, 1.2);
                        playTo(p, "mob.wither.ambient", 0.35, 0.5);
                    }
                    if (prof.id.equals("twin") && chance(0.35)) triggerEvent("phantom_sound", p, false);
                    if (type.equals("ash")) {
                        // kul: oyuncunun cevresinde yavasca dusen gri parcaciklar
                        Vec3 l = p.location();
                        for (int i = 0; i < 14; i++) {
                            particle(p.dimension(), "minecraft:basic_smoke_particle", new Vec3(l.x() + rand(-9, 9), l.y() + rand(2, 8), l.z() + rand(-9, 9)));
                        }
                    }
                } catch (Throwable e) { /* yut */ }
            }
        }, TICKS_PER_SEC * 30);

        // kul gecesinde parcaciklari sik tekrarla (sadece gorsel)
        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("nightweather") || !isNight() || !getNightType().equals("ash")) return;
            for (Player p : World.getPlayers()) {
                try {
                    if (!p.dimension().id().equals("minecraft:overworld")) continue;
                    Vec3 l = p.location();
                    for (int i = 0; i < 4; i++) {
                        particle(p.dimension(), "minecraft:basic_smoke_particle", new Vec3(l.x() + rand(-10, 10), l.y() + rand(3, 9), l.z() + rand(-10, 10)));
                    }
                } catch (Throwable e) { /* yut */ }
            }
        }, TICKS_PER_SEC * 3);

        try {
            Events.After.playerLeave.subscribe(ev -> fogged.remove(ev.playerId));
        } catch (Throwable e) { /* yut */ }

        try {
            Events.Before.chatSend.subscribe(ev -> {
                String msg = ev.message;
                if (!msg.equals("!moon") && !msg.equals("!night") && msg.indexOf("!night ") != 0) return;
                ev.cancel = true;
                Player player = ev.sender;
                Sys.run(() -> {
                    try {
                        if (msg.equals("!moon")) {
                            say(player, Text.commandHeader + "\n" + moonCalendarText());
                            return;
                        }
                        String[] parts = msg.split("\\s+");
                        String arg = parts.length > 1 ? parts[1] : null;
                        if (arg == null || arg.isEmpty()) {
                            say(player, Text.commandNow + currentProfile().name + "\n" + Text.commandNight + getNightType());
                            return;
                        }
                        if (arg.equals("dusk")) { startSlow(11800, 13000, 2); say(player, Text.duskLinger); return; }
                        if (!List.of(NIGHT_TYPES).contains(arg)) { say(player, Text.badType); return; }
                        writeNight(arg);
                        if (arg.equals("long")) startSlow(12500, 23500, 5);
                        say(player, Text.forced + arg + "§r");
                    } catch (Throwable e) { /* yut */ }
                });
            });
        } catch (Throwable e) { /* yut */ }
    }
}

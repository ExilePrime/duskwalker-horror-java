package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.*;

import static com.duskwalker.mod.Core.*;

/**
 * DUSKWALKER v14 - #1 OGRENEN AVCI (Adaptif Dusman). learn.js portu.
 * !dwdossier ; karsi hamle olaylari: light_siege, digger
 */
public final class Learn {
    private Learn() {}

    private static final String PROFILE_KEY = "duskwalker:v14learn";
    private static final String[] STYLES = { "light", "deep", "still", "run", "build", "fight" };

    private static final Map<String, String[]> STYLE_NAMES = new HashMap<>();
    static {
        STYLE_NAMES.put("light", new String[] { "Isiga siginma", "Sheltering in light" });
        STYLE_NAMES.put("deep", new String[] { "Yeralti", "Underground" });
        STYLE_NAMES.put("still", new String[] { "Hareketsiz bekleme", "Standing still" });
        STYLE_NAMES.put("run", new String[] { "Kosma", "Running" });
        STYLE_NAMES.put("build", new String[] { "Insa etme", "Building" });
        STYLE_NAMES.put("fight", new String[] { "Savasma", "Fighting" });
    }

    /** her tarza karsi: olay -> agirlik carpani (en yuksek ogrenme duzeyinde) */
    private static final Map<String, Map<String, Double>> COUNTERS = new HashMap<>();
    private static void counter(String style, Object... kv) {
        Map<String, Double> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], ((Number) kv[i + 1]).doubleValue());
        COUNTERS.put(style, m);
    }
    static {
        counter("light", "lantern_thief", 3, "light_flicker", 3, "moths", 2.5, "light_siege", 5);
        counter("deep", "digger", 5, "tinnitus", 2, "nape_breath", 2, "eyes_dark", 0.5);
        counter("still", "stand_still", 3, "nape_breath", 3, "countdown", 2.5, "mirror_behind", 3);
        counter("run", "dont_blink", 3, "shadow_follower", 3, "footprint_trail", 2.5, "wall_of_faces", 2.5);
        counter("build", "sign_turn", 3, "grave_marker", 3, "unwritten", 3, "haunted_item", 2.5);
        counter("fight", "ring_of_faces", 3, "horizon_watchers", 3, "procession", 2.5, "turning_heads", 2.5);
    }

    private static final Map<String, String[]> COUNTER_TEXT = new HashMap<>();
    static {
        COUNTER_TEXT.put("light", new String[] { "Fenerlerini sondurur", "It snuffs your lights" });
        COUNTER_TEXT.put("deep", new String[] { "Altindan kazar", "It digs beneath you" });
        COUNTER_TEXT.put("still", new String[] { "Seni durdugun yerde bulur", "It finds you where you stand" });
        COUNTER_TEXT.put("run", new String[] { "Kosmani kullanir", "It uses your running" });
        COUNTER_TEXT.put("build", new String[] { "Yaptigin seyleri bozar", "It tampers with what you build" });
        COUNTER_TEXT.put("fight", new String[] { "Kalabaliga cagirir", "It calls a crowd" });
    }

    private static String n(double v) { return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v); }

    // ---------------------------------------------------------
    //  Gece boyunca olcum
    // ---------------------------------------------------------
    private static final class NightData { double total, light, deep, still, run, build, fight; }
    private static final Map<String, NightData> nightData = new HashMap<>(); // oyuncu id -> olcumler
    private static NightData dataOf(Player p) { return nightData.computeIfAbsent(p.id(), k -> new NightData()); }

    // ---------------------------------------------------------
    //  Profil
    // ---------------------------------------------------------
    public static final class Profile {
        public final Map<String, Double> ema = new HashMap<>();
        public double nights;
        public double unpredictable;
        public String last = "";
        Profile() { for (String s : STYLES) ema.put(s, 0.0); }
    }

    public static final class Analysis {
        public final Map<String, Double> share = new HashMap<>();
        public String top;
        public double conf, dominance;
        public boolean unpredictable;
    }

    private static Profile readProfile(Player p) {
        Map<String, Object> m = Json.asMap(playerGet(p, PROFILE_KEY, null));
        Profile pr = new Profile();
        Map<String, Object> ema = m == null ? null : Json.asMap(m.get("ema"));
        if (m == null || ema == null) return pr;
        for (String s : STYLES) pr.ema.put(s, Json.num(ema.get(s), 0));
        pr.nights = Json.num(m.get("nights"), 0);
        pr.unpredictable = Json.num(m.get("unpredictable"), 0);
        pr.last = Json.str(m.get("last"), "");
        return pr;
    }

    private static void saveProfile(Player p, Profile pr) {
        Map<String, Object> ema = Json.newMap();
        for (String s : STYLES) ema.put(s, pr.ema.get(s));
        Map<String, Object> m = Json.newMap();
        m.put("ema", ema); m.put("nights", pr.nights); m.put("unpredictable", pr.unpredictable); m.put("last", pr.last);
        playerSet(p, PROFILE_KEY, m);
    }

    public static Analysis analyse(Profile pr) {
        double sum = 0;
        for (String s : STYLES) sum += pr.ema.get(s);
        if (sum == 0) sum = 1;
        Analysis a = new Analysis();
        for (String s : STYLES) a.share.put(s, pr.ema.get(s) / sum);
        String top = STYLES[0];
        for (String s : STYLES) if (a.share.get(s) > a.share.get(top)) top = s;
        a.top = top;
        a.conf = Math.min(1, pr.nights / 4);
        a.dominance = Math.max(0, Math.min(1, (a.share.get(top) - 0.25) / 0.45));
        a.unpredictable = pr.nights >= 2 && a.share.get(top) < 0.34;
        return a;
    }

    private static Analysis applyProfile(Player p) {
        Profile pr = readProfile(p);
        Analysis a = analyse(pr);
        if (!isEnabled("learn") || pr.nights < 2 || a.unpredictable) { weightMods.remove(p.id()); return a; }
        double strength = a.conf * a.dominance;
        Map<String, Double> mods = new HashMap<>();
        for (Map.Entry<String, Double> e : COUNTERS.get(a.top).entrySet()) mods.put(e.getKey(), 1 + (e.getValue() - 1) * strength);
        weightMods.put(p.id(), mods);
        return a;
    }

    private static void settleNight(Player p) {
        NightData d = nightData.remove(p.id());
        if (d == null || d.total < 120) return;
        Map<String, Double> score = new HashMap<>();
        score.put("light", Math.min(1, d.light / d.total));
        score.put("deep", Math.min(1, d.deep / d.total));
        score.put("still", Math.min(1, d.still / d.total));
        score.put("run", Math.min(1, d.run / d.total));
        score.put("build", Math.min(1, d.build / 120));
        score.put("fight", Math.min(1, d.fight / 20));
        Profile pr = readProfile(p);
        for (String s : STYLES) pr.ema.put(s, pr.ema.get(s) * 0.65 + score.get(s) * 0.35);
        pr.nights += 1;
        Analysis a = analyse(pr);
        boolean wasUnpredictable = a.unpredictable;
        pr.unpredictable = wasUnpredictable ? pr.unpredictable + 1 : 0;
        boolean changedStyle = !pr.last.isEmpty() && !pr.last.equals(a.top) && !wasUnpredictable;
        pr.last = a.top;
        saveProfile(p, pr);
        applyProfile(p);
        // geri bildirim
        if (wasUnpredictable) {
            addStat(p, "unpredictableNights");
            if (pr.unpredictable >= 2) { gainSanity(p, 10); title(p, tx("§bOngorulemez", "§bUnpredictable"), tx("§7seni cozemiyor", "§7it cannot read you"), 70); }
            else say(p, tx("§8(Duskwalker seni artik cozemiyor...)§r", "§8(the Duskwalker can no longer read you...)§r"));
        } else if (pr.nights >= 3 && a.conf * a.dominance > 0.25) {
            say(p, tx("§8(Duskwalker ogreniyor: §7", "§8(the Duskwalker is learning: §7") + tx(COUNTER_TEXT.get(a.top)) + tx("§8)§r", "§8)§r"));
            addStat(p, "learnedAgainst");
        }
        if (changedStyle) addStat(p, "styleChanges");
    }

    private static int lastT = -1;
    private static final Set<String> applied = new HashSet<>();

    // ---------------------------------------------------------
    //  Karsi hamle olaylari
    // ---------------------------------------------------------
    private static boolean isLightBlock(String id) { return id.endsWith("torch") || id.endsWith("lantern"); }

    private static final class Found {
        final int x, y, z; final Perm perm; final String id;
        Found(int x, int y, int z, Perm perm, String id) { this.x = x; this.y = y; this.z = z; this.perm = perm; this.id = id; }
        Vec3 v() { return new Vec3(x, y, z); }
    }

    private static Boolean lightSiege(Player player) {
        Dimension dim = player.dimension();
        Vec3 l = player.location();
        List<Found> found = new ArrayList<>();
        for (int i = 0; i < 260 && found.size() < 8; i++) {
            int px = (int) Math.floor(l.x()) + randInt(-10, 10);
            int py = (int) Math.floor(l.y()) + randInt(-3, 4);
            int pz = (int) Math.floor(l.z()) + randInt(-10, 10);
            Vec3 pos = new Vec3(px, py, pz);
            Block b = safe(() -> dim.getBlock(pos), (Block) null);
            if (b != null && isLightBlock(b.typeId())) {
                boolean dup = false;
                for (Found f : found) if (f.x == px && f.y == py && f.z == pz) { dup = true; break; }
                if (!dup) found.add(new Found(px, py, pz, b.permutation(), b.typeId()));
            }
        }
        if (found.size() < 2) return false;
        found.sort((a, b) -> Double.compare(hdist(a.v(), l), hdist(b.v(), l)));
        say(player, tx("§8(isiklar birer birer sonuyor...)§r", "§8(the lights go out one by one...)§r"));
        for (int i = 0; i < found.size(); i++) {
            final Found f = found.get(i);
            final int idx = i;
            Sys.runTimeout(() -> {
                safe(() -> { Block b = dim.getBlock(f.v()); if (b != null && b.typeId().equals(f.id)) b.setType("minecraft:air"); });
                smoke(dim, new Vec3(f.x + 0.5, f.y + 0.5, f.z + 0.5), 4);
                playAt(dim, "random.fizz", f.v(), 0.7, 0.6);
                if (isValid(player)) playTo(player, "duskwalker.tick", 0.5, 0.7 + idx * 0.05);
            }, 30 + i * 30);
        }
        // 45 sn sonra geri gelirler
        Sys.runTimeout(() -> {
            for (Found f : found) safe(() -> { Block b = dim.getBlock(f.v()); if (b != null && b.typeId().equals("minecraft:air")) b.setPermutation(f.perm); });
            if (isValid(player)) safe(() -> player.setActionBar(tx("§7(isiklar geri dondu)§r", "§7(the lights returned)§r")));
        }, 30 + found.size() * 30 + 45 * TICKS_PER_SEC);
        loseSanity(player, 5);
        addStat(player, "ev_light_siege");
        return true;
    }

    private static Boolean digger(Player player) {
        Dimension dim = player.dimension();
        Vec3 l = player.location();
        double a = Math.random() * Math.PI * 2;
        int steps = 9;
        for (int i = 0; i < steps; i++) {
            int d = 20 - i * 2;
            Sys.runTimeout(() -> playAt(dim, "dig.stone", new Vec3(l.x() + Math.cos(a) * d, l.y(), l.z() + Math.sin(a) * d), 1.1, 0.7), i * 14);
        }
        Sys.runTimeout(() -> {
            if (!isValid(player)) return;
            playTo(player, "duskwalker.whisper", 0.9, 0.55);
            Vec3 spot = standSpot(dim, behindPlayer(player, 5), 0, 2, 10);
            if (spot != null) {
                Entity e = spawnEffigy(dim, spot, 3);
                if (e != null) { faceToward(e, eyePos(player)); Sys.runTimeout(() -> despawn(e, true), 30); }
            }
            loseSanity(player, 8);
        }, steps * 14 + 10);
        return true;
    }

    // ---------------------------------------------------------
    //  Dosya (!dwdossier)
    // ---------------------------------------------------------
    private static String bar(double f) { int n = (int) Math.round(f * 12); return "§e" + "|".repeat(n) + "§8" + "|".repeat(12 - n); }

    public static void openDossier(Player player) {
        Profile pr = readProfile(player);
        Analysis a = analyse(pr);
        List<String> lines = new ArrayList<>();
        if (pr.nights < 2) {
            lines.add(tx("§7Duskwalker henuz seni yeterince gormedi.", "§7The Duskwalker has not watched you long enough."));
            lines.add(tx("§8Gozlenen gece: ", "§8Nights observed: ") + n(pr.nights));
        } else {
            lines.add(tx("§7Gozlenen gece: §f", "§7Nights observed: §f") + n(pr.nights));
            lines.add("");
            for (String s : STYLES) lines.add("§7" + tx(STYLE_NAMES.get(s)) + "\n" + bar(a.share.get(s)) + " §8" + Math.round(a.share.get(s) * 100) + "%");
            lines.add("");
            if (a.unpredictable) lines.add(tx("§bOngorulemezsin. Seni cozemiyor.", "§bYou are unpredictable. It cannot read you."));
            else lines.add(tx("§7Baskin tarzin: §f", "§7Dominant style: §f") + tx(STYLE_NAMES.get(a.top)) + "\n" + tx("§7Karsi hamle: §c", "§7Counter: §c") + tx(COUNTER_TEXT.get(a.top)) + "\n§8" + tx("Tarzini degistirirsen ogrendigini unutur.", "Change your style and it forgets."));
        }
        Forms.Action form = new Forms.Action().title(tx("Duskwalker Dosyasi", "Duskwalker Dossier")).body(String.join("\n", lines)).button(tx("Kapat", "Close"));
        showForm(player, form, r -> { });
    }

    public static void init() {
        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("learn") || !isNight()) return;
            for (Player p : World.getPlayers()) {
                try {
                    if (!isSurvival(p) || realm.contains(p.id()) || !p.dimension().id().equals("minecraft:overworld")) continue;
                    NightData d = dataOf(p);
                    d.total += 5;
                    if (refuge.getOrDefault(p.id(), 0) >= 3) d.light += 5;
                    if (isUnderground(p)) d.deep += 5;
                    if (stillSeconds(p) >= 10) d.still += 5;
                    if (p.isSprinting()) d.run += 5;
                } catch (Throwable e) { /* yut */ }
            }
        }, TICKS_PER_SEC * 5);

        try {
            Events.After.playerPlaceBlock.subscribe(ev -> safe(() -> { if (isNight()) dataOf(ev.player).build += 1; }));
            Events.After.entityHurt.subscribe(ev -> safe(() -> {
                Entity src = ev.damageSource != null ? ev.damageSource.damagingEntity : null;
                if (src != null && src.typeId().equals("minecraft:player") && isNight()) dataOf((Player) src).fight += 1;
            }));
        } catch (Throwable e) { /* yut */ }

        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("learn")) { lastT = -1; return; }
            int t = timeOfDay();
            if (lastT > 18000 && t < 4000) for (Player p : World.getPlayers()) safe(() -> settleNight(p));
            lastT = t;
            for (Player p : World.getPlayers()) {
                if (!applied.contains(p.id())) { applied.add(p.id()); safe(() -> applyProfile(p)); }
            }
        }, 40);

        try {
            Events.After.playerLeave.subscribe(ev -> { nightData.remove(ev.playerId); applied.remove(ev.playerId); });
        } catch (Throwable e) { /* yut */ }

        registerEvent(EventDef.of("light_siege").name("Light Siege").minDay(5).weight(1).cooldown(14L * TICKS_PER_MIN)
            .cond(p -> isNight()).run(Learn::lightSiege));
        registerEvent(EventDef.of("digger").name("The Digger").minDay(5).weight(1).cooldown(14L * TICKS_PER_MIN)
            .cond(p -> isUnderground(p)).run(Learn::digger));

        try {
            Events.Before.chatSend.subscribe(ev -> {
                if (!"!dwdossier".equals(ev.message)) return;
                ev.cancel = true;
                Player p = ev.sender;
                Sys.run(() -> { say(p, tx("§7Dosya aciliyor... sohbeti kapat.§r", "§7Opening dossier... close the chat.§r")); safe(() -> openDossier(p)); });
            });
        } catch (Throwable e) { /* yut */ }
    }
}

package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * DUSKWALKER v10 - CEKIRDEK (scripts/v10/core.js'in Java karsiligi).
 * Ortak yardimcilar, istatistik deposu ve "Korku Yonetmeni" (olay zamanlayicisi).
 * JS'teki en uste yazilan yan etkiler (subscribe / runInterval) init() icinde kayit edilir.
 */
public final class Core {
    private Core() {}

    public static final int TICKS_PER_SEC = 20;
    public static final int TICKS_PER_MIN = 1200;

    /** Bedrock surumuyle ayni dynamic property anahtarlari. */
    public static final class KEY {
        private KEY() {}
        public static final String SANITY = "duskwalker:sanity";
        public static final String LAST_SANITY_EVENT = "duskwalker:lastSanityEvent";
        public static final String EVIDENCE = "duskwalker:evidenceCount";
        public static final String CONFIG = "duskwalker:config";
        public static final String DAY_OVERRIDE = "duskwalker:dayOverride";
        public static final String DEFEATED = "duskwalker:defeated";
        public static final String ABDUCTED = "duskwalker:abductedUntil";
        public static final String STATS = "duskwalker:v10stats";
        public static final String ACHIEVEMENTS = "duskwalker:v10ach";
        public static final String KNOWN = "duskwalker:v10known";
    }

    // ---------------------------------------------------------
    //  Kucuk yardimcilar
    // ---------------------------------------------------------
    private static final Random RNG = new Random();

    /** min..max (ikisi de dahil) */
    public static int randInt(int min, int max) { return min + (int) Math.floor(Math.random() * (max - min + 1)); }
    public static double rand(double min, double max) { return min + Math.random() * (max - min); }
    public static boolean chance(double p) { return Math.random() < p; }
    public static <T> T pick(List<T> list) { return list.get((int) Math.floor(Math.random() * list.size())); }
    @SafeVarargs public static <T> T pick(T... list) { return list[(int) Math.floor(Math.random() * list.length)]; }
    public static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
    public static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    /** JS safe(fn, fallback) */
    public static <T> T safe(Supplier<T> fn, T fallback) {
        try { return fn.get(); } catch (Throwable e) { return fallback; }
    }
    /** JS safe(fn) (donus degeri yoksa / null fallback) */
    public static <T> T safe(Supplier<T> fn) {
        try { return fn.get(); } catch (Throwable e) { return null; }
    }
    /** JS safe(() => { ...yan etki... }) */
    public static void safe(Runnable fn) {
        try { fn.run(); } catch (Throwable e) { /* yut */ }
    }

    public static boolean isValid(Entity entity) {
        try {
            if (entity == null) return false;
            return entity.isValid();
        } catch (Throwable e) { return false; }
    }

    public static double hdist(Vec3 a, Vec3 b) { return Math.hypot(a.x() - b.x(), a.z() - b.z()); }
    public static double dist3(Vec3 a, Vec3 b) { return Math.sqrt((a.x() - b.x()) * (a.x() - b.x()) + (a.y() - b.y()) * (a.y() - b.y()) + (a.z() - b.z()) * (a.z() - b.z())); }

    // ---------------------------------------------------------
    //  Ayarlar / dunya durumu (main.js ile ayni veri)
    // ---------------------------------------------------------
    /** Ayar nesnesi (JSON haritasi); yoksa bos harita. */
    public static Map<String, Object> readConfig() {
        try {
            Object raw = World.getDynamicProperty(KEY.CONFIG);
            if (raw instanceof String s) {
                Map<String, Object> m = Json.asMap(Json.parse(s));
                if (m != null) return m;
            }
        } catch (Throwable e) { /* yut */ }
        return new LinkedHashMap<>();
    }

    /** cfg.enabled[key] haritasi veya null */
    private static Map<String, Object> enabledMap(Map<String, Object> cfg) { return Json.asMap(cfg.get("enabled")); }

    /** Acik onay gerektiren ayarlar: yalnizca ayar ACIKCA true ise acik sayilir */
    public static boolean isOptIn(String key) {
        Map<String, Object> en = enabledMap(readConfig());
        return en != null && Boolean.TRUE.equals(en.get(key));
    }

    public static boolean isEnabled(String key) {
        Map<String, Object> en = enabledMap(readConfig());
        return !(en != null && Boolean.FALSE.equals(en.get(key)));
    }

    public static int getDay() {
        try {
            Object override = World.getDynamicProperty(KEY.DAY_OVERRIDE);
            if (override instanceof Number n && n.doubleValue() > 0) return (int) n.doubleValue();
        } catch (Throwable e) { /* yut */ }
        try { int d = World.getDay(); if (d > 0) return d; } catch (Throwable e) { /* yut */ }
        try { return (int) Math.floor(World.getAbsoluteTime() / 24000.0) + 1; } catch (Throwable e) { /* yut */ }
        return 1;
    }

    public static double getIntensity() {
        Map<String, Object> cfg = readConfig();
        double mult = cfg.get("intensityMult") instanceof Number n ? n.doubleValue() : 1;
        double base = clamp((getDay() - 1) / 49.0, 0, 1);
        return clamp(base * mult, 0, 1.5);
    }

    public static int timeOfDay() { return safe(() -> World.getTimeOfDay(), 0); }
    public static boolean isNight() { int t = timeOfDay(); return t >= 13000 && t <= 23000; }
    public static boolean isDefeated() { return Boolean.TRUE.equals(safe(() -> World.getDynamicProperty(KEY.DEFEATED), (Object) false)) ; }

    public static boolean isUnderground(Player player) {
        return safe(() -> {
            Vec3 l = player.location();
            Block top = player.dimension().getTopmostBlock((int) Math.floor(l.x()), (int) Math.floor(l.z()));
            return top != null && top.y() > l.y() + 6;
        }, false);
    }

    public static boolean isSurvival(Player player) {
        String mode = safe(() -> player.getGameMode(), "survival");
        return !"creative".equals(mode) && !"spectator".equals(mode);
    }

    // ---------------------------------------------------------
    //  Saglamlik (sanity) ve kanit
    // ---------------------------------------------------------
    public static double getSanity(Player player) {
        Object s = safe(() -> player.getDynamicProperty(KEY.SANITY), (Object) null);
        return s instanceof Number n ? n.doubleValue() : 100;
    }

    public static void setSanity(Player player, double value) {
        safe(() -> player.setDynamicProperty(KEY.SANITY, clamp(value, 0, 100)));
    }

    /** Korku dinleyicileri: her anlamli korkuda (>= 6 kayip) cagrilir: fn(player, kayip, yeniSaglamlik) */
    public interface ScareListener { void onScare(Player player, double lost, double sanityNow); }
    public static final List<ScareListener> scareListeners = new ArrayList<>();
    private static final Map<String, List<Long>> scareLog = new HashMap<>();          // oyuncu id -> son korku tikleri
    public static final Map<String, Long> clarityUntil = new HashMap<>();             // oyuncu id -> berraklik bitis tiki
    public static final Map<String, Long> lullUntil = new HashMap<>();                // oyuncu id -> sakin donem bitis tiki (yonetmen susar)
    public static final Map<String, Long> lastScareTick = new HashMap<>();

    /** Alisma: son 5 dakikada ne kadar cok korkarsan, bir sonraki korku o kadar az etkiler (en fazla %35) */
    private static double habituationFactor(Player player, long tick) {
        if (!isEnabled("habituation")) return 1;
        List<Long> log = new ArrayList<>();
        for (long t : scareLog.getOrDefault(player.id(), new ArrayList<>())) if (tick - t < 6000) log.add(t);
        scareLog.put(player.id(), log);
        return Math.max(0.65, 1 - 0.07 * log.size());
    }

    public static void loseSanity(Player player, double amount) {
        long tick = Sys.currentTick();
        double scaled = (getPrefs(player).mild ? amount * 0.5 : amount) * perkMult(player, "fear") * pactMult(player, "fear");
        scaled *= habituationFactor(player, tick);
        if (clarityUntil.getOrDefault(player.id(), 0L) > tick) scaled *= 0.7;
        final double fscaled = scaled;
        double next = clamp(getSanity(player) - scaled, 0, 100);
        safe(() -> {
            player.setDynamicProperty(KEY.SANITY, next);
            player.setDynamicProperty(KEY.LAST_SANITY_EVENT, (double) Sys.currentTick());
        });
        minStat(player, "minSanity", Math.round(next));
        if (fscaled >= 6) {
            List<Long> log = scareLog.computeIfAbsent(player.id(), k -> new ArrayList<>());
            log.add(tick);
            lastScareTick.put(player.id(), tick);
            for (ScareListener fn : new ArrayList<>(scareListeners)) safe(() -> fn.onScare(player, fscaled, next));
        }
    }

    public static void gainSanity(Player player, double amount) {
        setSanity(player, getSanity(player) + amount);
    }

    public static void addEvidence(Player player, double amount) {
        if (!isEnabled("evidence")) return;
        safe(() -> {
            Object cur = player.getDynamicProperty(KEY.EVIDENCE);
            player.setDynamicProperty(KEY.EVIDENCE, (cur instanceof Number n ? n.doubleValue() : 0) + (amount == 0 ? 1 : amount));
        });
    }
    public static void addEvidence(Player player) { addEvidence(player, 1); }

    // ---------------------------------------------------------
    //  Ses / parcacik / ekran yardimcilari
    // ---------------------------------------------------------
    public static void playAt(Dimension dimension, String id, Vec3 loc, double volume, double pitch) {
        safe(() -> dimension.playSound(id, loc, Opts.Sound.of(volume, pitch)));
    }
    public static void playAt(Dimension dimension, String id, Vec3 loc) { playAt(dimension, id, loc, 1, 1); }

    public static void playTo(Player player, String id, double volume, double pitch) {
        safe(() -> player.playSound(id, Opts.Sound.of(volume, pitch)));
    }
    public static void playTo(Player player, String id) { playTo(player, id, 1, 1); }

    public static void particle(Dimension dimension, String id, Vec3 loc) {
        safe(() -> dimension.spawnParticle(id, loc));
    }

    public static void smoke(Dimension dimension, Vec3 loc, int count) {
        for (int i = 0; i < count; i++) {
            particle(dimension, "minecraft:large_smoke_particle", new Vec3(
                loc.x() + (Math.random() - 0.5), loc.y() + Math.random() * 1.8, loc.z() + (Math.random() - 0.5)));
        }
    }
    public static void smoke(Dimension dimension, Vec3 loc) { smoke(dimension, loc, 6); }

    public static void say(Player player, String text) { safe(() -> player.sendMessage(text)); }
    public static void actionbar(Player player, String text) { safe(() -> player.setActionBar(text)); }

    /** stay: 0 verilirse 40 tick */
    public static void title(Player player, String main, String sub, int stay) {
        safe(() -> player.setTitle(main, Opts.Title.of(5, stay == 0 ? 40 : stay, 20, sub == null ? "" : sub)));
    }
    public static void title(Player player, String main, String sub) { title(player, main, sub, 0); }

    /** Sureler SANIYE. */
    public static void fade(Player player, double r, double g, double b, double inT, double holdT, double outT) {
        if (getPrefs(player).noflash) return;
        safe(() -> player.cameraFade(Opts.Fade.of(r, g, b, inT, holdT, outT)));
    }

    public static void shake(Player player, double strength, double seconds) {
        if (getPrefs(player).noflash) return;
        safe(() -> player.cameraShake(strength, seconds, "rotational"));
    }

    public static void effect(Player player, String id, int ticks, int amplifier) {
        if (id.equals("darkness") && !isOptIn("darkness_fx")) return; // varsayilan: ekran kararmasi KAPALI
        safe(() -> player.addEffect(id, ticks, amplifier, false));
    }
    public static void effect(Player player, String id, int ticks) { effect(player, id, ticks, 0); }

    // ---------------------------------------------------------
    //  Konum yardimcilari
    // ---------------------------------------------------------
    private static double[] flatView(Player player) {
        Vec3 v = player.getViewDirection();
        double len = Math.hypot(v.x(), v.z());
        if (len == 0) len = 1;
        return new double[] { v.x() / len, v.z() / len };
    }

    public static Vec3 behindPlayer(Player player, double dist) {
        double[] v = flatView(player);
        Vec3 l = player.location();
        return new Vec3(l.x() - v[0] * dist, l.y(), l.z() - v[1] * dist);
    }

    public static Vec3 frontPlayer(Player player, double dist) {
        double[] v = flatView(player);
        Vec3 l = player.location();
        return new Vec3(l.x() + v[0] * dist, l.y(), l.z() + v[1] * dist);
    }

    public static Vec3 eyePos(Player player) {
        Vec3 l = player.location();
        return new Vec3(l.x(), l.y() + 1.6, l.z());
    }

    private static boolean airAt(Dimension dimension, double x, double y, double z) {
        return safe(() -> {
            Block b = dimension.getBlock(new Vec3(Math.floor(x), Math.floor(y), Math.floor(z)));
            return b != null && b.typeId().equals("minecraft:air");
        }, false);
    }

    private static boolean solidBelow(Dimension dimension, double x, double y, double z) {
        return safe(() -> {
            Block b = dimension.getBlock(new Vec3(Math.floor(x), Math.floor(y) - 1, Math.floor(z)));
            return b != null && !b.typeId().equals("minecraft:air") && !b.typeId().contains("water") && !b.typeId().contains("lava");
        }, false);
    }

    /**
     * Oyuncunun cevresinde durulabilir (2 blok hava + saglam zemin) bir nokta bulur.
     * Yerustunde en ust blok, yeralti/Nether'de oyuncunun kendi yuksekligi kullanilir. Bulamazsa null.
     */
    public static Vec3 standSpot(Dimension dimension, Vec3 center, double minDist, double maxDist, int tries) {
        boolean useSurface = dimension.id().equals("minecraft:overworld");
        for (int i = 0; i < (tries == 0 ? 14 : tries); i++) {
            double a = Math.random() * Math.PI * 2;
            double d = minDist + Math.random() * Math.max(0.01, maxDist - minDist);
            double x = center.x() + Math.cos(a) * d;
            double z = center.z() + Math.sin(a) * d;
            double y = center.y();
            if (useSurface) {
                Block top = safe(() -> dimension.getTopmostBlock((int) Math.floor(x), (int) Math.floor(z)), (Block) null);
                if (top != null && Math.abs(top.y() + 1 - center.y()) <= 8) y = top.y() + 1;
            }
            for (int dy : new int[] { 0, 1, -1 }) {
                double yy = Math.floor(y) + dy;
                if (airAt(dimension, x, yy, z) && airAt(dimension, x, yy + 1, z) && solidBelow(dimension, x, yy, z)) {
                    return new Vec3(Math.floor(x) + 0.5, yy, Math.floor(z) + 0.5);
                }
            }
        }
        return null;
    }
    public static Vec3 standSpot(Dimension dimension, Vec3 center, double minDist, double maxDist) { return standSpot(dimension, center, minDist, maxDist, 14); }

    /** Duskwalker'i hareketsiz bir "heykel" olarak dogurur (sadece bakar). Basarisizsa null. */
    public static Entity spawnStatue(Dimension dimension, Vec3 loc) {
        try {
            Entity e = dimension.spawnEntity("duskwalker:duskwalker", loc);
            safe(() -> e.addEffect("slowness", 20 * 90, 255, false));
            return e;
        } catch (Throwable err) { return null; }
    }

    /**
     * Efigi: hareketsiz, deri (skin) secilebilen heykel. skin: 1 soluk, 2 kaftanli, 3 yanik, 4 bogulmus.
     * Efigi varligi yuklenemezse klasik Duskwalker heykeline duser.
     */
    public static Entity spawnEffigy(Dimension dimension, Vec3 loc, int skin) {
        try {
            Entity e = dimension.spawnEntity("duskwalker:effigy", loc);
            if (skin > 0 && skin <= 4) safe(() -> e.triggerEvent("duskwalker:skin_" + skin));
            return e;
        } catch (Throwable err) { return spawnStatue(dimension, loc); }
    }

    public static void faceToward(Entity entity, Vec3 target) {
        safe(() -> entity.teleport(entity.location(), new Opts.Teleport().facing(target)));
    }

    public static void despawn(Entity entity, boolean withSmoke) {
        if (!isValid(entity)) return;
        if (withSmoke) safe(() -> smoke(entity.dimension(), entity.location(), 8));
        safe(() -> entity.triggerEvent("duskwalker:vanish"));
    }

    // ---------------------------------------------------------
    //  Form gosterme (sohbet acikken "UserBusy" doner, tekrar dene)
    // ---------------------------------------------------------
    public static <R extends Forms.Response> void showForm(Player player, Forms.Form<R> form, java.util.function.Consumer<R> handler) {
        showForm(player, form, handler, 0);
    }

    public static <R extends Forms.Response> void showForm(Player player, Forms.Form<R> form, java.util.function.Consumer<R> handler, int tries) {
        try {
            form.show(player, res -> {
                try {
                    if (res == null) return;
                    if (res.canceled) {
                        if ("UserBusy".equals(res.cancelationReason) && tries < 40) Sys.runTimeout(() -> showForm(player, form, handler, tries + 1), 20);
                        return;
                    }
                    handler.accept(res);
                } catch (Throwable e) { /* yut */ }
            });
        } catch (Throwable e) { /* yut */ }
    }

    // ---------------------------------------------------------
    //  Istatistik deposu (oyuncu basina JSON, bellekte onbellekli)
    // ---------------------------------------------------------
    private static final Map<String, Map<String, Object>> statCache = new HashMap<>();
    private static final Set<String> statDirty = new HashSet<>();

    public static Map<String, Object> getStats(Player player) {
        Map<String, Object> s = statCache.get(player.id());
        if (s == null) {
            s = new LinkedHashMap<>();
            Object raw = safe(() -> player.getDynamicProperty(KEY.STATS), (Object) null);
            if (raw instanceof String str) {
                Map<String, Object> parsed = safe(() -> Json.asMap(Json.parse(str)), (Map<String, Object>) null);
                if (parsed != null) s = parsed;
            }
            statCache.put(player.id(), s);
        }
        return s;
    }

    public static void addStat(Player player, String key, double amount) {
        Map<String, Object> s = getStats(player);
        s.put(key, Json.num(s.get(key), 0) + amount);
        statDirty.add(player.id());
    }
    public static void addStat(Player player, String key) { addStat(player, key, 1); }

    public static void setStat(Player player, String key, Object value) {
        getStats(player).put(key, value);
        statDirty.add(player.id());
    }

    public static void minStat(Player player, String key, double value) {
        Map<String, Object> s = getStats(player);
        if (!(s.get(key) instanceof Number) || value < Json.num(s.get(key), 0)) { s.put(key, value); statDirty.add(player.id()); }
    }

    public static void maxStat(Player player, String key, double value) {
        Map<String, Object> s = getStats(player);
        if (!(s.get(key) instanceof Number) || value > Json.num(s.get(key), 0)) { s.put(key, value); statDirty.add(player.id()); }
    }

    public static double getStat(Player player, String key, double fallback) {
        Object v = getStats(player).get(key);
        return v instanceof Number n ? n.doubleValue() : fallback;
    }
    public static double getStat(Player player, String key) { return getStat(player, key, 0); }

    private static void flushStats() {
        for (Player p : World.getPlayers()) {
            if (!statDirty.contains(p.id())) continue;
            safe(() -> p.setDynamicProperty(KEY.STATS, Json.stringify(getStats(p))));
            statDirty.remove(p.id());
        }
    }

    // ---------------------------------------------------------
    //  Oyuncu hareket takibi: ne kadar suredir kipirdamiyor?
    // ---------------------------------------------------------
    private static final class Motion { double x, y, z; long since; }
    private static final Map<String, Motion> motion = new HashMap<>();

    public static int stillSeconds(Player player) {
        Motion m = motion.get(player.id());
        if (m == null) return 0;
        return (int) Math.floor((Sys.currentTick() - m.since) / (double) TICKS_PER_SEC);
    }

    // ---------------------------------------------------------
    //  Isik siginagi durumu (light_refuge.js yazar, direktor okur)
    // ---------------------------------------------------------
    public static final Map<String, Integer> refuge = new HashMap<>(); // oyuncu id -> isik kaynagi sayisi

    /** Oyuncu basina kisisel zorluk carpani (adaptive.js yazar): >1 = daha az olay (merhamet) */
    public static final Map<String, Double> playerMult = new HashMap<>();

    /**
     * Oyuncu rahatlik tercihleri (comfort.js duzenler)
     *   mild    : korku kayiplari yariya iner
     *   noflash : ekran titremesi / kararma / flas yok
     *   nochat  : sahte sohbet ve sistem mesajlari yok
     *   pause   : yeni korku olaylari bu oyuncu icin durur
     */
    public static final class Prefs {
        public boolean mild, noflash, nochat, pause;
        public Prefs() {}
        public Prefs(boolean mild, boolean noflash, boolean nochat, boolean pause) { this.mild = mild; this.noflash = noflash; this.nochat = nochat; this.pause = pause; }
    }
    private static final String PREF_KEY = "duskwalker:v11prefs";
    private static final Map<String, Prefs> prefCache = new HashMap<>();

    public static Prefs getPrefs(Player player) {
        Prefs p = prefCache.get(player.id());
        if (p == null) {
            p = new Prefs();
            Object raw = safe(() -> player.getDynamicProperty(PREF_KEY), (Object) null);
            if (raw instanceof String str) {
                Map<String, Object> m = safe(() -> Json.asMap(Json.parse(str)), (Map<String, Object>) null);
                if (m != null) {
                    p.mild = Json.bool(m.get("mild"), false);
                    p.noflash = Json.bool(m.get("noflash"), false);
                    p.nochat = Json.bool(m.get("nochat"), false);
                    p.pause = Json.bool(m.get("pause"), false);
                }
            }
            prefCache.put(player.id(), p);
        }
        return p;
    }

    public static void setPrefs(Player player, Prefs prefs) {
        prefCache.put(player.id(), prefs);
        Map<String, Object> m = Json.newMap();
        m.put("mild", prefs.mild); m.put("noflash", prefs.noflash); m.put("nochat", prefs.nochat); m.put("pause", prefs.pause);
        safe(() -> player.setDynamicProperty(PREF_KEY, Json.stringify(m)));
    }

    /** Gece tipi / ay evresi carpanlari (nights.js yazar, direktor ve siginak okur) */
    public static final class Modifiers { public double gapMult = 1, regenMult = 1, noiseMult = 1; }
    public static final Modifiers modifiers = new Modifiers();

    /** Gercek ay evresi (0 = dolunay ... 7). Texture sirasi ile ayni. */
    public static int moonPhase() {
        Integer p = safe(() -> World.getMoonPhase(), (Integer) null);
        if (p != null) return ((p % 8) + 8) % 8;
        return (int) (Math.floor(safe(() -> (double) World.getAbsoluteTime(), 0.0) / 24000) % 8);
    }
    public static boolean isInRefuge(Player player) { return refuge.getOrDefault(player.id(), 0) >= 3; }

    // ---------------------------------------------------------
    //  Bilinen oyuncu adlari (Sahte Arkadas olayi icin)
    // ---------------------------------------------------------
    public static List<String> getKnownNames() {
        Object raw = safe(() -> World.getDynamicProperty(KEY.KNOWN), (Object) null);
        List<String> out = new ArrayList<>();
        if (!(raw instanceof String s)) return out;
        List<Object> l = safe(() -> Json.asList(Json.parse(s)), (List<Object>) null);
        if (l != null) for (Object o : l) if (o instanceof String str) out.add(str);
        return out;
    }

    // =========================================================
    //  KORKU YONETMENI
    //  Yeni olaylar burada kayit edilir. Yonetmen her 10 saniyede
    //  her oyuncu icin: bekleme suresi, gun, yogunluk, isik
    //  siginagi ve olay bekleme surelerine bakarak AGIRLIKLI bir
    //  olay secer. Olay "false" donerse (kosul saglanamadiysa)
    //  sayilmaz ve bekleme suresi baslamaz.
    // =========================================================
    /** Olay tanimi. run: false donerse olay sayilmaz; null veya true = basarili. cond: null = her zaman uygun. */
    public static final class EventDef {
        public String id;
        public String name;
        public double weight = 1;
        public int minDay = 1;
        public long cooldown = 8L * TICKS_PER_MIN;
        public boolean scary = true;
        public Predicate<Player> cond;
        public Function<Player, Boolean> run;

        public static EventDef of(String id) { EventDef d = new EventDef(); d.id = id; d.name = id; return d; }
        public EventDef name(String n) { this.name = n; return this; }
        public EventDef weight(double w) { this.weight = w; return this; }
        public EventDef minDay(int d) { this.minDay = d; return this; }
        public EventDef cooldown(long c) { this.cooldown = c; return this; }
        public EventDef scary(boolean s) { this.scary = s; return this; }
        public EventDef cond(Predicate<Player> c) { this.cond = c; return this; }
        public EventDef run(Function<Player, Boolean> r) { this.run = r; return this; }
        /** Donus degeri umursanmayan olaylar icin: her zaman basarili sayilir. */
        public EventDef runVoid(java.util.function.Consumer<Player> r) { this.run = p -> { r.accept(p); return Boolean.TRUE; }; return this; }
    }

    private static final Map<String, EventDef> registry = new LinkedHashMap<>();
    private static final Map<String, Long> cooldowns = new HashMap<>(); // "oyuncu|olay" -> bitis tiki
    private static final Map<String, Long> lastAny = new HashMap<>();   // oyuncu id -> son v10 olay tiki

    public static void registerEvent(EventDef def) { registry.put(def.id, def); }
    public static List<EventDef> listEvents() { return new ArrayList<>(registry.values()); }
    public static EventDef getEventDef(String id) { return registry.get(id); }

    public static boolean triggerEvent(String id, Player player, boolean force) {
        EventDef def = registry.get(id);
        if (def == null || !isValid(player)) return false;
        long tick = Sys.currentTick();
        if (!force) {
            if (!isEnabled(def.id)) return false;
            long until = cooldowns.getOrDefault(player.id() + "|" + id, 0L);
            if (tick < until) return false;
        }
        boolean ok;
        try { Boolean r = def.run == null ? Boolean.TRUE : def.run.apply(player); ok = !Boolean.FALSE.equals(r); } catch (Throwable e) { ok = false; }
        if (ok) {
            cooldowns.put(player.id() + "|" + id, tick + def.cooldown);
            lastAny.put(player.id(), tick);
            addStat(player, "ev_" + id);
            addStat(player, "v10events");
            logChronicle(player, "event", id);
        }
        return ok;
    }
    public static boolean triggerEvent(String id, Player player) { return triggerEvent(id, player, false); }

    private static void directPlayer(Player player, double intensity, int day) {
        long tick = Sys.currentTick();
        if (!isSurvival(player)) return;
        if (getPrefs(player).pause || realm.contains(player.id())) return;
        if (lullUntil.getOrDefault(player.id(), 0L) > tick) return;
        Object abducted = safe(() -> player.getDynamicProperty(KEY.ABDUCTED), (Object) 0.0);
        if (abducted instanceof Number n && n.doubleValue() > tick) return;

        if (!lastAny.containsKey(player.id())) { lastAny.put(player.id(), tick); return; }
        double gap = lerp(6, 3, clamp(intensity, 0, 1)) * TICKS_PER_MIN * modifiers.gapMult * playerMult.getOrDefault(player.id(), 1.0) * pactMult(player, "gap");
        if (tick - lastAny.get(player.id()) < gap) return;
        if (!chance(0.35)) return;

        List<EventDef> eligible = new ArrayList<>();
        for (EventDef def : registry.values()) {
            if (day < def.minDay || !isEnabled(def.id)) continue;
            if (tick < cooldowns.getOrDefault(player.id() + "|" + def.id, 0L)) continue;
            if (def.cond != null && !safe(() -> def.cond.test(player), false)) continue;
            eligible.add(def);
        }
        if (eligible.isEmpty()) return;

        // Isik siginaginda korku olaylarinin cogu bastirilir (Tilsim'in yerini alan karsi koyma yolu)
        if (safeZone.contains(player.id()) && chance(0.9)) {
            addStat(player, "sanctuarySaves");
            return;
        }
        if (buddies.contains(player.id()) && chance(0.5)) {
            addStat(player, "buddySaves");
            return;
        }
        if (isInRefuge(player) && chance(0.8)) {
            addStat(player, "refugeSaves");
            return;
        }

        Map<String, Double> wm = weightMods.getOrDefault(player.id(), new HashMap<>());
        double total = 0;
        for (EventDef d : eligible) total += d.weight * wm.getOrDefault(d.id, 1.0);
        double r = Math.random() * total;
        for (EventDef d : eligible) {
            r -= d.weight * wm.getOrDefault(d.id, 1.0);
            if (r <= 0) { triggerEvent(d.id, player, false); return; }
        }
    }

    // =========================================================
    //  v11 - ORTAK EK YARDIMCILAR (iki dilli metin, bakis acisi, esya verme)
    // =========================================================
    /** "tr" = Turkce surum, "en" = Ingilizce surum */
    public static final String LANG = "tr";

    /** JS tx([turkce, ingilizce]) -> Java tx(turkce, ingilizce) */
    public static String tx(String tr, String en) { return LANG.equals("tr") ? tr : en; }
    /** JS tx(pair) (pair = String[2]) */
    public static String tx(String[] pair) { return LANG.equals("tr") ? pair[0] : pair[1]; }

    /** Oyuncunun bakis yonu ile hedef arasindaki cosinus (1 = tam karsisinda, -1 = tam arkasinda) */
    public static double viewDot(Player player, Vec3 target) {
        return safe(() -> {
            Vec3 v = player.getViewDirection();
            Vec3 e = eyePos(player);
            double dx = target.x() - e.x(), dy = target.y() - e.y(), dz = target.z() - e.z();
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len == 0) len = 1;
            dx /= len; dy /= len; dz /= len;
            return v.x() * dx + v.y() * dy + v.z() * dz;
        }, 0.0);
    }

    public static boolean giveItem(Player player, String typeId, int amount) {
        return safe(() -> {
            Container inv = player.inventory();
            if (inv == null) return false;
            inv.addItem(new ItemStack(typeId, amount == 0 ? 1 : amount));
            return true;
        }, false);
    }
    public static boolean giveItem(Player player, String typeId) { return giveItem(player, typeId, 1); }

    /** Verilen x,z icin ayakta durulabilir yukseklik (refY civarinda asagi dogru arar); bulamazsa null */
    public static Integer groundY(Dimension dimension, double x, double refY, double z) {
        for (int dy = 3; dy >= -6; dy--) {
            int y = (int) Math.floor(refY) + dy;
            if (airAt(dimension, x, y, z) && airAt(dimension, x, y + 1, z) && solidBelow(dimension, x, y, z)) return y;
        }
        return null;
    }

    public static List<Player> onlinePlayers() { return World.getPlayers(); }

    // ---------------------------------------------------------
    //  Yetenekler (perks.js satin aldirir; carpanlar burada okunur)
    // ---------------------------------------------------------
    private static final String PERK_KEY = "duskwalker:v11perkset";
    private static final Map<String, List<String>> perkCache = new HashMap<>();
    public static final Map<String, Map<String, Double>> PERK_EFFECTS = new LinkedHashMap<>();
    static {
        PERK_EFFECTS.put("steady_hands", Map.of("fear", 0.9));
        PERK_EFFECTS.put("iron_nerves", Map.of("fear", 0.8));
        PERK_EFFECTS.put("soft_steps", Map.of("noise", 0.8));
        PERK_EFFECTS.put("silent_soles", Map.of("noise", 0.6));
        PERK_EFFECTS.put("lanternbearer", Map.of("regen", 1.5));
        PERK_EFFECTS.put("hearth_heart", Map.of("regen", 2.0));
    }

    public static List<String> getPerks(Player player) {
        List<String> list = perkCache.get(player.id());
        if (list == null) {
            list = new ArrayList<>();
            Object raw = safe(() -> player.getDynamicProperty(PERK_KEY), (Object) null);
            if (raw instanceof String s) {
                List<Object> l = safe(() -> Json.asList(Json.parse(s)), (List<Object>) null);
                if (l != null) for (Object o : l) if (o instanceof String str) list.add(str);
            }
            perkCache.put(player.id(), list);
        }
        return list;
    }
    public static void setPerks(Player player, List<String> list) {
        perkCache.put(player.id(), new ArrayList<>(list));
        safe(() -> player.setDynamicProperty(PERK_KEY, Json.stringify(list)));
    }
    public static boolean hasPerk(Player player, String id) { return getPerks(player).contains(id); }
    public static double perkMult(Player player, String kind) {
        double m = 1;
        for (String id : getPerks(player)) {
            Map<String, Double> e = PERK_EFFECTS.get(id);
            if (e != null && e.get(kind) != null) m *= e.get(kind);
        }
        return m;
    }

    // ---------------------------------------------------------
    //  Kronik (olay gunlugu): son 40 kayit, oyuncu basina
    // ---------------------------------------------------------
    private static final String CHRONICLE_KEY = "duskwalker:v11chronicle";

    public static void logChronicle(Player player, String kind, String ref) {
        safe(() -> {
            List<Object> list = readChronicle(player);
            Map<String, Object> rec = Json.newMap();
            rec.put("d", (double) getDay());
            rec.put("k", kind);
            rec.put("r", ref == null ? "" : ref);
            list.add(rec);
            while (list.size() > 40) list.remove(0);
            player.setDynamicProperty(CHRONICLE_KEY, Json.stringify(list));
        });
    }

    /** Kayitlar: { d: gun, k: tur, r: referans } haritalari listesi */
    public static List<Object> readChronicle(Player player) {
        Object raw = safe(() -> player.getDynamicProperty(CHRONICLE_KEY), (Object) null);
        if (raw instanceof String s) {
            List<Object> l = safe(() -> Json.asList(Json.parse(s)), (List<Object>) null);
            if (l != null) return l;
        }
        return new ArrayList<>();
    }

    /** Arkadas sistemi: birbirine yakin duran oyuncular (buddy.js yazar, direktor okur) */
    public static final Set<String> buddies = new HashSet<>();

    // v14 altyapisi ------------------------------------------------
    /** Ogrenen Avci: oyuncu id -> { olayId: carpan } (director agirliklarini oyuncuya gore degistirir) */
    public static final Map<String, Map<String, Double>> weightMods = new HashMap<>();
    /** Anlasmalar (pacts): oyuncu id -> { fear, noise, regen, gap } carpanlari */
    public static final Map<String, Map<String, Double>> pactMods = new HashMap<>();
    /** Siginak icindeki oyuncular (director olaylari buyuk olcude bastirilir) */
    public static final Set<String> safeZone = new HashSet<>();
    public static double pactMult(Player player, String kind) {
        Map<String, Double> m = pactMods.get(player.id());
        return m != null && m.get(kind) != null ? m.get(kind) : 1;
    }

    /** Paradoks Koridoru icindeki oyuncular (paradox.js yazar; diger sistemler sessiz kalir) */
    public static final Set<String> realm = new HashSet<>();

    // Dunyaya / oyuncuya ozel kucuk anahtar-deger deposu (JSON). Donus: Json bicimi (Map/List/Double/String/Boolean) veya fallback.
    public static Object worldGet(String key, Object fallback) {
        Object raw = safe(() -> World.getDynamicProperty(key), (Object) null);
        if (!(raw instanceof String s)) return fallback;
        try { return Json.parse(s); } catch (Throwable e) { return fallback; }
    }
    public static void worldSet(String key, Object value) {
        safe(() -> World.setDynamicProperty(key, Json.stringify(value)));
    }
    public static Object playerGet(Player player, String key, Object fallback) {
        Object raw = safe(() -> player.getDynamicProperty(key), (Object) null);
        if (!(raw instanceof String s)) return fallback;
        try { return Json.parse(s); } catch (Throwable e) { return fallback; }
    }
    public static void playerSet(Player player, String key, Object value) {
        safe(() -> player.setDynamicProperty(key, Json.stringify(value)));
    }

    // =========================================================
    //  Kayit (JS'te dosyanin en ustundeki yan etkiler)
    // =========================================================
    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        initialized = true;

        Sys.runInterval(Core::flushStats, 200);

        Events.Before.playerLeave.subscribe(ev -> {
            try {
                Player p = ev.player;
                if (statDirty.contains(p.id())) {
                    p.setDynamicProperty(KEY.STATS, Json.stringify(getStats(p)));
                    statDirty.remove(p.id());
                }
                statCache.remove(p.id());
            } catch (Throwable e) { /* yut */ }
        });

        // Oyuncu hareket takibi
        Sys.runInterval(() -> {
            long tick = Sys.currentTick();
            for (Player p : World.getPlayers()) {
                try {
                    Vec3 l = p.location();
                    Motion m = motion.get(p.id());
                    if (m == null) {
                        Motion nm = new Motion(); nm.x = l.x(); nm.y = l.y(); nm.z = l.z(); nm.since = tick;
                        motion.put(p.id(), nm);
                        continue;
                    }
                    if (Math.hypot(l.x() - m.x, l.z() - m.z) > 0.6 || Math.abs(l.y() - m.y) > 1.2) {
                        m.x = l.x(); m.y = l.y(); m.z = l.z(); m.since = tick;
                    }
                } catch (Throwable e) { /* yut */ }
            }
        }, 10);

        Events.After.playerSpawn.subscribe(ev -> {
            try {
                if (!ev.initialSpawn) return;
                List<String> names = getKnownNames();
                if (!names.contains(ev.player.name())) {
                    names.add(ev.player.name());
                    List<String> last = names.subList(Math.max(0, names.size() - 20), names.size());
                    World.setDynamicProperty(KEY.KNOWN, Json.stringify(last));
                }
            } catch (Throwable e) { /* yut */ }
        });

        // Korku yonetmeni: her 10 saniyede
        Sys.runInterval(() -> {
            if (isDefeated()) return;
            double intensity = getIntensity();
            int day = getDay();
            for (Player p : World.getPlayers()) safe(() -> directPlayer(p, intensity, day));
        }, 200);

        Events.After.playerLeave.subscribe(ev -> {
            try {
                String id = ev.playerId;
                lastAny.remove(id);
                prefCache.remove(id);
                playerMult.remove(id);
                perkCache.remove(id);
                buddies.remove(id);
                weightMods.remove(id); pactMods.remove(id); safeZone.remove(id);
                realm.remove(id);
                scareLog.remove(id); clarityUntil.remove(id); lullUntil.remove(id); lastScareTick.remove(id);
                motion.remove(id);
                refuge.remove(id);
                cooldowns.keySet().removeIf(k -> k.startsWith(id + "|"));
            } catch (Throwable e) { /* yut */ }
        });
    }
}

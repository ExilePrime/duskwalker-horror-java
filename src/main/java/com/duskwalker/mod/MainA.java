package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * main.js satir 1-1148 (bolum A): sabitler, config, butceli tarayici, yaratik state machine, faz sistemi,
 * gunluk sayfalari + kutuphane, final (Hesaplasma), saglamlik sistemi, zemin bozulmasi, kanit,
 * yapi yerlestirme, uyku bekcisi ve labirent uretecinin (generateMaze) sonu.
 * Not: generateMaze fonksiyonu satir 1148'de bittigi icin bolum sinirina (1130) kadar degil, sonuna kadar buradadir.
 */
public final class MainA {
    private MainA() {}

    // ------------------------------------------------------------------ anahtarlar
    public static final String SANITY = "duskwalker:sanity";
    public static final String LAST_SANITY_EVENT = "duskwalker:lastSanityEvent";
    public static final String TORCH_COUNT = "duskwalker:torchCount";
    public static final String ANNOYED_UNTIL = "duskwalker:annoyedUntil";
    public static final String LAST_POS_X = "duskwalker:lastPosX";
    public static final String LAST_POS_Z = "duskwalker:lastPosZ";
    public static final String LAST_POS_TICK = "duskwalker:lastPosTick";
    public static final String CAMPING_UNTIL = "duskwalker:campingUntil";
    public static final String STRUCTURE_INDEX = "duskwalker:structureIndex";
    public static final String NEXT_STRUCTURE = "duskwalker:nextStructure";
    public static final String WAS_SLEEPING = "duskwalker:wasSleeping";
    public static final String SLEEP_COOLDOWN = "duskwalker:sleepCooldown";
    public static final String ABDUCTED_UNTIL = "duskwalker:abductedUntil";
    public static final String WOLF_WARNED = "duskwalker:wolfWarned";
    public static final String NEXT_ZERO_SANITY = "duskwalker:nextZeroSanity";
    public static final String CONFIG_KEY = "duskwalker:config";
    public static final String DAY_OVERRIDE = "duskwalker:dayOverride";
    public static final String HEARTBEAT_LAST = "duskwalker:heartbeatLast";
    public static final String AYLIN_TRUST = "duskwalker:aylinTrust";
    public static final String AYLIN_BOND = "duskwalker:aylinBond";
    public static final String EVIDENCE_COUNT = "duskwalker:evidenceCount";
    public static final String NEXT_SEEP = "duskwalker:nextSeep";
    public static final String NEXT_WATCHER = "duskwalker:nextWatcher";

    /** JS truthiness: null/false/0/"" = false. */
    public static boolean truthy(Object o) {
        if (o == null) return false;
        if (o instanceof Boolean b) return b;
        if (o instanceof Number n) return n.doubleValue() != 0 && !Double.isNaN(n.doubleValue());
        if (o instanceof String s) return !s.isEmpty();
        return true;
    }

    // ================================================================== CONFIG
    /** Varsayilan "enabled" haritasi (sira korunur). */
    private static Map<String, Object> defaultEnabled() {
        Map<String, Object> e = new LinkedHashMap<>();
        for (String k : new String[] {
            "stalk", "jumpscare", "quickstrike", "hunt", "huntfast", "twinhunt",
            "whisper", "chat", "sign", "torch", "door", "possession", "reflection",
            "abduction", "wolfRevenge", "structures", "sanity", "moon", "heartbeat", "evidence", "seep", "watcher",
            "echo", "mimic", "stolen", "clock", "homevisit" }) e.put(k, Boolean.TRUE);
        e.put("darkness_fx", Boolean.FALSE);
        e.put("sanity_fog", Boolean.FALSE);
        return e;
    }

    /** { intensityMult: Double, enabled: Map<String,Object(Boolean)> } */
    public static Map<String, Object> cloneDefaultConfig() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("intensityMult", 1.0);
        cfg.put("enabled", defaultEnabled());
        return cfg;
    }

    public static Map<String, Object> getConfig() {
        try {
            Object raw = World.getDynamicProperty(CONFIG_KEY);
            if (raw instanceof String s) {
                Map<String, Object> parsed = Json.asMap(Json.parse(s));
                Map<String, Object> cfg = cloneDefaultConfig();
                if (parsed != null) {
                    if (parsed.get("intensityMult") instanceof Number n) cfg.put("intensityMult", n.doubleValue());
                    Map<String, Object> pe = Json.asMap(parsed.get("enabled"));
                    if (pe != null) cfgEnabled(cfg).putAll(pe);
                }
                return cfg;
            }
        } catch (Throwable e) { /* yut */ }
        return cloneDefaultConfig();
    }

    public static void saveConfig(Map<String, Object> cfg) {
        try { World.setDynamicProperty(CONFIG_KEY, Json.stringify(cfg)); } catch (Throwable e) { /* yut */ }
    }

    /** cfg.enabled (her zaman bos olmayan degistirilebilir harita; yoksa gecici bos harita) */
    public static Map<String, Object> cfgEnabled(Map<String, Object> cfg) {
        Map<String, Object> m = cfg == null ? null : Json.asMap(cfg.get("enabled"));
        return m != null ? m : new LinkedHashMap<>();
    }
    /** JS: cfg.enabled[key] === false */
    public static boolean cfgIsFalse(Map<String, Object> cfg, String key) { return Boolean.FALSE.equals(cfgEnabled(cfg).get(key)); }
    /** JS: cfg.enabled[key] === true */
    public static boolean cfgIsTrue(Map<String, Object> cfg, String key) { return Boolean.TRUE.equals(cfgEnabled(cfg).get(key)); }
    /** cfg.intensityMult */
    public static double cfgIntensityMult(Map<String, Object> cfg) { return Json.num(cfg.get("intensityMult"), 1.0); }

    // ================================================================== TICK BUTCESI
    public static final int MS_BUDGET = 3; // her interval cagrisinda bu isler icin harcanacak azami ms

    public static long nowMs() { try { return System.currentTimeMillis(); } catch (Throwable e) { return 0; } }

    /**
     * collectFn: bos kuyruk oldugunda YENI bir is listesi uretir (bir "tam gecis")
     * processFn: kuyruktaki tek bir ogeyi isler. Donen Runnable dogrudan Sys.runInterval'a verilebilir.
     * budgetMs <= 0 ise MS_BUDGET kullanilir (JS: budgetMs || MS_BUDGET).
     */
    public static <T> Runnable createBudgetedScanner(Supplier<List<T>> collectFn, Consumer<T> processFn, int budgetMs) {
        final ArrayDeque<T> queue = new ArrayDeque<>();
        return () -> {
            if (queue.isEmpty()) {
                try {
                    List<T> l = collectFn.get();
                    queue.clear();
                    if (l != null) queue.addAll(l);
                } catch (Throwable e) { queue.clear(); }
            }
            long start = nowMs();
            while (!queue.isEmpty() && (nowMs() - start) < (budgetMs > 0 ? budgetMs : MS_BUDGET)) {
                T item = queue.poll();
                try { processFn.accept(item); } catch (Throwable e) { /* yut */ }
            }
        };
    }
    public static <T> Runnable createBudgetedScanner(Supplier<List<T>> collectFn, Consumer<T> processFn) {
        return createBudgetedScanner(collectFn, processFn, 0);
    }

    // ================================================================== YARATIK STATE MACHINE
    /** JS'teki { mode, stateTicks, ...extra } nesnesi. Ek alanlar extra haritasinda (get/put/num yardimcilari). */
    public static final class CreatureState {
        public String mode;
        public long stateTicks;
        public final Map<String, Object> extra = new HashMap<>();
        public CreatureState(String mode) { this.mode = mode; }
        public Object get(String k) { return extra.get(k); }
        public void put(String k, Object v) { extra.put(k, v); }
        public double num(String k, double def) { return extra.get(k) instanceof Number n ? n.doubleValue() : def; }
        public boolean has(String k) { return extra.containsKey(k); }
    }

    /** CREATURE_BEHAVIORS[typeId] = fn(entity, state, dimension) */
    public interface CreatureBehavior { void run(Entity entity, CreatureState state, Dimension dimension); }

    private static final Map<String, CreatureState> creatureStates = new HashMap<>();
    public static final Map<String, CreatureBehavior> CREATURE_BEHAVIORS = new HashMap<>();

    public static void registerCreatureBehavior(String typeId, CreatureBehavior fn) { CREATURE_BEHAVIORS.put(typeId, fn); }

    public static CreatureState getCreatureState(Entity entity) {
        CreatureState s = creatureStates.get(entity.id());
        if (s == null) { s = new CreatureState("init"); creatureStates.put(entity.id(), s); }
        return s;
    }
    public static CreatureState enterCreatureState(Entity entity, String mode, Map<String, Object> extra) {
        CreatureState s = new CreatureState(mode);
        if (extra != null) s.extra.putAll(extra);
        creatureStates.put(entity.id(), s);
        return s;
    }
    public static CreatureState enterCreatureState(Entity entity, String mode) { return enterCreatureState(entity, mode, null); }
    public static void clearCreatureState(Entity entity) {
        try { creatureStates.remove(entity.id()); } catch (Throwable e) { /* yut */ }
    }

    private static final class ScanItem {
        final Entity entity; final Dimension dimension;
        ScanItem(Entity e, Dimension d) { entity = e; dimension = d; }
    }

    private static final Runnable creatureScanner = createBudgetedScanner(
        () -> {
            List<ScanItem> items = new ArrayList<>();
            for (String dimId : new String[] { "overworld", "nether", "the_end" }) {
                Dimension dimension;
                try { dimension = World.getDimension(dimId); } catch (Throwable e) { continue; }
                List<Entity> list;
                try { list = dimension.getEntities(Opts.EntityQuery.family("duskwalker_creature")); } catch (Throwable e) { continue; }
                for (Entity c : list) items.add(new ScanItem(c, dimension));
            }
            return items;
        },
        (ScanItem it) -> {
            boolean valid = false;
            try { valid = it.entity.isValid(); } catch (Throwable e) { /* yut */ }
            if (!valid) { clearCreatureState(it.entity); return; }
            CreatureBehavior behavior = CREATURE_BEHAVIORS.get(it.entity.typeId());
            if (behavior == null) return;
            CreatureState state = getCreatureState(it.entity);
            state.stateTicks++;
            try { behavior.run(it.entity, state, it.dimension); } catch (Throwable e) { /* yut */ }
        }
    );

    // ================================================================== FAZ / EVRIM
    public static final String LAST_PHASE_KEY = "duskwalker:lastPhase";

    public static final class Phase {
        public final String key, name; public final int minDay;
        public final String title, subtitle, message; public final String[] unlocks;
        Phase(String key, String name, int minDay, String title, String subtitle, String message, String... unlocks) {
            this.key = key; this.name = name; this.minDay = minDay; this.title = title; this.subtitle = subtitle; this.message = message; this.unlocks = unlocks;
        }
    }

    public static final Phase[] PHASES = {
        new Phase("awaken", "UYANIS", 1,
            "§7. . .§r", "§8bir seyler degisti§r",
            "§7Bir seyin farkina vardigini hissediyorsun... ama daha bir sey gormedin.§r",
            "stalk", "whisper", "chat", "sign", "torch", "door", "reflection"),
        new Phase("watch", "GOZLEM", 10,
            "§7O SENI GORUYOR§r", "§8artik sadece hissetmiyorsun§r",
            "§7Artik sadece hissetmiyorsun. Bazen onu gercekten goruyorsun.§r",
            "jumpscare", "seep", "stolen"),
        new Phase("hunted", "AV BASLADI", 20,
            "§4AV BASLADI§r", "§8sabri tukendi§r",
            "§4O artik bekleyecek kadar sabirli degil.§r",
            "quickstrike", "possession", "watcher", "echo", "homevisit"),
        new Phase("siege", "KUSATMA", 30,
            "§4KUSATMA§r", "§8hicbir yer guvenli degil§r",
            "§4Uykularin bile artik guvenli degil.§r",
            "hunt", "abduction", "mimic"),
        new Phase("collapse", "COKUS", 40,
            "§4COKUS§r", "§8bir tanesi yetmiyor artik§r",
            "§4Bir tanesi yetmiyor artik.§r",
            "huntfast", "twinhunt"),
        new Phase("final", "SON YUZLESME", 50,
            "§4§lSON YUZLESME§r", "§8kacis yok§r",
            "§4Artik saklanacak yer kalmadi. Yuzlesme zamani.§r")
    };

    public static int getCurrentPhaseIndex(double day) {
        int idx = 0;
        for (int i = 0; i < PHASES.length; i++) {
            if (day >= PHASES[i].minDay) idx = i;
        }
        return idx;
    }

    /** O ana kadar gecilmis TUM fazlarin acilislarinin birlesimi. */
    public static Set<String> getUnlockedEventSet(double day) {
        int idx = getCurrentPhaseIndex(day);
        Set<String> set = new HashSet<>();
        for (int i = 0; i <= idx; i++) set.addAll(Arrays.asList(PHASES[i].unlocks));
        return set;
    }

    // ================================================================== GUNLUK SAYFALARI
    public static final String[][] JOURNAL_PAGES = {
        {
            "GUNLUK 1\n\nBu koye uc ay once geldim. Toprak verimli, su yakin, komsular sessiz ama nazik. Ilk hafta hicbir sorun yoktu.",
            "Gun 3: Geceleri sesler duymaya basladim. Fisilti gibi ama kelime degil. Ilk basta ruzgar sandim, ama ruzgar yoktu.",
            "Gun 7: Sabah kalktigimda kapim ardina kadar aciktı. Kimse yoktu, kar da yoktu ki iz kalsin. Sadece acik bir kapi.",
            "Komsuma sordum. Gulumsedi ama gozleri gulmedi. 'Buraya yeni gelenler hep ayni seyi sorar' dedi. Baska bir sey soylemedi."
        },
        {
            "GUNLUK 2\n\nKoy halki bir kurala uyuyor gibi: 'Ona bakma.' Kim oldugunu sormadim, cunku zaten biliyordum kimden bahsettiklerini.",
            "Dun gece bakmamam gerektigini unuttum. Ormanin kenarinda bir sey durdu, uzun, hareketsiz. Baktim. Sadece bir saniye.",
            "O saniye yetti. Artik biliyorum: seciliyorsun. Bakanlar seciliyor. Ve secilenler bir daha eskisi gibi yalniz kalamiyor.",
            "Komsum dedi ki: 'Yalniz olanlari seciyor. Kalabalikta kaybolursan belki unutur.' Sanki unutmak diye bir sey varmis gibi."
        },
        {
            "GUNLUK 3\n\nBir kurt besledim, evcillestirdim, ona guvendim. Geceleri kapimin onunde uyurdu, gunduzleri pesimden ayrilmazdi.",
            "Uc hafta sonra tuhaflasti. Ulumaya basladi, sonra sustu, sonra tuyleri urperdi - sanki bir sey yaklasiyormus gibi.",
            "Duman vardi once. Sonra O oradaydi. Kurdumdan geriye hicbir sey kalmadi - ne bir ses, ne bir iz. Sadece duman.",
            "Simdi anliyorum: evcillestirdigin her sey, sevdigin her sey, ona bir davetiye. Artik hicbir seyi yanimda tutmaya cesaret edemiyorum."
        },
        {
            "GUNLUK 4\n\nBu kitabi bulan kisiye: mesaleleri sondurme. Isik onu tamamen durdurmuyor ama yavaslatiyor - bunu deneyerek ogrendim.",
            "Kapilarimi, pencerelerimi kontrol ediyorum her gece. Bazen hepsi kilitliyken bile bir kapinin kendi kendine actigini duyuyorum.",
            "Saglamligim dustukce her sey daha kotu oluyor. Fisiltilar siklasiyor, golgeler uzuyor.",
            "Bir arkadasim dedi ki: 'Korkma, sadece izliyor.' Ama izlemek yeterli degil artik. Aylar gectikce sadece izlemekle kalmiyor."
        },
        {
            "GUNLUK 5\n\nUykuda oldum. Basucumda durdugunu hissettim - gercekten hissettim, ruya degildi. Sabah uyandigimda hala oradaydi.",
            "Beni yakaladi. Bir anda baska bir yere goturdu - karanlik, bogucu, tanimadigim bir yer. Nasil kactigimi hatirlamiyorum.",
            "Kactim ama bedelini odedim: gozlerim gunlerce tam goremedi, sanki hala o karanlikta bir parcam kalmis gibi.",
            "Bir dahaki sefer kacamayabilirim. Bunu bilerek uyumak... bilemiyorum bu nasil bir sey."
        },
        {
            "GUNLUK 6\n\nEski koy kayitlarini buldum. Halk ona 'Alacakaranlik Yuruyen' diyor ama kayitlarda baska bir isim de geciyor: 'Bekci'.",
            "Bekci kelimesi tuhaf - sanki koruyucu bir seymis gibi ama bahsedilen baglam hep korku dolu. Kim ya da ne oldugu belirsiz.",
            "Bir kayitta soyle yaziyor: 'Bekci onunla birlikte yasiyor, ama onun bir parcasi degil. Onu tutan, onu durdurabilen de o.'",
            "Eger bu dogruysa, Bekci hala bir yerlerde olabilir. Aramaya karar verdim - baska secenegim yok gibi."
        },
        {
            "GUNLUK 7\n\nSoylenceyi buldum, daha eski bir kayitta. Bekci bir zamanlar siradan biriymis - genc bir kadin, kardesiyle yasiyormus.",
            "Kardesi yalnizlasmis, koyden uzaklasmis, sonunda kaybolmus. Kadin onu aramis ama buldugunda artik o eski kardesi degilmis.",
            "O bir seye donusmus - yalnizligin, terk edilmisligin bir yankisina. Kadin onu duraramamis, sadece yaninda kalabilmis.",
            "Simdi onu bir yerde tutuyor, kimse ona ulasamasin diye. Kendini feda etmis - hem ona, hem hepimize karsi."
        },
        {
            "GUNLUK 8\n\nBekci'nin nerede oldugunu bulmaya calisiyorum haftalardir. Izler hep ayni yere gidiyor sonra dumana karisiyor.",
            "Yer altinda bir sey olmali - eski bir yapi, bir zindan belki. Duskwalker'in en cok gorundugu yerlerin altinda.",
            "Buldugum her yapi bir sonrakine isaret ediyor gibi, sanki biri - ya da bir sey - beni yonlendiriyor. Iyi mi kotu mu bilmiyorum.",
            "Bu geceki fisiltilar daha net: 'yaklasiyorsun.' Kime yaklastigimi bilmiyorum ama devam edecegim."
        },
        {
            "GUNLUK 9\n\nBirisi bulmus olmali Bekci'yi - eminim. Yoksa bu topraklar cok once tamamen terk edilirdi, tek bir koylu bile kalmazdi.",
            "Belki de sen busun - bu gunlukleri toplayan, izleri takip eden. Belki senin sansin benimkinden iyi olur.",
            "Bir sey daha ogrendim: elli gun. Bir sekilde elli gun hayatta kalanlar, en azindan yuzlesme sansini kazaniyor.",
            "Hazirlan. Zindani bul. Bekci'yi bul. Ve eger bulursan - ona nazik davran. O da bir zamanlar sadece kardesini kurtarmaya calisan biriydi."
        },
        {
            "GUNLUK 10\n\nSon sayfa. Eger bunlarin hepsini bir araya getirdiysen, artik her seyi biliyorsun - benim bildigim kadarini en azindan.",
            "Onu yenmenin tek yolu yalniz savasmak degil. Tek yolu... birini bulmak. Onu hala taniyan, hala onemseyen birini.",
            "Bekci'yi bul. Zindanin nerede oldugunu artik biliyorsun ya da yakinda ogreneceksin. Onu ozgur birak.",
            "Ve sonra - elli gun dolunca - yalniz gitme. Bu benim yapamadigim sey. Umarim sen yapabilirsin. Iyi sanslar, kim olursan ol."
        }
    };

    public static final int TICKS_PER_MIN = 1200;
    public static final String TAMED_AT = "duskwalker:tamedAt";
    public static final String DELAY = "duskwalker:delay";
    public static final String TRIGGERED = "duskwalker:triggered";
    public static final String STORY_SHOWN = "duskwalker:storyShown";
    public static final String NEXT_VISIBLE = "duskwalker:nextVisible";
    public static final String NEXT_AMBIENT = "duskwalker:nextAmbient";
    public static final String MILESTONE_KEY = "duskwalker:milestone_";

    public static final String[] STORY_LINES = {
        "§7...§r",
        "§7Bu topraklar bir zamanlar sessizdi.§r",
        "§7Sonra O geldi; dumanin icinden, adi unutulmus biri.§r",
        "§7Koyluler ona §4Duskwalker§7 demeye basladi - alacakaranlikta yuruyen.§r",
        "§7Simdilik uzakta, sezilir sezilmez. Ama gunler gectikce güclenecek.§r",
        "§7Yalniz kalirsan... bakma, o zaten seni izliyor olabilir.§r",
        "§7Onu yenmenin tek yolu... sanirim birini bulmak.§r"
    };

    /** JS: { 10: [...], 25: [...], 50: [...] } */
    public static final Map<Integer, String[]> MILESTONE_LINES = new LinkedHashMap<>();
    static {
        MILESTONE_LINES.put(10, new String[] { "§7Bir seyler degisiyor. Gölgeler biraz daha uzun surer oldu.§r" });
        MILESTONE_LINES.put(25, new String[] { "§7Artik saklanamazsin. O, her yerde hissediliyor.§r" });
        MILESTONE_LINES.put(50, new String[] { "§4Duskwalker§r artik bu dünyanin efendisi.", "§4Duskwalker§r: Burasi artik benim." });
    }

    public static final String[] TAUNT_LINES = {
        "§4Duskwalker§r: Onu sana birakmayacagim.",
        "§4Duskwalker§r: Yalnizlik... en tatli olanidir.",
        "§4Duskwalker§r: Bak nasil da sessiz simdi.",
        "§4Duskwalker§r: Bir dahakine daha hizli ol.",
        "§4Duskwalker§r: Ben hep dumanin icindeyim."
    };

    public static final String[] CHAT_LINES = {
        "§4Duskwalker§r: Seni izliyorum.",
        "§4Duskwalker§r: Arkana bakma.",
        "§4Duskwalker§r: Burasi benim topragim.",
        "§4Duskwalker§r: Yalniz misin?",
        "§4Duskwalker§r: Duman her zaman geri getirir beni.",
        "§4Duskwalker§r: Kapilar seni durdurmaz. Beni de.",
        "§4Duskwalker§r: Isik seni kurtarmayacak."
    };

    public static final String[] SIGN_MESSAGES = { "O BURADAYDI", "GERI DON", "IZLIYORUM", "YALNIZ KALMA", "DUMAN GELIYOR", "ARKANA BAKMA" };
    public static final String[] WHISPER_SOUNDS = { "mob.endermen.stare", "ambient.cave", "step.stone" };

    // ================================================================== kucuk yardimcilar
    /** JS randInt: ikisi de floor edilir; max <= min ise min. */
    public static int randInt(double min, double max) {
        double mn = Math.floor(min), mx = Math.floor(max);
        if (mx <= mn) return (int) mn;
        return (int) Math.floor(mn + Math.random() * (mx - mn + 1));
    }
    public static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    public static int jitter(double base, double spread) {
        return (int) Math.max(200, Math.round(base * (1 - spread + Math.random() * spread * 2)));
    }

    public static int getDay() {
        try {
            Object override = World.getDynamicProperty(DAY_OVERRIDE);
            if (override instanceof Number n && n.doubleValue() > 0) return (int) n.doubleValue();
        } catch (Throwable e) { /* yut */ }
        try { int d = World.getDay(); if (d > 0) return d; } catch (Throwable e) { /* yut */ }
        try { return (int) Math.floor(World.getAbsoluteTime() / 24000.0) + 1; } catch (Throwable e) { /* yut */ }
        return 1;
    }

    // ================================================================== DOLUNAY
    public static final String MOON_WARNED_KEY = "duskwalker:moonWarned_";

    public static boolean isFullMoonNight() {
        int phase = (getDay() - 1) % 8;
        return phase == 0;
    }

    /** cfg null olabilir. */
    public static double getIntensity(Map<String, Object> cfg) {
        int day = getDay();
        double base = Math.min(1, Math.max(0, (day - 1) / 49.0));
        if (isFullMoonNight() && (cfg == null || !cfgIsFalse(cfg, "moon"))) base = Math.min(1, base + 0.15);
        double mult = (cfg != null && cfg.get("intensityMult") instanceof Number n) ? n.doubleValue() : 1.0;
        return base * mult;
    }
    public static double getIntensity() { return getIntensity(null); }

    /** 50. gunde gorsel degisim */
    public static void applyDominant(Entity entity) {
        try {
            if (getDay() >= 50) entity.setProperty("duskwalker:dominant", true);
        } catch (Throwable e) { /* yut */ }
    }

    // Varyasyonlar kaldirildi (uyumluluk fonksiyonlari)
    public static int applyVariant(Entity entity, Integer forcedVariant) { return 0; }
    public static int applyVariant(Entity entity) { return 0; }
    public static double getVariantPitch(int variant) { return 1.0; }
    public static String getHuntEventName(int variant, boolean fast) { return fast ? "duskwalker:hunt_start_fast" : "duskwalker:hunt_start"; }
    public static double getVariantStrikeDamage(int variant) { return 4; }

    // ================================================================== FINAL (HESAPLASMA)
    public static final String DEFEATED = "duskwalker:defeated";
    public static final String FINALE_ACTIVE = "duskwalker:finaleActive";

    public static boolean isDefeated() {
        try { return truthy(World.getDynamicProperty(DEFEATED)); } catch (Throwable e) { return false; }
    }

    public static void triggerFinale(Player player) {
        try {
            if (isDefeated() || truthy(World.getDynamicProperty(FINALE_ACTIVE))) return;
            World.setDynamicProperty(FINALE_ACTIVE, true);
            try { World.getDimension("overworld").runCommand("weather thunder 6000"); } catch (Throwable e) { /* yut */ }
            String[] openLines = {
                "§dAylin§r: Hazir misin?",
                "§dAylin§r: Yaninda olacagim. Bu sefer yalniz degilsin.",
                "§4Duskwalker§r: Onu benden alamazsin."
            };
            finaleSendOpen(player, openLines, 0);
        } catch (Throwable e) { /* yut */ }
    }

    private static void finaleSendOpen(Player player, String[] openLines, int i) {
        if (i < openLines.length) {
            try { World.sendMessage(openLines[i]); } catch (Throwable e) { /* yut */ }
            Sys.runTimeout(() -> finaleSendOpen(player, openLines, i + 1), 50);
        } else {
            finaleBeginEncounter(player);
        }
    }

    private static void finaleBeginEncounter(Player player) {
        final Dimension dimension = player.dimension();
        Vec3 view = player.getViewDirection();
        Vec3 pl = player.location();
        Vec3 center = new Vec3(pl.x() + view.x() * 10, pl.y(), pl.z() + view.z() * 10);
        final Vec3 pos = findSpawnSpot(dimension, center, 0, 3);
        smokePuff(dimension, pos, 14);
        try { dimension.playSound("mob.wither.spawn", pos, Opts.Sound.of(1.0, 0.3)); } catch (Throwable e) { /* yut */ }
        Sys.runTimeout(() -> {
            Entity monster;
            try { monster = dimension.spawnEntity("duskwalker:duskwalker", pos); }
            catch (Throwable e) { World.setDynamicProperty(FINALE_ACTIVE, false); return; }
            applyDominant(monster);
            int finaleVariant = applyVariant(monster);
            try { monster.triggerEvent(getHuntEventName(finaleVariant, true)); } catch (Throwable e) { /* yut */ }
            long deadline = Sys.currentTick() + 90 * 20;
            finaleCheck(player, monster, dimension, deadline);
        }, 40);
    }

    private static void finaleCheck(Player player, Entity monster, Dimension dimension, long deadline) {
        try {
            boolean alive = false;
            try { alive = player.isValid(); } catch (Throwable e) { /* yut */ }
            long tick = Sys.currentTick();
            long remaining = Math.max(0, Math.round((deadline - tick) / 20.0));
            if (alive) { try { player.setActionBar("§4Dayan! " + remaining + "s§r"); } catch (Throwable e) { /* yut */ } }

            if (!alive) {
                // oyuncu bu seferlik basaramadi - tekrar denenebilir
                World.setDynamicProperty(FINALE_ACTIVE, false);
                try { monster.triggerEvent("duskwalker:hunt_end"); } catch (Throwable e) { /* yut */ }
                try { monster.triggerEvent("duskwalker:vanish"); } catch (Throwable e) { /* yut */ }
                return;
            }
            if (tick >= deadline) {
                World.setDynamicProperty(DEFEATED, true);
                World.setDynamicProperty(FINALE_ACTIVE, false);
                try { monster.triggerEvent("duskwalker:hunt_end"); } catch (Throwable e) { /* yut */ }
                try { smokePuff(dimension, monster.location(), 16); } catch (Throwable e) { /* yut */ }
                try { monster.triggerEvent("duskwalker:vanish"); } catch (Throwable e) { /* yut */ }
                String[] closingLines = {
                    "§7Duman incelir... ve bu sefer geri donmez.§r",
                    "§dAylin§r: Basardik. O simdilik gitti... belki de sonsuza dek.§r",
                    "§7Bu dunyada Alacakaranlik Yuruyen'in hakimiyetine son verdin.§r"
                };
                finaleSendClose(closingLines, 0);
                return;
            }
            Sys.runTimeout(() -> finaleCheck(player, monster, dimension, deadline), 20);
        } catch (Throwable e) { World.setDynamicProperty(FINALE_ACTIVE, false); }
    }

    private static void finaleSendClose(String[] closingLines, int j) {
        if (j < closingLines.length) {
            try { World.sendMessage(closingLines[j]); } catch (Throwable e) { /* yut */ }
            Sys.runTimeout(() -> finaleSendClose(closingLines, j + 1), 60);
        }
    }

    // ================================================================== yardimcilar: spawn noktasi / duman
    /** tries <= 0 ise 6. Her zaman bir konum doner. */
    public static Vec3 findSpawnSpot(Dimension dimension, Vec3 center, double minDist, double maxDist, int tries) {
        int n = tries <= 0 ? 6 : tries;
        for (int i = 0; i < n; i++) {
            double angle = Math.random() * Math.PI * 2;
            double dist = minDist + Math.random() * Math.max(0.01, maxDist - minDist);
            Vec3 candidate = new Vec3(center.x() + Math.cos(angle) * dist, center.y(), center.z() + Math.sin(angle) * dist);
            try {
                Block feet = dimension.getBlock(candidate);
                Block head = dimension.getBlock(new Vec3(candidate.x(), candidate.y() + 1, candidate.z()));
                if (feet != null && head != null && feet.typeId().equals("minecraft:air") && head.typeId().equals("minecraft:air")) return candidate;
            } catch (Throwable e) { /* yut */ }
        }
        return new Vec3(center.x(), center.y() + 0.1, center.z());
    }
    public static Vec3 findSpawnSpot(Dimension dimension, Vec3 center, double minDist, double maxDist) {
        return findSpawnSpot(dimension, center, minDist, maxDist, 6);
    }

    public static void smokePuff(Dimension dimension, Vec3 pos, int count) {
        for (int i = 0; i < count; i++) {
            Sys.runTimeout(() -> {
                try {
                    dimension.spawnParticle("minecraft:large_smoke_particle", new Vec3(
                        pos.x() + (Math.random() - 0.5), pos.y() + Math.random() * 1.8, pos.z() + (Math.random() - 0.5)));
                } catch (Throwable e) { /* yut */ }
            }, i * 2);
        }
    }

    // ================================================================== SAGLAMLIK
    /** JS: yoksa 100 yazar ve dondurur. */
    public static double getSanity(Player player) {
        Object s = null;
        try { s = player.getDynamicProperty(SANITY); } catch (Throwable e) { /* yut */ }
        if (!(s instanceof Number)) {
            try { player.setDynamicProperty(SANITY, 100.0); } catch (Throwable e) { /* yut */ }
            return 100;
        }
        return ((Number) s).doubleValue();
    }

    public static void loseSanity(Player player, double amount) {
        try {
            double amt = amount;
            // AYLIN'E ADAK - guven bagi tamamlaninca korkunun etkisi hafifler
            try { if (truthy(player.getDynamicProperty(AYLIN_BOND))) amt = amt * 0.85; } catch (Throwable e) { /* yut */ }
            double s = Math.max(0, Math.min(100, getSanity(player) - amt));
            player.setDynamicProperty(SANITY, s);
            player.setDynamicProperty(LAST_SANITY_EVENT, (double) Sys.currentTick());
        } catch (Throwable e) { /* yut */ }
    }

    private static void sanityTick() {
        if (isDefeated()) return;
        Map<String, Object> cfg = getConfig();
        if (cfgIsFalse(cfg, "sanity")) return;
        long tick = Sys.currentTick();
        List<Player> players = World.getPlayers();
        for (Player player : players) {
            try {
                Object lastO = player.getDynamicProperty(LAST_SANITY_EVENT);
                double last = lastO instanceof Number n ? n.doubleValue() : 0;
                double sanity = getSanity(player);
                // uzun sure korku yasamadiysa yavasca toparlanir
                if (tick - last > 2 * TICKS_PER_MIN && sanity < 100) {
                    player.setDynamicProperty(SANITY, Math.min(100, sanity + 2));
                }
                // cok dusuk saglamlikta nadiren hafif bulanti + ekstra fisilti
                if (sanity < 30 && Math.random() < 0.03) {
                    try { player.addEffect("nausea", 30, 0, false); } catch (Throwable e) { /* yut */ }
                    try { MainC.doWhisper(player); } catch (Throwable e) { /* yut */ }
                }
                // sahte gorunmeler
                if (sanity < 40 && Math.random() < 0.012) {
                    doFakeSighting(player);
                }
                // coklu oyunculuda paylasilan korku
                if (players.size() > 1 && Math.random() < 0.05) {
                    for (Player other : players) {
                        if (other.id().equals(player.id())) continue;
                        try {
                            if (!other.dimension().id().equals(player.dimension().id())) continue;
                            double d = Math.hypot(player.location().x() - other.location().x(), player.location().z() - other.location().z());
                            if (d < 20 && getSanity(other) < 25) {
                                player.setDynamicProperty(SANITY, Math.max(0, getSanity(player) - 1));
                                break;
                            }
                        } catch (Throwable e) { /* yut */ }
                    }
                }
            } catch (Throwable e) { /* yut */ }
        }
    }

    public static void doFakeSighting(Player player) {
        try {
            Dimension dimension = player.dimension();
            Vec3 view = player.getViewDirection();
            double dist = 12 + Math.random() * 15;
            Vec3 pl = player.location();
            Vec3 center = new Vec3(pl.x() + view.x() * dist, pl.y(), pl.z() + view.z() * dist);
            Vec3 pos = findSpawnSpot(dimension, center, 0, 2);
            final Entity entity;
            try { entity = dimension.spawnEntity("duskwalker:duskwalker", pos); } catch (Throwable e) { return; }
            applyVariant(entity);
            Sys.runTimeout(() -> {
                try { entity.triggerEvent("duskwalker:vanish"); } catch (Throwable e) { /* yut */ }
            }, randInt(4, 8));
        } catch (Throwable e) { /* yut */ }
    }

    // ================================================================== SANITY 0 SONUCU
    private static void zeroSanityTick() {
        if (isDefeated()) return;
        Map<String, Object> cfg = getConfig();
        if (cfgIsFalse(cfg, "sanity")) return;
        long tick = Sys.currentTick();
        for (Player player : World.getPlayers()) {
            try {
                if (getSanity(player) > 0) { player.setDynamicProperty(NEXT_ZERO_SANITY, null); continue; }
                Object next = player.getDynamicProperty(NEXT_ZERO_SANITY);
                if (!(next instanceof Number)) {
                    player.setDynamicProperty(NEXT_ZERO_SANITY, (double) (tick + randInt(600, 1200)));
                    continue;
                }
                if (tick < ((Number) next).doubleValue()) continue;
                player.setDynamicProperty(NEXT_ZERO_SANITY, (double) (tick + randInt(1400, 2600)));
                doZeroSanityEvent(player);
            } catch (Throwable e) { /* yut */ }
        }
    }

    public static void doZeroSanityEvent(Player player) {
        try {
            Dimension dimension = player.dimension();
            if (Math.random() < 0.5) {
                // gercek halusinasyon: birden fazla sahte Duskwalker etrafta belirir
                int count = randInt(2, 3);
                for (int i = 0; i < count; i++) {
                    Sys.runTimeout(() -> {
                        try {
                            Vec3 view = player.getViewDirection();
                            double angleOffset = (Math.random() - 0.5) * Math.PI;
                            double dist = 6 + Math.random() * 10;
                            double angle = Math.atan2(view.z(), view.x()) + angleOffset;
                            Vec3 pl = player.location();
                            Vec3 center = new Vec3(pl.x() + Math.cos(angle) * dist, pl.y(), pl.z() + Math.sin(angle) * dist);
                            Vec3 pos = findSpawnSpot(dimension, center, 0, 2);
                            Entity entity = dimension.spawnEntity("duskwalker:duskwalker", pos);
                            applyVariant(entity);
                            Sys.runTimeout(() -> { try { entity.triggerEvent("duskwalker:vanish"); } catch (Throwable e) { /* yut */ } }, randInt(10, 24));
                        } catch (Throwable e) { /* yut */ }
                    }, i * 8);
                }
                try { player.addEffect("nausea", 60, 1, false); } catch (Throwable e) { /* yut */ }
                try { if (cfgIsTrue(getConfig(), "darkness_fx")) player.addEffect("darkness", 60, 0, false); } catch (Throwable e) { /* yut */ }
                try { player.sendMessage("§4(...gercek degildi. degil miydi?)§r"); } catch (Throwable e) { /* yut */ }
            } else {
                // yon kaybi: gecici sersemleme
                try {
                    double yawJitter = (Math.random() - 0.5) * 140;
                    player.runCommand("tp @s ~ ~ ~ ~" + String.format(Locale.ROOT, "%.1f", yawJitter) + " ~");
                } catch (Throwable e) { /* yut */ }
                try { player.addEffect("nausea", 100, 0, false); } catch (Throwable e) { /* yut */ }
                try { player.playSound("mob.endermen.stare", Opts.Sound.of(0.5, 0.4)); } catch (Throwable e) { /* yut */ }
                try { player.sendMessage("§7(hangi yone baktigini bilemiyorsun...)§r"); } catch (Throwable e) { /* yut */ }
            }
        } catch (Throwable e) { /* yut */ }
    }

    // ================================================================== ZEMIN BOZULMASI
    public static void corruptGround(Dimension dimension, Vec3 pos) {
        try {
            final Vec3 blockPos = new Vec3(Math.floor(pos.x()), Math.floor(pos.y()) - 1, Math.floor(pos.z()));
            Block block = dimension.getBlock(blockPos);
            if (block == null) return;
            final String original = block.typeId();
            if (original.equals("minecraft:air") || original.contains("water") || original.contains("lava") || original.contains("soul_soil")) return;
            block.setType("minecraft:soul_soil");
            Sys.runTimeout(() -> {
                try {
                    Block b2 = dimension.getBlock(blockPos);
                    if (b2 != null && b2.typeId().equals("minecraft:soul_soil")) b2.setType(original);
                } catch (Throwable e) { /* yut */ }
            }, 6000);
        } catch (Throwable e) { /* yut */ }
    }

    // ================================================================== KANIT
    /** JS: amount || 1 */
    public static void addEvidence(Player player, double amount) {
        try {
            if (cfgIsFalse(getConfig(), "evidence")) return;
            Object cur = player.getDynamicProperty(EVIDENCE_COUNT);
            double count = (cur instanceof Number n ? n.doubleValue() : 0) + (amount == 0 ? 1 : amount);
            player.setDynamicProperty(EVIDENCE_COUNT, count);
            if (count % 5 == 0) {
                try { player.sendMessage("§7(" + (long) count + " kanit topladin - bir sonraki yapi daha yakinda beliriyor...)§r"); } catch (Throwable e) { /* yut */ }
                try {
                    long tick = Sys.currentTick();
                    Object next = World.getDynamicProperty(NEXT_STRUCTURE);
                    if (next instanceof Number nn && nn.doubleValue() > tick) {
                        double remaining = nn.doubleValue() - tick;
                        double shortened = tick + Math.max(20 * 20, Math.round(remaining * 0.6));
                        World.setDynamicProperty(NEXT_STRUCTURE, shortened);
                    }
                } catch (Throwable e) { /* yut */ }
            }
        } catch (Throwable e) { /* yut */ }
    }
    public static void addEvidence(Player player) { addEvidence(player, 1); }

    // ================================================================== GUNLUK KITAPLARI / KUTUPHANE
    public static final String JMARK = "§8​J";
    public static final String JOURNALS_KEY = "duskwalker:v15journals";
    public static final String STRUCT_REG = "duskwalker:v15structs";

    /** Gunluk bir KITAP ESYASI olarak verilir. pages kullanilmaz (JS'te de kullanilmiyordu). num null/0 ise "?" */
    public static ItemStack createJournalBook(String[] pages, Integer num) {
        ItemStack book;
        try { book = new ItemStack("minecraft:book", 1); } catch (Throwable e) { return null; }
        try {
            int n = num == null ? 0 : num;
            book.nameTag = Core.tx("§6Gunluk ", "§6Journal ") + (n == 0 ? "?" : String.valueOf(n));
            String two = String.valueOf(n);
            while (two.length() < 2) two = "0" + two;
            book.setLore(JMARK + two,
                Core.tx("§7Oku: esyayi kullan (sag tik / dokun)", "§7Read: use the item (right-click / tap)"),
                Core.tx("§8Duskwalker Gunlugu", "§8Duskwalker Journal"));
        } catch (Throwable e) { /* yut */ }
        return book;
    }

    /** JS parseInt(s, 10): bastaki (isaretli) rakamlari okur; yoksa NaN yerine null. */
    private static Integer parseIntJs(String s) {
        String t = s.stripLeading();
        int i = 0;
        if (i < t.length() && (t.charAt(i) == '+' || t.charAt(i) == '-')) i++;
        int ds = i;
        while (i < t.length() && Character.isDigit(t.charAt(i))) i++;
        if (i == ds) return null;
        try { return Integer.parseInt(t.substring(0, i)); } catch (Throwable e) { return null; }
    }

    public static int journalNumOf(ItemStack item) {
        if (item == null) return 0;
        List<String> lore;
        try { lore = item.getLore(); } catch (Throwable e) { return 0; }
        if (lore == null || lore.isEmpty() || lore.get(0).indexOf(JMARK) != 0) return 0;
        Integer n = parseIntJs(lore.get(0).substring(JMARK.length()));
        return n == null ? 0 : n;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> foundList(Player player) {
        Object o = Core.playerGet(player, JOURNALS_KEY, null);
        List<Object> l = Json.asList(o);
        return l != null ? l : new ArrayList<>();
    }

    private static boolean hasNum(List<Object> l, int n) {
        for (Object o : l) if (o instanceof Number x && x.doubleValue() == n) return true;
        return false;
    }

    public static void markJournalFound(Player player, int num, boolean announce) {
        if (num < 1 || num > 10) return;
        List<Object> found = foundList(player);
        if (hasNum(found, num)) return;
        found.add((double) num);
        Core.playerSet(player, JOURNALS_KEY, found);
        Core.addStat(player, "journalsFound");
        if (announce) {
            try {
                player.sendMessage(Core.tx(
                    "§6[Gunluk]§r Gunluk " + num + " kutuphanene eklendi §8(" + found.size() + "/10)§r",
                    "§6[Journal]§r Journal " + num + " added to your library §8(" + found.size() + "/10)§r"));
            } catch (Throwable e) { /* yut */ }
        }
    }

    public static void openJournalPage(Player player, int num) {
        if (num < 1 || num > JOURNAL_PAGES.length) return;
        String[] pages = JOURNAL_PAGES[num - 1];
        Forms.Action form = new Forms.Action()
            .title(Core.tx("Gunluk ", "Journal ") + num)
            .body("§f" + String.join("\n\n", pages))
            .button(Core.tx("Kutuphane", "Library"));
        Core.showForm(player, form, res -> openJournalLibrary(player));
    }

    public static void openJournalLibrary(Player player) {
        final List<Object> found = foundList(player);
        Forms.Action form = new Forms.Action()
            .title(Core.tx("Gunluk Kutuphanesi", "Journal Library"))
            .body(Core.tx("§7Bulunan: §f", "§7Found: §f") + found.size() + " / 10\n§8"
                + Core.tx("Bir yapiya yaklasinca ya da gunlugu alinca buraya eklenir.", "Added when you approach a structure or pick up a journal."));
        for (int i = 1; i <= 10; i++) form.button(hasNum(found, i) ? "§6" + Core.tx("Gunluk ", "Journal ") + i : "§8???");
        Core.showForm(player, form, res -> {
            int n = res.selection + 1;
            if (hasNum(found, n)) openJournalPage(player, n);
        });
    }

    /** num null ise rastgele gunluk. */
    public static void doJournal(Player player, Integer num) {
        try {
            Dimension dimension = player.dimension();
            int idx = num != null ? num - 1 : randInt(0, JOURNAL_PAGES.length - 1);
            ItemStack book = createJournalBook(idx >= 0 && idx < JOURNAL_PAGES.length ? JOURNAL_PAGES[idx] : null, idx + 1);
            if (book == null) return;
            boolean given = false;
            try {
                Container inv = player.inventory();
                if (inv != null && inv.emptySlotsCount() > 0) { inv.addItem(book); given = true; }
            } catch (Throwable e) { /* yut */ }
            if (!given) {
                try {
                    Vec3 l = player.location();
                    dimension.spawnItem(book, new Vec3(Math.floor(l.x()) + 0.5, Math.floor(l.y()), Math.floor(l.z()) + 0.5));
                } catch (Throwable e) { /* yut */ }
            }
            markJournalFound(player, idx + 1, true);
            try { player.sendMessage(Core.tx("§7(eski bir gunluk buldun...)§r", "§7(you found an old journal...)§r")); } catch (Throwable e) { /* yut */ }
        } catch (Throwable e) { /* yut */ }
    }
    public static void doJournal(Player player) { doJournal(player, null); }

    private static final Map<String, Long> v15lastUse = new HashMap<>();

    public static void v15JournalUse(Player p, ItemStack item) {
        int n = journalNumOf(item);
        if (n == 0) return;
        long t = Sys.currentTick();
        if (t - v15lastUse.getOrDefault(p.id(), 0L) < 10) return;
        v15lastUse.put(p.id(), t);
        markJournalFound(p, n, true);
        openJournalPage(p, n);
    }

    // ================================================================== YAPI YERLESTIRME
    /** Uygun alan bulur (x, y = zemin ustu, z); bulamazsa null. */
    public static Vec3 findStructureSite(Dimension dimension, Vec3 playerPos) {
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = Math.random() * Math.PI * 2;
            double dist = 40 + Math.random() * 50;
            int x = (int) Math.floor(playerPos.x() + Math.cos(angle) * dist);
            int z = (int) Math.floor(playerPos.z() + Math.sin(angle) * dist);
            for (int y = (int) Math.floor(playerPos.y()) + 30; y > Math.floor(playerPos.y()) - 30 && y > -60; y--) {
                try {
                    Block block = dimension.getBlock(x, y, z);
                    Block above = dimension.getBlock(x, y + 1, z);
                    if (block == null || above == null) continue;
                    if (block.typeId().equals("minecraft:air") || block.typeId().contains("water") || block.typeId().contains("lava")) continue;
                    if (!above.typeId().equals("minecraft:air")) continue;
                    boolean clear = true;
                    for (int dy = 1; dy <= 14; dy++) {
                        Block b = dimension.getBlock(x, y + dy, z);
                        if (b == null || !b.typeId().equals("minecraft:air")) { clear = false; break; }
                    }
                    if (clear) return new Vec3(x, y + 1, z);
                } catch (Throwable e) { /* yut */ }
            }
        }
        return null;
    }

    // v14.1: YAPI DOGRULAMA
    public static boolean v15Loaded(Dimension dimension, Vec3 c, int r) {
        int[][] pts = { { 0, 0 }, { r, 0 }, { -r, 0 }, { 0, r }, { 0, -r }, { r, r }, { -r, -r }, { r, -r }, { -r, r } };
        for (int[] p : pts) {
            try { if (dimension.getBlock(new Vec3(c.x() + p[0], c.y(), c.z() + p[1])) == null) return false; } catch (Throwable e) { return false; }
        }
        return true;
    }

    public static int v15CountSolid(Dimension dimension, Vec3 c) {
        int n = 0;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = 0; dy <= 3; dy++) {
            try {
                Block b = dimension.getBlock(new Vec3(c.x() + dx, c.y() + dy, c.z() + dz));
                if (b != null && !b.typeId().equals("minecraft:air")) n++;
            } catch (Throwable e) { /* yut */ }
        }
        return n;
    }

    /**
     * fnName: Bedrock yolu ("duskwalker/structure_01"). JS'te komut sonucunun successCount'una da bakiliyordu;
     * Java'da komut istisna firlatmazsa basarili sayilir, asil dogrulama yapinin gercekten yerlesmesidir
     * (kati blok sayisi artmali).
     */
    public static boolean v15RunFunction(Dimension dimension, Vec3 site, String fnName) {
        if (!v15Loaded(dimension, site, 12)) return false;
        int before = v15CountSolid(dimension, site);
        boolean ok;
        try {
            dimension.runCommand("execute positioned " + (long) site.x() + " " + (long) site.y() + " " + (long) site.z() + " run function " + fnName);
            ok = true;
        } catch (Throwable e) { ok = false; }
        return ok && v15CountSolid(dimension, site) > before;
    }

    public static Vec3 v15FindChestSpot(Dimension dimension, Vec3 site) {
        Vec3 best = null;
        double bd = 1e9;
        for (int dx = -7; dx <= 7; dx++) for (int dz = -7; dz <= 7; dz++) {
            for (int dy = 3; dy >= -2; dy--) {
                try {
                    double x = site.x() + dx, y = site.y() + dy, z = site.z() + dz;
                    Block b = dimension.getBlock(new Vec3(x, y, z));
                    Block above = dimension.getBlock(new Vec3(x, y + 1, z));
                    Block below = dimension.getBlock(new Vec3(x, y - 1, z));
                    if (b == null || above == null || below == null || !b.typeId().equals("minecraft:air") || !above.typeId().equals("minecraft:air")) continue;
                    String bt = below.typeId();
                    if (bt.equals("minecraft:air") || bt.contains("water") || bt.contains("lava") || bt.contains("leaves") || bt.contains("ladder")) continue;
                    double d = dx * dx + dz * dz + Math.abs(dy);
                    if (d > 0 && d < bd) { bd = d; best = new Vec3(x, y, z); }
                } catch (Throwable e) { /* yut */ }
            }
        }
        return best;
    }

    public static boolean v15PlaceJournalChest(Dimension dimension, Vec3 site, ItemStack book) {
        Vec3 spot = v15FindChestSpot(dimension, site);
        if (spot == null) return false;
        try {
            Block b = dimension.getBlock(spot);
            b.setType("minecraft:chest");
            Block b2 = dimension.getBlock(spot);
            Container inv = b2 == null ? null : b2.container();
            if (inv == null) return false;
            inv.addItem(book);
            return inv.getItem(0) != null;
        } catch (Throwable e) { return false; }
    }

    public static void v15RegisterStructure(int num, Vec3 site, String dimId) {
        try {
            Object raw = World.getDynamicProperty(STRUCT_REG);
            List<Object> reg = raw instanceof String s ? Json.asList(Json.parse(s)) : new ArrayList<>();
            if (reg == null) reg = new ArrayList<>();
            Map<String, Object> rec = Json.newMap();
            rec.put("n", (double) num); rec.put("x", site.x()); rec.put("y", site.y()); rec.put("z", site.z()); rec.put("d", dimId);
            reg.add(rec);
            World.setDynamicProperty(STRUCT_REG, Json.stringify(reg));
        } catch (Throwable e) { /* yut */ }
    }

    public static boolean spawnNextStructure(Player player) {
        try {
            Object io = World.getDynamicProperty(STRUCTURE_INDEX);
            int idx = io instanceof Number n ? (int) n.doubleValue() : 0;
            if (idx >= 10) return false;
            final Dimension dimension = player.dimension();
            Runnable retrySoon = () -> { try { World.setDynamicProperty(NEXT_STRUCTURE, (double) (Sys.currentTick() + 2 * TICKS_PER_MIN)); } catch (Throwable e) { /* yut */ } };
            Vec3 site = findStructureSite(dimension, player.location());
            if (site == null) { retrySoon.run(); return false; }
            int num = idx + 1;
            String tag = String.valueOf(num);
            if (tag.length() < 2) tag = "0" + tag;
            // yapi GERCEKTEN yerlesmediyse: duyuru yok, sayac ilerlemez
            if (!v15RunFunction(dimension, site, "duskwalker/structure_" + tag)) { retrySoon.run(); return false; }

            ItemStack book = createJournalBook(JOURNAL_PAGES[num - 1], num);
            boolean placed = false;
            if (book != null) placed = v15PlaceJournalChest(dimension, site, book);
            if (!placed && book != null) { try { dimension.spawnItem(book, new Vec3(site.x() + 0.5, site.y() + 1, site.z() + 0.5)); } catch (Throwable e) { /* yut */ } }
            v15RegisterStructure(num, site, dimension.id());
            World.setDynamicProperty(STRUCTURE_INDEX, (double) num);
            try {
                player.sendMessage(Core.tx(
                    "§7Bir şey... yeni bir şey belirdi bu dünyada. (" + num + "/10)§r",
                    "§7Something... something new has appeared in this world. (" + num + "/10)§r"));
            } catch (Throwable e) { /* yut */ }

            // 10. yapi tamamlandiginda: bir sure sonra ozel Zindan da beliriyor (o da dogrulanir)
            if (num == 10) {
                Sys.runTimeout(() -> tryDungeon(player, dimension, 0), 200);
            }
            return true;
        } catch (Throwable e) { return false; }
    }

    private static void tryDungeon(Player player, Dimension dimension, int attempt) {
        try {
            if (attempt > 5) return;
            Vec3 dsite = findStructureSite(dimension, player.location());
            if (dsite == null || !v15RunFunction(dimension, dsite, "duskwalker/dungeon")) {
                Sys.runTimeout(() -> tryDungeon(player, dimension, attempt + 1), 3 * TICKS_PER_MIN);
                return;
            }
            try {
                World.sendMessage(Core.tx(
                    "§7Tüm günlükler toplandı... bir yerlerde bir kapı daha açıldı.§r",
                    "§7All journals collected... somewhere, another door has opened.§r"));
            } catch (Throwable e) { /* yut */ }
        } catch (Throwable e) { /* yut */ }
    }

    private static void structureTick() {
        if (isDefeated()) return;
        if (cfgIsFalse(getConfig(), "structures")) return;
        long tick = Sys.currentTick();
        Object io = World.getDynamicProperty(STRUCTURE_INDEX);
        double idx = io instanceof Number n ? n.doubleValue() : 0;
        if (idx >= 10) return;
        Object next = World.getDynamicProperty(NEXT_STRUCTURE);
        if (next == null) { World.setDynamicProperty(NEXT_STRUCTURE, (double) (tick + randInt(10 * TICKS_PER_MIN, 20 * TICKS_PER_MIN))); return; }
        if (next instanceof Number nn && tick < nn.doubleValue()) return;
        List<Player> players = World.getPlayers();
        if (players.isEmpty()) return;
        Player player = players.get(randInt(0, players.size() - 1));
        World.setDynamicProperty(NEXT_STRUCTURE, (double) (tick + randInt(15 * TICKS_PER_MIN, 30 * TICKS_PER_MIN)));
        spawnNextStructure(player);
    }

    // ================================================================== KARABASAN / UYKU BEKCISI
    public static final Map<String, Entity> sleepWatchers = new HashMap<>();

    public static boolean isPlayerSleeping(Player player) {
        try { return player.isSleeping(); } catch (Throwable e) { return false; }
    }

    public static void spawnSleepWatcher(Player player) {
        try {
            Dimension dimension = player.dimension();
            Vec3 spot = findSpawnSpot(dimension, player.location(), 0.8, 1.4);
            final Entity monster;
            try { monster = dimension.spawnEntity("duskwalker:duskwalker", spot); } catch (Throwable e) { return; }
            applyDominant(monster);
            applyVariant(monster);
            sleepWatchers.put(player.id(), monster);
            Sys.runTimeout(() -> keepWatch(player, monster), 20);
        } catch (Throwable e) { /* yut */ }
    }

    private static void keepWatch(Player player, Entity monster) {
        Entity cur = sleepWatchers.get(player.id());
        if (cur == null || !cur.id().equals(monster.id())) return;
        try {
            if (!monster.isValid() || !isPlayerSleeping(player)) return;
            Vec3 l = player.location();
            monster.teleport(new Vec3(l.x() + 0.6, l.y(), l.z()));
        } catch (Throwable e) { return; }
        Sys.runTimeout(() -> keepWatch(player, monster), 20);
    }

    // ================================================================== KACIS LABIRENTI (sabitler + uretec)
    public static final int MAZE_N = 11;
    public static final int MAZE_S = MAZE_N * 2 + 1;
    public static final String[] MAZE_WALLS = { "minecraft:deepslate_bricks", "minecraft:cracked_deepslate_bricks", "minecraft:polished_blackstone_bricks", "minecraft:blackstone" };
    public static final String[] MAZE_SIGNS = { "CIKIS BURADA", "SON DONEMEC", "GERI DON", "ARKANDA" };
    public static final String[] MAZE_WHISPERS = { "Sol... hayir, sag.", "Bu yolu hatirliyorum.", "Duvarlar seni taniyor.", "Arkana bak.", "Cok yaklastin.", "Yanlis yoldasin.", "Isigi gordun mu?" };
    public static final int[][] MAZE_DIRS = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

    public static <T> List<T> mazeShuffle(List<T> a) {
        for (int i = a.size() - 1; i > 0; i--) { int j = randInt(0, i); T t = a.get(i); a.set(i, a.get(j)); a.set(j, t); }
        return a;
    }
    public static String mazeWallBlock() { return MAZE_WALLS[randInt(0, MAZE_WALLS.length - 1)]; }

    /** JS { open, decoys, signs, lights }: open[S][S] bool izgara; digerleri [cx, cz] hucre listeleri. */
    public static final class Maze {
        public final boolean[][] open;
        public final List<int[]> decoys, signs, lights;
        Maze(boolean[][] open, List<int[]> decoys, List<int[]> signs, List<int[]> lights) {
            this.open = open; this.decoys = decoys; this.signs = signs; this.lights = lights;
        }
    }

    public static Maze generateMaze() {
        final int N = MAZE_N, S = MAZE_S;
        final boolean[][] open = new boolean[S][S];
        boolean[][] vis = new boolean[N][N];
        ArrayList<int[]> stack = new ArrayList<>();
        stack.add(new int[] { 0, 0 });
        vis[0][0] = true; open[1][1] = true;
        while (!stack.isEmpty()) {
            int[] top = stack.get(stack.size() - 1);
            int cx = top[0], cz = top[1];
            boolean moved = false;
            for (int[] d : mazeShuffle(new ArrayList<>(Arrays.asList(MAZE_DIRS)))) {
                int nx = cx + d[0], nz = cz + d[1];
                if (nx < 0 || nz < 0 || nx >= N || nz >= N || vis[nx][nz]) continue;
                vis[nx][nz] = true;
                open[2 * cx + 1 + d[0]][2 * cz + 1 + d[1]] = true;
                open[2 * nx + 1][2 * nz + 1] = true;
                stack.add(new int[] { nx, nz }); moved = true; break;
            }
            if (!moved) stack.remove(stack.size() - 1);
        }
        // donguler: fazladan duvar delikleri (yol secimini zorlastirir)
        for (int k = 0; k < N * 2; k++) {
            int cx = randInt(0, N - 1), cz = randInt(0, N - 1);
            int[] d = MAZE_DIRS[randInt(0, 3)];
            int nx = cx + d[0], nz = cz + d[1];
            if (nx < 0 || nz < 0 || nx >= N || nz >= N) continue;
            open[2 * cx + 1 + d[0]][2 * cz + 1 + d[1]] = true;
        }
        List<int[]> dead = new ArrayList<>();
        for (int cx = 0; cx < N; cx++) for (int cz = 0; cz < N; cz++) {
            if ((cx == 0 && cz == 0) || (cx == N - 1 && cz == N - 1)) continue;
            int c = 0;
            for (int[] d : MAZE_DIRS) if (open[2 * cx + 1 + d[0]][2 * cz + 1 + d[1]]) c++;
            if (c == 1) dead.add(new int[] { cx, cz });
        }
        mazeShuffle(dead);
        List<int[]> decoys = new ArrayList<>();
        for (int[] c : dead) { if (c[0] + c[1] >= 8) decoys.add(c); }
        if (decoys.size() > 3) decoys = new ArrayList<>(decoys.subList(0, 3));
        List<int[]> signs = new ArrayList<>();
        for (int[] c : dead) { if (!decoys.contains(c)) signs.add(c); }
        if (signs.size() > 4) signs = new ArrayList<>(signs.subList(0, 4));
        List<int[]> lights = new ArrayList<>();
        for (int k = 0; k < 30 && lights.size() < 6; k++) {
            int cx = randInt(0, N - 1), cz = randInt(0, N - 1);
            if ((cx == 0 && cz == 0) || (cx == N - 1 && cz == N - 1)) continue;
            final int fx = cx, fz = cz;
            if (decoys.stream().anyMatch(c -> c[0] == fx && c[1] == fz) || signs.stream().anyMatch(c -> c[0] == fx && c[1] == fz)) continue;
            lights.add(new int[] { cx, cz });
        }
        return new Maze(open, decoys, signs, lights);
    }

    // ================================================================== KAYIT (JS ust duzey yan etkileri, kaynak sirasiyla)
    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        initialized = true;

        // yaratik state machine tarayicisi (satir 169)
        Sys.runInterval(creatureScanner, 2);

        // yeni faza girilince tek seferlik gecis (satir 239)
        Sys.runInterval(() -> {
            try {
                int day = getDay();
                int idx = getCurrentPhaseIndex(day);
                Object last = World.getDynamicProperty(LAST_PHASE_KEY);
                if (!(last instanceof Number)) { World.setDynamicProperty(LAST_PHASE_KEY, (double) idx); return; }
                if (idx <= ((Number) last).doubleValue()) return;
                World.setDynamicProperty(LAST_PHASE_KEY, (double) idx);
                Phase phase = PHASES[idx];
                for (Player player : World.getPlayers()) {
                    try { player.setTitle(phase.title, Opts.Title.of(10, 60, 20, phase.subtitle)); } catch (Throwable e) { /* yut */ }
                    try { player.playSound("mob.wither.spawn", Opts.Sound.of(0.6, 0.4)); } catch (Throwable e) { /* yut */ }
                }
                try { World.sendMessage(phase.message); } catch (Throwable e) { /* yut */ }
            } catch (Throwable e) { /* yut */ }
        }, 200);

        // dolunay uyarisi (satir 510)
        Sys.runInterval(() -> {
            if (isDefeated()) return;
            try {
                if (cfgIsFalse(getConfig(), "moon")) return;
                if (!isFullMoonNight()) return;
                int t = World.getTimeOfDay();
                if (t < 12800 || t > 13400) return; // sadece gece basinda, dar bir pencerede
                String key = MOON_WARNED_KEY + getDay();
                if (truthy(World.getDynamicProperty(key))) return;
                World.setDynamicProperty(key, true);
                World.sendMessage("§7Bu gece ay tam... §4Duskwalker§7 daha guclu.§r");
            } catch (Throwable e) { /* yut */ }
        }, 100);

        // saglamlik toparlanma / bulanti / sahte gorunme (satir 574)
        Sys.runInterval(MainA::sanityTick, 100);

        // saglamlik 0 sonucu (satir 642)
        Sys.runInterval(MainA::zeroSanityTick, 100);

        // gunluk kullanimi + !dwjournals (satir 848)
        try {
            Events.After.itemUse.subscribe(ev -> { try { v15JournalUse(ev.source, ev.itemStack); } catch (Throwable e) { /* yut */ } });
            Events.After.playerInteractWithBlock.subscribe(ev -> { try { v15JournalUse(ev.player, ev.itemStack); } catch (Throwable e) { /* yut */ } });
            Events.Before.chatSend.subscribe(ev -> {
                if (!"!dwjournals".equals(ev.message)) return;
                ev.cancel = true;
                Player p = ev.sender;
                Sys.run(() -> { try { openJournalLibrary(p); } catch (Throwable e) { /* yut */ } });
            });
        } catch (Throwable e) { /* yut */ }

        // envanterdeki gunlukleri kutuphaneye ekle (satir 860)
        Sys.runInterval(() -> {
            for (Player p : World.getPlayers()) {
                try {
                    Container inv = p.inventory();
                    if (inv == null) continue;
                    for (int i = 0; i < inv.size(); i++) {
                        int n = journalNumOf(inv.getItem(i));
                        if (n != 0) markJournalFound(p, n, true);
                    }
                } catch (Throwable e) { /* yut */ }
            }
        }, 200);

        // yapiya yaklasinca gunluk otomatik kutuphaneye girer (satir 874)
        Sys.runInterval(() -> {
            try {
                Object raw = World.getDynamicProperty(STRUCT_REG);
                List<Object> reg = raw instanceof String s ? Json.asList(Json.parse(s)) : new ArrayList<>();
                if (reg == null || reg.isEmpty()) return;
                for (Player p : World.getPlayers()) {
                    for (Object so : reg) {
                        Map<String, Object> s = Json.asMap(so);
                        if (s == null) continue;
                        if (!p.dimension().id().equals(s.get("d"))) continue;
                        Vec3 l = p.location();
                        if (Math.hypot(Json.num(s.get("x"), 0) - l.x(), Json.num(s.get("z"), 0) - l.z()) < 14 && Math.abs(Json.num(s.get("y"), 0) - l.y()) < 16) {
                            markJournalFound(p, (int) Json.num(s.get("n"), 0), true);
                        }
                    }
                }
            } catch (Throwable e) { /* yut */ }
        }, 100);

        // yapi zamanlayicisi (satir 1027)
        Sys.runInterval(MainA::structureTick, 200);
    }
}

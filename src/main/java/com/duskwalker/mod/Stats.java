package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.*;
import java.util.function.Predicate;

import static com.duskwalker.mod.Core.*;

/**
 * DUSKWALKER v10 - ISTATISTIK, BASARIMLAR, TABELA GUNLUGU (stats.js)
 * - Oyuncu basina istatistik toplar (olum, gece, yuruyus, blok...)
 * - 14+ basarim; acilinca ekranda kart + ses
 * - !dwstats / !dwach formlari (komutlar Commands'ta)
 * - Tabela Gunlugu olayi: yakininda, sadece SANA ait bilgilerin yazili oldugu bir tabela belirir
 */
public final class Stats {
    private Stats() {}

    private static boolean initialized = false;

    /** JS sayi -> metin (tam sayi ise ondaliksiz). */
    private static String num(double d) { return Json.stringify(d); }
    private static double g(Map<String, Object> s, String k) { return Json.num(s.get(k), 0); }

    // yuruyus + oynama suresi icin son konum
    private static final class LastPos { double x, z; String d; }
    private static final Map<String, LastPos> lastPos = new HashMap<>();

    public static void init() {
        if (initialized) return;
        initialized = true;

        // ---------------------------------------------------------
        //  Istatistik toplama
        // ---------------------------------------------------------
        Events.After.playerPlaceBlock.subscribe(ev -> {
            try {
                addStat(ev.player, "blocksPlaced");
                if (ev.block != null && ev.block.typeId().contains("torch")) addStat(ev.player, "torchesPlaced");
            } catch (Throwable e) { /* yut */ }
        });

        Events.After.playerBreakBlock.subscribe(ev -> {
            try { addStat(ev.player, "blocksBroken"); } catch (Throwable e) { /* yut */ }
        });

        Events.After.entityDie.subscribe(ev -> {
            try {
                Entity dead = ev.deadEntity;
                if (dead == null) return;
                if (dead.typeId().equals("minecraft:player")) {
                    Player dp = (Player) dead;
                    addStat(dp, "deaths");
                    Entity killer = ev.damageSource != null ? ev.damageSource.damagingEntity : null;
                    if (killer != null && killer.typeId().equals("duskwalker:duskwalker")) addStat(dp, "deathsByDusk");
                    return;
                }
                // Duskwalker lavda oldu: yakindaki oyunculara "Lav Avcisi" sayaci
                if (dead.typeId().equals("duskwalker:duskwalker") && ev.damageSource != null && "lava".equals(ev.damageSource.cause)) {
                    for (Player p : World.getPlayers()) {
                        if (p.dimension().id().equals(dead.dimension().id()) && hdist(p.location(), dead.location()) < 48) addStat(p, "duskLavaKills");
                    }
                }
            } catch (Throwable e) { /* yut */ }
        });

        Events.Before.chatSend.subscribe(ev -> {
            try {
                String msg = ev.message;
                if (msg == null || (!msg.isEmpty() && msg.charAt(0) == '!')) return;
                Player sender = ev.sender;
                String[] words = msg.trim().split("\\s+");
                String lastW = words.length > 0 ? words[words.length - 1] : "";
                final String last = lastW == null ? "" : lastW;
                Sys.run(() -> safe(() -> {
                    addStat(sender, "chatMessages");
                    if (last.length() >= 2) setStat(sender, "lastWord", last.substring(0, Math.min(14, last.length())));
                }));
            } catch (Throwable e) { /* yut */ }
        });

        // yuruyus + oynama suresi (2 sn'de bir)
        Sys.runInterval(() -> {
            for (Player p : World.getPlayers()) {
                try {
                    Vec3 l = p.location();
                    LastPos prev = lastPos.get(p.id());
                    LastPos np = new LastPos(); np.x = l.x(); np.z = l.z(); np.d = p.dimension().id();
                    lastPos.put(p.id(), np);
                    addStat(p, "playSeconds", 2);
                    if (prev != null && prev.d.equals(p.dimension().id())) {
                        double d = Math.hypot(l.x() - prev.x, l.z() - prev.z);
                        if (d > 0.2 && d < 12) addStat(p, "distance", d);
                    }
                } catch (Throwable e) { /* yut */ }
            }
        }, 40);

        Events.After.playerLeave.subscribe(ev -> lastPos.remove(ev.playerId));

        // basarim denetimi
        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("achievements")) return;
            for (Player p : World.getPlayers()) {
                try {
                    Set<String> set = getUnlocked(p);
                    Map<String, Object> stats = getStats(p);
                    for (Achievement a : ACHIEVEMENTS) {
                        if (set.contains(a.id) || !a.check.test(stats)) continue;
                        set.add(a.id);
                        saveUnlocked(p);
                        title(p, "§6Basarim", "§e" + a.name, 60);
                        playTo(p, "random.levelup", 0.8, 1.0);
                        say(p, "§6[Basarim]§r §e" + a.name + "§r - " + a.desc);
                    }
                } catch (Throwable e) { /* yut */ }
            }
        }, 200);

        Events.After.playerLeave.subscribe(ev -> unlockedCache.remove(ev.playerId));

        // ---------------------------------------------------------
        //  TABELA GUNLUGU (olay)
        // ---------------------------------------------------------
        registerEvent(EventDef.of("sign_diary")
            .name("Tabela Gunlugu")
            .minDay(6)
            .weight(2)
            .cooldown(20L * TICKS_PER_MIN)
            .run(player -> {
                Dimension dimension = player.dimension();
                Vec3 spot = standSpot(dimension, frontPlayer(player, 6), 0, 3, 14);
                if (spot == null) return false;
                Block block = safe(() -> dimension.getBlock(new Vec3(Math.floor(spot.x()), Math.floor(spot.y()), Math.floor(spot.z()))), (Block) null);
                if (block == null) return false;
                try {
                    block.setType("minecraft:standing_sign");
                    block.setSignText(buildDiaryText(player));
                } catch (Throwable e) { return false; }

                Vec3 loc = new Vec3(block.x(), block.y(), block.z());
                // 5 dakika sonra (hala tabelaysa) kaybolur
                Sys.runTimeout(() -> safe(() -> {
                    Block b = dimension.getBlock(loc);
                    if (b != null && b.typeId().equals("minecraft:standing_sign")) b.setType("minecraft:air");
                }), 5 * TICKS_PER_MIN);

                Sys.runTimeout(() -> {
                    if (isValid(player)) actionbar(player, "§8(yakinlarda bir tabela var...)§r");
                }, 40);
                loseSanity(player, 4);
                addEvidence(player, 1);
                return true;
            }));
    }

    // ---------------------------------------------------------
    //  Basarimlar
    // ---------------------------------------------------------
    private static int distinctEvents(Map<String, Object> s) {
        int n = 0;
        for (Map.Entry<String, Object> e : s.entrySet()) if (e.getKey().indexOf("ev_") == 0 && Json.num(e.getValue(), 0) > 0) n++;
        return n;
    }

    public static final class Achievement {
        public final String id, name, desc;
        public final Predicate<Map<String, Object>> check;
        Achievement(String id, String name, String desc, Predicate<Map<String, Object>> check) { this.id = id; this.name = name; this.desc = desc; this.check = check; }
    }

    private static Achievement ach(String id, String name, String desc, Predicate<Map<String, Object>> check) { return new Achievement(id, name, desc, check); }

    public static final List<Achievement> ACHIEVEMENTS = new ArrayList<>(List.of(
        ach("first_night", "Ilk Gece", "Bir geceyi atlat", s -> g(s, "nightsSurvived") >= 1),
        ach("three_nights", "Uc Gece", "3 geceyi atlat", s -> g(s, "nightsSurvived") >= 3),
        ach("week", "Bir Hafta", "7 geceyi atlat", s -> g(s, "nightsSurvived") >= 7),
        ach("fifty", "Elli Gun", "Dunyanin 50. gunune ulas", s -> g(s, "longestDay") >= 50),
        ach("walker", "Yuruyen", "5000 blok yuru", s -> g(s, "distance") >= 5000),
        ach("builder", "Insaatci", "500 blok koy", s -> g(s, "blocksPlaced") >= 500),
        ach("miner", "Madenci", "1000 blok kir", s -> g(s, "blocksBroken") >= 1000),
        ach("fear", "Siniri Gormek", "Saglamligini 10'un altina dusur", s -> s.get("minSanity") instanceof Number n && n.doubleValue() <= 10),
        ach("first_death", "Ilk Olum", "Bir kez ol", s -> g(s, "deaths") >= 1),
        ach("dusk_victim", "Onun Kurbani", "Duskwalker tarafindan ol", s -> g(s, "deathsByDusk") >= 1),
        ach("light_keeper", "Isik Bekcisi", "Isik siginaginda toplam 10 dakika gecir", s -> g(s, "refugeSeconds") >= 600),
        ach("collector", "Kabus Koleksiyoncusu", "4 farkli yeni korku olayi yasa", s -> distinctEvents(s) >= 4),
        ach("lava_hunter", "Lav Avcisi", "Duskwalker'in lavda olmesine tanik ol", s -> g(s, "duskLavaKills") >= 1),
        ach("night_owl", "Gece Kusu", "Toplam 1 saat oyna", s -> g(s, "playSeconds") >= 3600),
        ach("torch_master", "Mesale Ustasi", "50 mesale yerlestir", s -> g(s, "torchesPlaced") >= 50),
        ach("stone_still", "Tas Gibi", "Durursan Gelir olayini 3 kez yasa", s -> g(s, "ev_stand_still") >= 3),
        ach("moon_watcher", "Ay Gozlemcisi", "8 ay evresinin hepsini gor", s -> {
            for (String k : new String[] { "full", "twin", "third", "none", "eclipse", "crescent", "first", "blood" }) {
                if (!(g(s, "moon_" + k) > 0)) return false;
            }
            return true;
        }),
        ach("blood_night", "Kan Ayi Gecesi", "Bir Kan Ayi gecesine tanik ol", s -> g(s, "moon_blood") >= 1),
        ach("night_watch", "Gece Nobetcisi", "5 nobet gorevini tamamla", s -> g(s, "questsDone") >= 5),
        ach("unblinking", "Gozunu Kirpmayan", "Don't Blink'ten 3 kez kurtul", s -> g(s, "blinkSurvived") >= 3),
        ach("high_roller", "Kumarbaz", "Mum Kumari'ni 5 kez oyna", s -> (g(s, "gambleBless") + g(s, "gambleCurse") + g(s, "gambleOmen") + g(s, "gambleGift")) >= 5),
        ach("dreamer", "Ruyaci", "5 ruya gor", s -> g(s, "dreams") >= 5),
        ach("explorer", "Kasif", "3 harabe bul", s -> g(s, "ruinsFound") >= 3),
        ach("hollow_visitor", "Bos Ev Ziyaretcisi", "Bos Ev'e gir", s -> g(s, "houseVisits") >= 1),
        ach("scholar", "Bilgin", "Kodeksi tamamla", s -> g(s, "codexDone") >= 1),
        ach("together", "Birlikte", "Toplam 10 dakika baska bir oyuncuyla yakin kal", s -> g(s, "buddySeconds") >= 600),
        ach("resolute", "Azimli", "5 yetenek satin al", s -> g(s, "perksBought") >= 5),
        ach("paradox_solver", "Paradoks Cozucu", "Paradoks Koridoru'ndan kapi yaparak kurtul", s -> g(s, "paradoxSolved") >= 1),
        ach("hollow_witness", "Bos Gece Tanigi", "Bir Bos Gece'ye tanik ol", s -> g(s, "hollowNights") >= 1),
        ach("oath_keeper", "Yemin Sahibi", "3 yemini sabaha kadar tut", s -> g(s, "oathsKept") >= 3),
        ach("moon_gazer", "Ay Seyircisi", "Aya 5 kez uzun uzun bak", s -> g(s, "moonGazes") >= 5),
        ach("lull_breaker", "Sakinligi Bozan", "3 Yalan Sakinligin sonunu gor", s -> g(s, "lullBroken") >= 3),
        ach("letter_collector", "Mektup Toplayici", "12 mektubun hepsini bul", s -> g(s, "lettersFound") >= 12),
        ach("broken_mind", "Cokmus Zihin", "Bir Cokus Noktasi yasa", s -> g(s, "breakingPoints") >= 1),
        ach("grounded", "Toprakli", "Topraklanma ile 60 sn sakinles", s -> g(s, "groundingSeconds") >= 60),
        ach("eclipse_keeper", "Tutulma Bekcisi", "Bir Tutulma Nobetini tamamla", s -> g(s, "eclipseVigils") >= 1),
        ach("unreadable", "Okunamayan", "Duskwalker'in seni cozemedigi 3 gece gecir", s -> g(s, "unpredictableNights") >= 3),
        ach("keeper_of_light", "Isik Muhafizi", "3 kusatmayi savus", s -> g(s, "siegesRepelled") >= 3),
        ach("past_witness", "Dunku Sen", "Kendi gecmis rotani 3 kez gor", s -> g(s, "metPastSelf") + g(s, "pastGifts") >= 3),
        ach("depth_walker", "Derinlik Yuruyucusu", "Bos Derinlikleri bitir", s -> g(s, "depthsCleared") >= 1),
        ach("bargainer", "Pazarlikci", "5 anlasma yap", s -> g(s, "pactsAccepted") >= 5),
        ach("clean_books", "Temiz Defter", "Bir borcu zamaninda ode", s -> g(s, "debtsPaid") >= 1),
        ach("deja_survivor", "Tekrar Eden", "Deja Vu yasa ve 3 geri sayimi atlat", s -> g(s, "dejaVu") >= 1 && g(s, "countdownSaved") >= 3)
    ));

    private static final Map<String, Set<String>> unlockedCache = new HashMap<>();

    private static Set<String> getUnlocked(Player player) {
        Set<String> set = unlockedCache.get(player.id());
        if (set == null) {
            set = new LinkedHashSet<>();
            Object raw = safe(() -> player.getDynamicProperty(KEY.ACHIEVEMENTS), (Object) null);
            if (raw instanceof String str) {
                final Set<String> fset = set;
                safe(() -> {
                    List<Object> l = Json.asList(Json.parse(str));
                    if (l != null) for (Object o : l) if (o instanceof String id) fset.add(id);
                });
            }
            unlockedCache.put(player.id(), set);
        }
        return set;
    }

    private static void saveUnlocked(Player player) {
        safe(() -> player.setDynamicProperty(KEY.ACHIEVEMENTS, Json.stringify(new ArrayList<>(getUnlocked(player)))));
    }

    public static List<String> getUnlockedIds(Player player) { return new ArrayList<>(getUnlocked(player)); }

    // ---------------------------------------------------------
    //  Formlar
    // ---------------------------------------------------------
    private static String fmtTime(double seconds) {
        long m = (long) Math.floor(seconds / 60);
        return m >= 60 ? (m / 60) + " sa " + (m % 60) + " dk" : m + " dk";
    }

    public static void openStatsForm(Player player) {
        Map<String, Object> s = getStats(player);
        String[] lines = {
            "§7Oynama suresi: §f" + fmtTime(g(s, "playSeconds")),
            "§7Atlatilan gece: §f" + num(g(s, "nightsSurvived")),
            "§7Olum: §f" + num(g(s, "deaths")) + " §7(Duskwalker: §f" + num(g(s, "deathsByDusk")) + "§7)",
            "§7Yurunen mesafe: §f" + Math.round(g(s, "distance")) + " blok",
            "§7Koyulan / kirilan blok: §f" + num(g(s, "blocksPlaced")) + " / " + num(g(s, "blocksBroken")),
            "§7En dusuk saglamlik: §f" + (s.get("minSanity") instanceof Number mn ? num(mn.doubleValue()) : "-"),
            "§7Isik siginaginda: §f" + fmtTime(g(s, "refugeSeconds")),
            "§7Yasanan yeni korku olayi: §f" + num(g(s, "v10events")) + " §7(" + distinctEvents(s) + " cesit)",
            "§7Basarim: §f" + getUnlocked(player).size() + " / " + ACHIEVEMENTS.size()
        };
        Forms.Action form = new Forms.Action()
            .title("Duskwalker - Istatistikler")
            .body(String.join("\n", lines))
            .button("Basarimlar")
            .button("Kapat");
        showForm(player, form, res -> {
            if (res.selection == 0) openAchievementsForm(player);
        });
    }

    public static void openAchievementsForm(Player player) {
        Set<String> set = getUnlocked(player);
        List<String> lines = new ArrayList<>();
        for (Achievement a : ACHIEVEMENTS) lines.add((set.contains(a.id) ? "§a[x] " : "§8[ ] ") + "§f" + a.name + " §7- " + a.desc);
        Forms.Action form = new Forms.Action()
            .title("Duskwalker - Basarimlar")
            .body(String.join("\n", lines))
            .button("Geri")
            .button("Kapat");
        showForm(player, form, res -> {
            if (res.selection == 0) openStatsForm(player);
        });
    }

    // ---------------------------------------------------------
    //  TABELA GUNLUGU (olay) metni
    // ---------------------------------------------------------
    private static String buildDiaryText(Player player) {
        Map<String, Object> s = getStats(player);
        List<String> options = new ArrayList<>();
        if (g(s, "deaths") > 0) options.add(num(g(s, "deaths")) + " KEZ OLDUN\n" + num(g(s, "nightsSurvived") + 1) + ". GECE");
        if (s.get("lastWord") instanceof String lw) options.add("SON SOZUN:\n" + lw.toUpperCase(Locale.ROOT));
        if (g(s, "distance") > 200) options.add(Math.round(g(s, "distance")) + " BLOK\nYURUDUN");
        if (g(s, "torchesPlaced") > 5) options.add(num(g(s, "torchesPlaced")) + " MESALE\nYAKTIN");
        options.add("HEPSINI\nGORUYORUM");
        return pick(options);
    }
}

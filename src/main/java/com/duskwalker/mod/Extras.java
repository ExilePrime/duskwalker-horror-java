package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.*;

import static com.duskwalker.mod.Core.*;

/**
 * DUSKWALKER v12 - EKSTRALAR (extras.js)
 *  1) lost_letters  : 12 kayip mektup; !dwletters
 *  2) sanity_graph  : !dwgraph son ~24 dakikanin akil sagligi grafigi
 *  3) ranks         : basarim sayisina gore unvan; !dwrank
 *  4) night_report  : her sabah bir "gece raporu"
 *  5) lying_compass : !dwcompass evine yon gosterir... akli dusukse yalan soyler
 *  6) night_verse   : her sabah, o geceden uretilen 3 satirlik bir dize
 */
public final class Extras {
    private Extras() {}

    private static boolean initialized = false;

    private static String num(double d) { return Json.stringify(d); }

    // ---------------------------------------------------------
    //  1) Kayip mektuplar
    // ---------------------------------------------------------
    private static final class Letter {
        final String tr, en; final String[] loreTr, loreEn;
        Letter(String tr, String en, String[] loreTr, String[] loreEn) { this.tr = tr; this.en = en; this.loreTr = loreTr; this.loreEn = loreEn; }
        String name() { return tx(tr, en); }
        String[] lore() { return LANG.equals("tr") ? loreTr : loreEn; }
    }

    private static final Letter[] LETTERS = {
        new Letter("Mektup I", "Letter I", new String[] { "§7Sevgili Aylin, bu gece yine pencereye", "§7baktim. Kardesim orada degildi... ama hep oradaydi." }, new String[] { "§7Dear Aylin, tonight I looked out the", "§7window again. Brother wasn't there... yet he always was." }),
        new Letter("Mektup II", "Letter II", new String[] { "§7Koyun bir kismi gitti.", "§7Ardindan kapilari acik biraktilar." }, new String[] { "§7Part of the village has left.", "§7They left the doors open behind them." }),
        new Letter("Mektup III", "Letter III", new String[] { "§7Mesaleleri saydim. Uc tane yeter.", "§7Dorduncuyu kimse yakmak istemiyor." }, new String[] { "§7I counted the torches. Three are enough.", "§7No one wants to light the fourth." }),
        new Letter("Mektup IV", "Letter IV", new String[] { "§7Gece yarisi biri kapimi caldi.", "§7Acmadim. Sabah kapinin disinda adim vardi." }, new String[] { "§7At midnight someone knocked.", "§7I didn't open. By morning my name was outside." }),
        new Letter("Mektup V", "Letter V", new String[] { "§7Aynada yansimam bir saniye gec kaldi.", "§7Sonra bir daha. Sonra hic gelmedi." }, new String[] { "§7My reflection lagged a second.", "§7Then again. Then it never came." }),
        new Letter("Mektup VI", "Letter VI", new String[] { "§7Kuyu dibinden birinin ismimi", "§7soyledigini duydum. Cevap vermedim." }, new String[] { "§7I heard someone say my name", "§7from the bottom of the well. I didn't answer." }),
        new Letter("Mektup VII", "Letter VII", new String[] { "§7Sessiz kalmayi ogrendik.", "§7Kosmayi ogrenemedik." }, new String[] { "§7We learned to be quiet.", "§7We never learned to run." }),
        new Letter("Mektup VIII", "Letter VIII", new String[] { "§7Kapiyi yapan kisi kurtulur derler.", "§7Ama once arkasini donmesi gerekir." }, new String[] { "§7They say whoever makes the door escapes.", "§7But first they must turn their back." }),
        new Letter("Mektup IX", "Letter IX", new String[] { "§7Ay iki oldugu gece cocuklar", "§7uyumadi. Gokyuzune baktilar." }, new String[] { "§7The night the moon doubled the", "§7children didn't sleep. They watched the sky." }),
        new Letter("Mektup X", "Letter X", new String[] { "§7Biri bana yardim edebilirdi.", "§7Ama o gece kimse bakmadi." }, new String[] { "§7Someone could have helped me.", "§7But that night no one looked." }),
        new Letter("Mektup XI", "Letter XI", new String[] { "§7Lav hepsini yutar derler.", "§7Ben sadece bir kez gordum." }, new String[] { "§7They say lava swallows everything.", "§7I saw it only once." }),
        new Letter("Mektup XII", "Letter XII", new String[] { "§7Bunu bulan: yalniz gitme.", "§7Aylin'i bul. O hala bekliyor." }, new String[] { "§7Whoever finds this: don't go alone.", "§7Find Aylin. She is still waiting." })
    };
    private static final String LMARK = "§8​L";
    private static final String LKEY = "duskwalker:v12letters";

    public static ItemStack makeLetter(int i) {
        ItemStack it = new ItemStack("minecraft:paper", 1);
        it.nameTag = "§6" + LETTERS[i].name();
        List<String> lore = new ArrayList<>();
        lore.add(LMARK + String.format("%02d", i));
        lore.addAll(Arrays.asList(LETTERS[i].lore()));
        it.setLore(lore);
        return it;
    }
    public static ItemStack randomLetter() { return makeLetter(randInt(0, LETTERS.length - 1)); }

    private static boolean containsNum(List<Object> l, int v) {
        for (Object o : l) if (o instanceof Number n && n.doubleValue() == v) return true;
        return false;
    }

    private static List<Object> foundList(Player p) {
        List<Object> l = Json.asList(playerGet(p, LKEY, null));
        return l != null ? l : new ArrayList<>();
    }

    /** JS parseInt(s, 10): bastaki rakamlari okur, yoksa -1 (NaN). */
    private static int parseIntJs(String s) {
        int i = 0; boolean neg = false;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        if (i < s.length() && (s.charAt(i) == '-' || s.charAt(i) == '+')) { neg = s.charAt(i) == '-'; i++; }
        int st = i; long v = 0;
        while (i < s.length() && Character.isDigit(s.charAt(i))) { v = v * 10 + (s.charAt(i) - '0'); i++; }
        if (i == st) return Integer.MIN_VALUE;
        return (int) (neg ? -v : v);
    }

    public static void openLetters(Player player) {
        List<Object> found = foundList(player);
        Forms.Action form = new Forms.Action().title(tx("Kayip Mektuplar", "Lost Letters")).body(tx("§7Bulunan: §f", "§7Found: §f") + found.size() + " / 12");
        for (int i = 0; i < LETTERS.length; i++) form.button(containsNum(found, i) ? "§f" + LETTERS[i].name() : "§8???");
        showForm(player, form, res -> {
            int i = res.selection;
            if (i < 0 || i >= LETTERS.length || !containsNum(found, i)) return;
            Forms.Action f = new Forms.Action().title(LETTERS[i].name()).body(String.join("\n", LETTERS[i].lore())).button(tx("Geri", "Back"));
            showForm(player, f, r2 -> openLetters(player));
        });
    }

    // ---------------------------------------------------------
    //  2) Akil sagligi grafigi
    // ---------------------------------------------------------
    private static final Map<String, List<Integer>> samples = new HashMap<>();

    private static String graphText(Player player) {
        List<Integer> a = samples.getOrDefault(player.id(), new ArrayList<>());
        if (a.size() < 3) return tx("§7Henuz yeterli veri yok.§r", "§7Not enough data yet.§r");
        String blocks = "▁▂▃▄▅▆▇█";
        StringBuilder line = new StringBuilder();
        int mn = Integer.MAX_VALUE, mx = Integer.MIN_VALUE;
        for (int v : a) {
            line.append(v < 30 ? "§c" : v < 60 ? "§e" : "§a").append(blocks.charAt((int) Math.min(7, Math.floor(v / 12.5))));
            mn = Math.min(mn, v); mx = Math.max(mx, v);
        }
        return tx("§6Akil sagligi (son ~", "§6Sanity (last ~") + Math.round(a.size() / 2.0) + tx(" dk):§r\n", " min):§r\n") + line + "\n§7min §f" + mn + " §7max §f" + mx + " §7now §f" + a.get(a.size() - 1);
    }

    // ---------------------------------------------------------
    //  3) Unvanlar
    // ---------------------------------------------------------
    private static final int[] RANK_AT = { 0, 3, 7, 13, 21 };
    private static final String[][] RANK_NAMES = { { "Uykucu", "Sleeper" }, { "Nobetci", "Watcher" }, { "Yuruyen", "Walker" }, { "Bekci", "Keeper" }, { "Bos", "Hollow" } };

    private static final class Rank { String name; int n; int next = -1; }

    private static Rank rankOf(Player player) {
        int n = Stats.getUnlockedIds(player).size();
        int r = 0;
        for (int i = 0; i < RANK_AT.length; i++) if (n >= RANK_AT[i]) r = i;
        Rank out = new Rank();
        out.name = tx(RANK_NAMES[r]);
        out.n = n;
        for (int x : RANK_AT) if (x > n) { out.next = x; break; }
        return out;
    }

    // ---------------------------------------------------------
    //  4 + 6) Gece raporu ve dize
    // ---------------------------------------------------------
    private static final class Snap { double ev, dist, spikes, n; }
    private static final Map<String, Snap> snap = new HashMap<>();
    private static final Map<String, Double> minSan = new HashMap<>();
    private static int lastT = -1;

    private static final String[][] VERSE = {
        { "Gece {n}. Hala buradayim.", "Night {n}. Still here." },
        { "{d} blok yurudum, golgem hep geriden geldi.", "I walked {d} blocks; my shadow always came behind." },
        { "Akil sagligim {m}'e kadar indi, ama sabah oldu.", "My mind sank to {m}, yet morning came." },
        { "Isik beni bir kez daha korudu.", "The light protected me once more." },
        { "Sessizce bekledim. O da bekledi.", "I waited in silence. So did it." },
        { "Bu gece {e} sey yasadim, hicbiri gercek degildi.", "Tonight I lived {e} things, none of them real." },
        { "Adimi duydum ama cevap vermedim.", "I heard my name and did not answer." },
        { "Ay bana bakti, ben de ona.", "The moon looked at me, and I at it." },
        { "Sabah kapiyi actim; kimse yoktu.", "At dawn I opened the door; no one was there." },
        { "Hala nefes aliyorum. Bu yeter.", "I am still breathing. That is enough." }
    };

    /** JS String.replace(string, string): yalniz ilk eslesme. */
    private static String replaceFirstLit(String s, String target, String repl) {
        int i = s.indexOf(target);
        return i < 0 ? s : s.substring(0, i) + repl + s.substring(i + target.length());
    }

    private static void startNight(Player p) {
        Snap s = new Snap();
        s.ev = getStat(p, "v10events", 0); s.dist = getStat(p, "distance", 0); s.spikes = getStat(p, "noiseSpikes", 0); s.n = getStat(p, "nightsSurvived", 0);
        snap.put(p.id(), s);
        minSan.put(p.id(), getSanity(p));
    }

    private static void endNight(Player p) {
        Snap s = snap.get(p.id());
        if (s == null) return;
        double ev = getStat(p, "v10events", 0) - s.ev;
        long dist = Math.round(getStat(p, "distance", 0) - s.dist);
        double spikes = getStat(p, "noiseSpikes", 0) - s.spikes;
        long m = Math.round(minSan.get(p.id()) == null ? getSanity(p) : minSan.get(p.id()));
        if (isEnabled("night_report")) {
            say(p, tx("§6=== Gece Raporu ===§r\n§7Yasanan olay: §f", "§6=== Night Report ===§r\n§7Events lived: §f") + num(ev)
                + tx("\n§7Yurunen: §f", "\n§7Walked: §f") + dist
                + tx(" blok\n§7En dusuk akil sagligi: §f", " blocks\n§7Lowest sanity: §f") + m
                + tx("\n§7Gurultu patlamasi: §f", "\n§7Noise spikes: §f") + num(spikes));
        }
        if (isEnabled("night_verse")) {
            List<String[]> picks = new ArrayList<>();
            List<String[]> pool = new ArrayList<>(Arrays.asList(VERSE));
            while (picks.size() < 3 && !pool.isEmpty()) picks.add(pool.remove(randInt(0, pool.size() - 1)));
            List<String> parts = new ArrayList<>();
            for (String[] v : picks) {
                String t = tx(v);
                t = replaceFirstLit(t, "{n}", num(s.n + 1));
                t = replaceFirstLit(t, "{d}", String.valueOf(dist));
                t = replaceFirstLit(t, "{m}", String.valueOf(m));
                t = replaceFirstLit(t, "{e}", num(ev));
                parts.add(t);
            }
            final String text = String.join("\n", parts);
            Sys.runTimeout(() -> { if (isValid(p)) say(p, "§8§o" + text + "§r"); }, 60);
        }
        snap.remove(p.id());
    }

    // ---------------------------------------------------------
    //  5) Yalan pusula
    // ---------------------------------------------------------
    private static final String[][] DIRS = { { "K", "N" }, { "KD", "NE" }, { "D", "E" }, { "GD", "SE" }, { "G", "S" }, { "GB", "SW" }, { "B", "W" }, { "KB", "NW" } };

    private static String compassText(Player player) {
        Vec3 sp = safe(() -> player.getSpawnPoint(), (Vec3) null);
        if (sp == null) return tx("§7Bir yatagin / dogma noktan yok.§r", "§7You have no bed or spawn point set.§r");
        Vec3 l = player.location();
        double dx = sp.x() - l.x(), dz = sp.z() - l.z();
        long dist = Math.round(Math.hypot(dx, dz));
        boolean lying = getSanity(player) < 25;
        if (lying) { double a = Math.random() * Math.PI * 2; dx = Math.cos(a) * dist; dz = Math.sin(a) * dist; dist = Math.round(dist * rand(0.3, 2.2)); }
        double ang = Math.atan2(dx, -dz);               // 0 = kuzey (-z), saat yonu
        int idx = (int) (((Math.round(ang / (Math.PI / 4)) % 8) + 8) % 8);
        return tx("§6Pusula: §f", "§6Compass: §f") + tx(DIRS[idx]) + " §7" + dist + tx(" blok", " blocks") + (lying ? tx(" §8(igne titriyor...)", " §8(the needle trembles...)") : "");
    }

    // =========================================================
    public static void init() {
        if (initialized) return;
        initialized = true;

        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("lost_letters")) return;
            for (Player p : World.getPlayers()) {
                try {
                    Container inv = p.inventory();
                    if (inv == null) continue;
                    List<Object> found = foundList(p);
                    boolean dirty = false;
                    for (int s = 0; s < inv.size(); s++) {
                        final int slot = s;
                        ItemStack it = safe(() -> inv.getItem(slot), (ItemStack) null);
                        if (it == null) continue;
                        List<String> lore = safe(() -> it.getLore(), new ArrayList<String>());
                        if (lore != null && !lore.isEmpty() && lore.get(0).indexOf(LMARK) == 0) {
                            int idx = parseIntJs(lore.get(0).substring(LMARK.length()));
                            if (idx != Integer.MIN_VALUE && !containsNum(found, idx)) {
                                found.add((double) idx); dirty = true;
                                addStat(p, "lettersFound");
                                say(p, tx("§6[Mektup]§r Yeni bir mektup bulundu: ", "§6[Letter]§r You found a new letter: ") + LETTERS[idx].name() + " §8(" + found.size() + "/12)");
                                playTo(p, "random.orb", 0.6, 1.0);
                            }
                        }
                    }
                    if (dirty) playerSet(p, LKEY, found);
                } catch (Throwable e) { /* yut */ }
            }
        }, TICKS_PER_SEC * 10);

        Sys.runInterval(() -> {
            for (Player p : World.getPlayers()) {
                try {
                    List<Integer> a = samples.computeIfAbsent(p.id(), k -> new ArrayList<>());
                    a.add((int) Math.round(getSanity(p)));
                    while (a.size() > 48) a.remove(0);
                } catch (Throwable e) { /* yut */ }
            }
        }, TICKS_PER_SEC * 30);

        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("ranks")) return;
            for (Player p : World.getPlayers()) {
                try {
                    Rank r = rankOf(p);
                    Object last = safe(() -> p.getDynamicProperty("duskwalker:v12rank"), (Object) null);
                    if (!r.name.equals(last)) {
                        safe(() -> p.setDynamicProperty("duskwalker:v12rank", r.name));
                        if (last != null) { title(p, tx("§6Yeni Unvan", "§6New Rank"), "§e" + r.name, 70); playTo(p, "random.levelup", 0.8, 0.8); }
                    }
                } catch (Throwable e) { /* yut */ }
            }
        }, TICKS_PER_SEC * 15);

        Sys.runInterval(() -> {
            if (isDefeated()) { lastT = -1; return; }
            int t = timeOfDay();
            if (lastT >= 0) {
                if (lastT < 12500 && t >= 12500) for (Player p : World.getPlayers()) safe(() -> startNight(p));
                if (lastT > 18000 && t < 4000) for (Player p : World.getPlayers()) safe(() -> endNight(p));
            }
            lastT = t;
        }, 40);

        Sys.runInterval(() -> {
            for (Player p : World.getPlayers()) {
                Double cur = minSan.get(p.id());
                if (cur != null) minSan.put(p.id(), Math.min(cur, getSanity(p)));
            }
        }, TICKS_PER_SEC * 15);

        Events.Before.chatSend.subscribe(ev -> {
            String m = ev.message;
            if (!m.equals("!dwletters") && !m.equals("!dwgraph") && !m.equals("!dwrank") && !m.equals("!dwcompass")) return;
            ev.cancel = true;
            Player p = ev.sender;
            Sys.run(() -> {
                try {
                    if (m.equals("!dwletters")) { say(p, tx("§7Mektuplar aciliyor... sohbeti kapat.§r", "§7Opening letters... close the chat.§r")); openLetters(p); }
                    else if (m.equals("!dwgraph")) say(p, graphText(p));
                    else if (m.equals("!dwcompass")) say(p, compassText(p));
                    else {
                        Rank r = rankOf(p);
                        say(p, tx("§6Unvan: §f", "§6Rank: §f") + r.name + " §8(" + r.n + tx(" basarim", " achievements") + ")"
                            + (r.next >= 0 ? tx(" §7sonraki: ", " §7next: ") + r.next + tx(" basarimda", " achievements") : ""));
                    }
                } catch (Throwable e) { /* yut */ }
            });
        });

        Events.After.playerLeave.subscribe(ev -> { samples.remove(ev.playerId); snap.remove(ev.playerId); minSan.remove(ev.playerId); });
    }
}

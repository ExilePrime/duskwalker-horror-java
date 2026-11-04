package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

import static com.duskwalker.mod.Core.*;

/**
 * DUSKWALKER v11 - GECE NOBETI (gunluk gorevler) (quests.js)
 * Her aksam her oyuncuya rastgele bir nobet gorevi verilir; sabah degerlendirilir. !quest ile ilerleme.
 */
public final class Quests {
    private Quests() {}

    private static final String QUEST_KEY = "duskwalker:v11quest";

    private static final class QDef {
        final String id, stat, kind;
        final IntSupplier gen;
        final IntFunction<String> text;
        QDef(String id, String stat, String kind, IntSupplier gen, IntFunction<String> text) {
            this.id = id; this.stat = stat; this.kind = kind; this.gen = gen; this.text = text;
        }
    }

    private static final QDef[] POOL = {
        new QDef("torches", "torchesPlaced", "delta", () -> randInt(6, 12),
            n -> tx("Gece boyunca " + n + " mesale yerlestir", "Place " + n + " torches tonight")),
        new QDef("refuge", "refugeSeconds", "delta", () -> randInt(90, 180),
            n -> tx("Isik siginaginda toplam " + n + " saniye gecir", "Spend " + n + " seconds in a light refuge")),
        new QDef("blocks", "blocksPlaced", "delta", () -> randInt(40, 90),
            n -> tx("Gece boyunca " + n + " blok yerlestir", "Place " + n + " blocks tonight")),
        new QDef("walk", "distance", "delta", () -> randInt(200, 450),
            n -> tx("Gece boyunca " + n + " blok yuru", "Walk " + n + " blocks tonight")),
        new QDef("quiet", "noiseSpikes", "zero", () -> 0,
            n -> tx("Gece boyunca sessiz kal: Duskwalker seni duymasin", "Stay quiet all night: don't let the Duskwalker hear you")),
        new QDef("calm", "", "low", () -> 35,
            n -> tx("Saglamligini gece boyunca " + n + "'in ustunde tut", "Keep your sanity above " + n + " all night"))
    };

    private static final Object[][] REWARDS = {
        { "minecraft:torch", 16 }, { "minecraft:golden_carrot", 4 }, { "minecraft:glow_berries", 6 },
        { "minecraft:bread", 6 }, { "minecraft:experience_bottle", 2 }, { "minecraft:candle", 4 }
    };

    private static QDef questDef(String id) {
        for (QDef q : POOL) if (q.id.equals(id)) return q;
        return null;
    }

    private static final class Progress { int cur; boolean ok; Progress(int c, boolean o) { cur = c; ok = o; } }

    private static Progress progressOf(Player player, Map<String, Object> q) {
        QDef def = questDef(Json.str(q.get("id"), ""));
        if (def == null) return new Progress(0, false);
        double base = Json.num(q.get("base"), 0), target = Json.num(q.get("target"), 0), low = Json.num(q.get("low"), 0);
        if (def.kind.equals("delta")) {
            int cur = (int) Math.floor(getStat(player, def.stat) - base);
            return new Progress(cur, cur >= target);
        }
        if (def.kind.equals("zero")) {
            int cur = (int) Math.floor(getStat(player, def.stat) - base);
            return new Progress(cur, cur == 0);
        }
        return new Progress((int) Math.round(low), low >= target);
    }

    private static void assign(Player player) {
        QDef def = pick(POOL);
        int target = def.gen.getAsInt();
        Map<String, Object> q = Json.newMap();
        q.put("id", def.id);
        q.put("target", (double) target);
        q.put("base", def.stat.isEmpty() ? 0.0 : getStat(player, def.stat));
        q.put("low", getSanity(player));
        q.put("announced", false);
        playerSet(player, QUEST_KEY, q);
        title(player, tx("§6Gece Nobeti", "§6Night Watch"), "§7" + def.text.apply(target), 80);
        say(player, tx("§6[Nobet]§r ", "§6[Watch]§r ") + def.text.apply(target));
    }

    private static void settle(Player player) {
        Map<String, Object> q = Json.asMap(playerGet(player, QUEST_KEY, null));
        if (q == null) return;
        Progress result = progressOf(player, q);
        safe(() -> player.setDynamicProperty(QUEST_KEY, null));
        if (result.ok) {
            gainSanity(player, 20);
            Object[] r = pick(REWARDS);
            giveItem(player, (String) r[0], (Integer) r[1]);
            addStat(player, "questsDone");
            title(player, tx("§aNobet Tamam", "§aWatch Complete"), tx("§7+20 saglamlik ve bir odul", "§7+20 sanity and a reward"), 60);
            playTo(player, "random.levelup", 0.7, 1.1);
        } else {
            loseSanity(player, 5);
            addStat(player, "questsFailed");
            actionbar(player, tx("§8Nobet basarisiz oldu.§r", "§8The watch failed.§r"));
        }
    }

    private static int lastT = -1;

    public static void init() {
        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("quests")) { lastT = -1; return; }
            int t = timeOfDay();
            if (lastT >= 0) {
                if (lastT < 12500 && t >= 12500) {
                    for (Player p : World.getPlayers()) if (isSurvival(p)) safe(() -> assign(p));
                }
                if (lastT > 18000 && t < 4000) {
                    for (Player p : World.getPlayers()) safe(() -> settle(p));
                }
            }
            lastT = t;
        }, 40);

        // gece boyunca en dusuk saglamligi izle + erken tamamlama bildirimi
        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("quests")) return;
            for (Player p : World.getPlayers()) {
                try {
                    Map<String, Object> q = Json.asMap(playerGet(p, QUEST_KEY, null));
                    if (q == null) continue;
                    double s = getSanity(p);
                    boolean dirty = false;
                    if (s < Json.num(q.get("low"), 0)) { q.put("low", s); dirty = true; }
                    Progress r = progressOf(p, q);
                    if (r.ok && !Json.bool(q.get("announced"), false) && questDef(Json.str(q.get("id"), "")).kind.equals("delta")) {
                        q.put("announced", true); dirty = true;
                        actionbar(p, tx("§aHedef tamam! Sabaha kadar hayatta kal.§r", "§aGoal reached! Survive until dawn.§r"));
                    }
                    if (dirty) playerSet(p, QUEST_KEY, q);
                } catch (Throwable e) { /* yut */ }
            }
        }, TICKS_PER_SEC * 5);

        try {
            Events.Before.chatSend.subscribe(ev -> {
                if (!"!quest".equals(ev.message)) return;
                ev.cancel = true;
                Player p = ev.sender;
                Sys.run(() -> {
                    Map<String, Object> q = Json.asMap(playerGet(p, QUEST_KEY, null));
                    if (q == null) { say(p, tx("§7Su an aktif bir nobet gorevin yok. Aksam olunca yenisi gelir.§r", "§7You have no active watch. A new one arrives at dusk.§r")); return; }
                    QDef def = questDef(Json.str(q.get("id"), ""));
                    Progress r = progressOf(p, q);
                    int target = (int) Json.num(q.get("target"), 0);
                    String prog = def.kind.equals("low") ? tx("en dusuk saglamlik: ", "lowest sanity: ") + r.cur
                        : (def.kind.equals("zero") ? tx("gurultu patlamasi: ", "noise spikes: ") + r.cur : r.cur + " / " + target);
                    say(p, tx("§6Nobet:§r ", "§6Watch:§r ") + def.text.apply(target) + "\n§7" + prog + (r.ok ? " §a✔" : ""));
                });
            });
        } catch (Throwable e) { /* yut */ }
    }
}

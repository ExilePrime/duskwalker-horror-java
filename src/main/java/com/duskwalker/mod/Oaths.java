package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.Map;

import static com.duskwalker.mod.Core.*;

/**
 * DUSKWALKER v12 - YEMINLER (risk-odul katmani)  [v10/oaths.js]
 * Geceleyin !dwoath ile yemin: SESSIZ (kosma), BARIS (zarar verme), BOS ELLER (blok koyma).
 */
public final class Oaths {
    private Oaths() {}

    private static final String OATH_KEY = "duskwalker:v12oath";

    private static final class OathDef {
        final String id; final String[] name; final String[] desc; final String rewardId; final int rewardCount;
        OathDef(String id, String[] name, String[] desc, String rewardId, int rewardCount) {
            this.id = id; this.name = name; this.desc = desc; this.rewardId = rewardId; this.rewardCount = rewardCount;
        }
    }

    private static final OathDef[] OATHS = {
        new OathDef("silent", new String[] { "Sessiz Yemin", "Silent Oath" }, new String[] { "Gece boyunca kosma.", "Do not sprint all night." }, "minecraft:golden_carrot", 6),
        new OathDef("pacifist", new String[] { "Baris Yemini", "Pacifist Oath" }, new String[] { "Gece boyunca hicbir varliga zarar verme.", "Harm no creature all night." }, "minecraft:golden_apple", 1),
        new OathDef("bare", new String[] { "Bos Eller", "Bare Hands" }, new String[] { "Gece boyunca hic blok koyma.", "Place no blocks all night." }, "minecraft:torch", 24)
    };

    private static OathDef oathDef(String id) {
        for (OathDef o : OATHS) if (o.id.equals(id)) return o;
        return null;
    }

    private static double dayStamp() { return Math.floor(safe(() -> (double) World.getAbsoluteTime(), 0.0) / 24000); }

    /** Kayitli yemin durumu (id, day, broken) veya null */
    private static Map<String, Object> state(Player p) { return Json.asMap(playerGet(p, OATH_KEY, null)); }

    private static void swear(Player player, OathDef def) {
        Map<String, Object> st = Json.newMap();
        st.put("id", def.id); st.put("day", dayStamp()); st.put("broken", false);
        playerSet(player, OATH_KEY, st);
        title(player, tx("§6Yemin", "§6Oath"), "§7" + tx(def.name), 60);
        say(player, tx("§6[Yemin]§r ", "§6[Oath]§r ") + tx(def.desc));
        playTo(player, "duskwalker.drone", 0.6, 1.1);
        addStat(player, "oathsSworn");
    }

    private static void violate(Player player, String why) {
        Map<String, Object> st = state(player);
        if (st == null || Json.bool(st.get("broken"), false)) return;
        st.put("broken", true);
        playerSet(player, OATH_KEY, st);
        loseSanity(player, 15);
        effect(player, "darkness", 400, 0);
        title(player, tx("§4Yemin Bozuldu", "§4Oath Broken"), "§7" + why, 60);
        playTo(player, "duskwalker.creak", 0.9, 0.6);
        addStat(player, "oathsBroken");
    }

    private static int lastT = -1;

    public static void init() {
        // ihlal algilama
        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("oaths")) return;
            for (Player p : World.getPlayers()) {
                try {
                    Map<String, Object> st = state(p);
                    if (st == null || Json.bool(st.get("broken"), false) || !"silent".equals(st.get("id"))) continue;
                    if (p.isSprinting()) violate(p, tx("kostun", "you ran"));
                } catch (Throwable e) { /* yut */ }
            }
        }, 10);

        try {
            Events.After.entityHurt.subscribe(ev -> {
                try {
                    Entity src = ev.damageSource == null ? null : ev.damageSource.damagingEntity;
                    if (src == null || !"minecraft:player".equals(src.typeId())) return;
                    Player sp = (Player) src;
                    Map<String, Object> st = state(sp);
                    if (st != null && !Json.bool(st.get("broken"), false) && "pacifist".equals(st.get("id"))) violate(sp, tx("zarar verdin", "you harmed something"));
                } catch (Throwable e) { /* yut */ }
            });
        } catch (Throwable e) { /* yut */ }

        try {
            Events.After.playerPlaceBlock.subscribe(ev -> {
                try {
                    Map<String, Object> st = state(ev.player);
                    if (st != null && !Json.bool(st.get("broken"), false) && "bare".equals(st.get("id"))) violate(ev.player, tx("blok koydun", "you placed a block"));
                } catch (Throwable e) { /* yut */ }
            });
        } catch (Throwable e) { /* yut */ }

        // sabah degerlendirme
        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("oaths")) { lastT = -1; return; }
            int t = timeOfDay();
            if (lastT > 18000 && t < 4000) {
                for (Player p : World.getPlayers()) {
                    try {
                        Map<String, Object> st = state(p);
                        if (st == null) continue;
                        safe(() -> p.setDynamicProperty(OATH_KEY, null));
                        if (Json.bool(st.get("broken"), false)) continue;
                        String sid = Json.str(st.get("id"), "");
                        OathDef def = oathDef(sid);
                        if (def == null) continue;
                        gainSanity(p, 25);
                        giveItem(p, def.rewardId, def.rewardCount);
                        addStat(p, "oathsKept");
                        logChronicle(p, "oath", sid);
                        title(p, tx("§aYemin Tutuldu", "§aOath Kept"), "§7" + tx(def.name), 70);
                        playTo(p, "random.levelup", 0.8, 0.9);
                    } catch (Throwable e) { /* yut */ }
                }
            }
            lastT = t;
        }, 40);

        try {
            Events.Before.chatSend.subscribe(ev -> {
                if (!"!dwoath".equals(ev.message)) return;
                ev.cancel = true;
                Player p = ev.sender;
                Sys.run(() -> {
                    say(p, tx("§7Yemin menusu... sohbeti kapat.§r", "§7Oath menu... close the chat.§r"));
                    safe(() -> openOathForm(p));
                });
            });
        } catch (Throwable e) { /* yut */ }
    }

    private static void openOathForm(Player player) {
        Map<String, Object> active = state(player);
        if (active != null) {
            OathDef def = oathDef(Json.str(active.get("id"), ""));
            say(player, tx("§6Aktif yemin: §f", "§6Active oath: §f")
                + (def != null ? tx(def.name) : Json.str(active.get("id"), "undefined"))
                + (Json.bool(active.get("broken"), false) ? tx(" §c(bozuldu)", " §c(broken)") : tx(" §a(tutuluyor)", " §a(kept so far)")));
            return;
        }
        int t = timeOfDay();
        if (!(t >= 11500 || t <= 100)) { say(player, tx("§7Yemin sadece aksam/gece edilir.§r", "§7Oaths are sworn only at dusk or night.§r")); return; }
        Forms.Action form = new Forms.Action().title(tx("Yemin", "Oath"))
            .body(tx("§7Bir yemin sec. Sabaha kadar tutarsan odul, bozarsan ceza.", "§7Pick an oath. Keep it until dawn for a reward; break it and pay."));
        for (OathDef o : OATHS) form.button("§f" + tx(o.name) + "\n§7" + tx(o.desc));
        showForm(player, form, res -> {
            if (res.selection >= 0 && res.selection < OATHS.length) swear(player, OATHS[res.selection]);
        });
    }
}

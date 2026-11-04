package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.*;

import static com.duskwalker.mod.Core.*;

/** DUSKWALKER v11 - KRONIK (kisisel olay gunlugu). chronicle.js portu. */
public final class Chronicle {
    private Chronicle() {}

    private static final String[][] WATCH = {
        { "nightsSurvived", "night" }, { "questsDone", "quest" }, { "ruinsFound", "ruin" },
        { "houseVisits", "house" }, { "oathsKept", "oath" }, { "deaths", "death" }, { "dreams", "dream" }, { "blinkSurvived", "blink" }
    };
    private static final Map<String, Map<String, Double>> last = new HashMap<>(); // oyuncu id -> { stat: deger }

    private static String n(double v) { return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v); }

    private static String describe(Map<String, Object> entry) {
        String k = Json.str(entry.get("k"), "");
        Object ro = entry.get("r");
        String r = ro instanceof Number num ? n(num.doubleValue()) : (ro == null ? "undefined" : String.valueOf(ro));
        if (k.equals("event")) {
            Codex.Entry found = null;
            for (Codex.Entry x : Codex.ENTRIES) if (x.key.equals("ev_" + r)) { found = x; break; }
            return tx("Yasandi: ", "Experienced: ") + (found != null ? tx(found.name) : r);
        }
        String[] t;
        switch (k) {
            case "night" -> t = new String[] { "Bir geceyi atlattin (" + r + ".)", "Survived a night (#" + r + ")" };
            case "quest" -> t = new String[] { "Bir nobet gorevini tamamladin", "Completed a night watch" };
            case "ruin" -> t = new String[] { "Bir harabe buldun", "Discovered a ruin" };
            case "house" -> t = new String[] { "Bos Ev'e girdin", "Entered the Hollow House" };
            case "death" -> t = new String[] { "Oldun (" + r + ". kez)", "You died (time " + r + ")" };
            case "dream" -> t = new String[] { "Bir ruya gordun", "You had a dream" };
            case "blink" -> t = new String[] { "Don't Blink'ten kurtuldun", "Survived Don't Blink" };
            case "paradox" -> t = new String[] { "Paradoksu cozdun", "Solved the paradox" };
            case "oath" -> t = new String[] { "Bir yemini tuttun", "Kept an oath" };
            case "depths" -> t = new String[] { "Bos Derinlikleri astin", "Conquered the Hollow Depths" };
            case "pact" -> t = new String[] { "Duskwalker ile anlastin: " + r, "Made a pact with the Duskwalker: " + r };
            default -> t = null;
        }
        return t != null ? tx(t) : k;
    }

    public static void openChronicle(Player player) {
        List<Object> list = new ArrayList<>(readChronicle(player));
        Collections.reverse(list);
        String body;
        if (list.isEmpty()) {
            body = tx("§7Henuz bir kayit yok.", "§7No entries yet.");
        } else {
            StringBuilder sb = new StringBuilder();
            int cnt = Math.min(25, list.size());
            for (int i = 0; i < cnt; i++) {
                Map<String, Object> e = Json.asMap(list.get(i));
                if (e == null) continue;
                if (i > 0) sb.append("\n");
                sb.append("§8").append(tx("Gun ", "Day ")).append(n(Json.num(e.get("d"), 0))).append("§7: §f").append(describe(e));
            }
            body = sb.toString();
        }
        Forms.Action form = new Forms.Action().title(tx("Kronik", "Chronicle")).body(body).button(tx("Kapat", "Close"));
        showForm(player, form, r -> { });
    }

    public static void init() {
        Sys.runInterval(() -> {
            for (Player p : World.getPlayers()) {
                try {
                    Map<String, Double> seen = last.get(p.id());
                    if (seen == null) {
                        seen = new HashMap<>();
                        for (String[] w : WATCH) seen.put(w[0], getStat(p, w[0], 0));
                        last.put(p.id(), seen);
                        continue;
                    }
                    for (String[] w : WATCH) {
                        double now = getStat(p, w[0], 0);
                        if (now > seen.getOrDefault(w[0], 0.0)) { logChronicle(p, w[1], n(now)); seen.put(w[0], now); }
                    }
                } catch (Throwable e) { /* yut */ }
            }
        }, 100);

        try { Events.After.playerLeave.subscribe(ev -> last.remove(ev.playerId)); } catch (Throwable e) { /* yut */ }

        try {
            Events.Before.chatSend.subscribe(ev -> {
                if (!"!dwchronicle".equals(ev.message)) return;
                ev.cancel = true;
                Player p = ev.sender;
                Sys.run(() -> {
                    say(p, tx("§7Kronik aciliyor... sohbeti kapat.§r", "§7Opening chronicle... close the chat.§r"));
                    safe(() -> openChronicle(p));
                });
            });
        } catch (Throwable e) { /* yut */ }
    }
}

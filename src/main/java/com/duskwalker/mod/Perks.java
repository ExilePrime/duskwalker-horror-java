package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.*;

import static com.duskwalker.mod.Core.*;

/** DUSKWALKER v11 - AZIM YETENEKLERI. perks.js portu. */
public final class Perks {
    private Perks() {}

    /** id, ad, aciklama, bedel, gereken yetenek ("" = yok) */
    public static final class Perk {
        public final String id; public final String[] name, desc; public final int cost; public final String req;
        Perk(String id, String nameTr, String nameEn, String descTr, String descEn, int cost, String req) {
            this.id = id; this.name = new String[] { nameTr, nameEn }; this.desc = new String[] { descTr, descEn }; this.cost = cost; this.req = req;
        }
    }

    public static final List<Perk> PERKS = List.of(
        new Perk("steady_hands", "Sabit Eller", "Steady Hands", "Korku kayiplari %10 azalir", "Fear losses reduced by 10%", 1, ""),
        new Perk("iron_nerves", "Demir Sinirler", "Iron Nerves", "Korku kayiplari ayrica %20 azalir", "Fear losses reduced a further 20%", 2, "steady_hands"),
        new Perk("soft_steps", "Yumusak Adimlar", "Soft Steps", "Gurultu %20 yavas birikir", "Noise builds 20% slower", 1, ""),
        new Perk("silent_soles", "Sessiz Tabanlar", "Silent Soles", "Gurultu ayrica %25 yavas birikir", "Noise builds a further 25% slower", 2, "soft_steps"),
        new Perk("lanternbearer", "Fener Tasiyici", "Lanternbearer", "Isik siginagi %50 daha cok toparlar", "Light refuges heal 50% more", 2, ""),
        new Perk("hearth_heart", "Ocak Yuregi", "Hearth Heart", "Isik siginagi ayrica %33 daha cok toparlar", "Light refuges heal a further 33% more", 3, "lanternbearer"),
        new Perk("dream_weaver", "Ruya Dokuyucu", "Dream Weaver", "Huzurlu ruya ihtimali artar", "Peaceful dreams are more likely", 2, ""),
        new Perk("quick_recovery", "Hizli Toparlanma", "Quick Recovery", "Kabus sonrasi kayip yariya iner", "Nightmare loss is halved", 2, ""),
        new Perk("lucky_hand", "Sansli El", "Lucky Hand", "Mum Kumari'nda nimet ihtimali artar", "Better odds of blessings in the Candle Gamble", 1, ""),
        new Perk("stone_stare", "Tas Bakis", "Stone Stare", "Don't Blink cezasi yariya iner", "Don't Blink penalty is halved", 2, "")
    );

    private static String n(double v) { return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v); }

    public static double totalPoints(Player player) {
        return Math.floor(
            getStat(player, "nightsSurvived", 0) +
            getStat(player, "questsDone", 0) * 2 +
            getStat(player, "ruinsFound", 0) +
            getStat(player, "houseVisits", 0) * 2 +
            getStat(player, "codexDone", 0) * 3 +
            getStat(player, "paradoxSolved", 0) * 2 +
            getStat(player, "oathsKept", 0) * 2 +
            getStat(player, "eclipseVigils", 0) * 3 +
            getStat(player, "siegesRepelled", 0) +
            getStat(player, "depthsCleared", 0) * 4 +
            getStat(player, "unpredictableNights", 0)
        );
    }

    private static double spent(Player player) {
        double n = 0;
        List<String> owned = getPerks(player);
        for (Perk p : PERKS) if (owned.contains(p.id)) n += p.cost;
        return n;
    }

    public static double pointsLeft(Player player) { return totalPoints(player) - spent(player); }

    private static void buy(Player player, Perk perk) {
        List<String> owned = new ArrayList<>(getPerks(player));
        if (owned.contains(perk.id)) return;
        if (!perk.req.isEmpty() && !owned.contains(perk.req)) {
            Perk need = null;
            for (Perk p : PERKS) if (p.id.equals(perk.req)) { need = p; break; }
            say(player, tx("§cOnce gerekeni al: ", "§cRequires first: ") + tx(need.name) + "§r");
            return;
        }
        if (pointsLeft(player) < perk.cost) {
            say(player, tx("§cYeterli Azim puanin yok.§r", "§cNot enough Resolve points.§r"));
            return;
        }
        owned.add(perk.id);
        setPerks(player, owned);
        addStat(player, "perksBought");
        title(player, tx("§6Yetenek", "§6Perk"), "§e" + tx(perk.name), 50);
        playTo(player, "random.levelup", 0.8, 1.0);
    }

    public static void openPerks(Player player) {
        Forms.Action form = new Forms.Action()
            .title(tx("Azim Yetenekleri", "Resolve Perks"))
            .body(tx("§7Kalan Azim puani: §f", "§7Resolve points left: §f") + n(pointsLeft(player)) + " §8/ " + n(totalPoints(player)));
        for (Perk p : PERKS) {
            boolean have = hasPerk(player, p.id);
            boolean locked = !p.req.isEmpty() && !hasPerk(player, p.req);
            String label = have ? "§a[x] " + tx(p.name) : locked ? "§8[ ] " + tx(p.name) : "§f[ ] " + tx(p.name) + " §e(" + p.cost + ")";
            form.button(label + "\n§7" + tx(p.desc));
        }
        showForm(player, form, res -> {
            if (res.selection >= 0 && res.selection < PERKS.size()) {
                buy(player, PERKS.get(res.selection));
                Sys.runTimeout(() -> safe(() -> openPerks(player)), 10);
            }
        });
    }

    public static void init() {
        try {
            Events.Before.chatSend.subscribe(ev -> {
                if (!"!dwperks".equals(ev.message)) return;
                ev.cancel = true;
                Player p = ev.sender;
                Sys.run(() -> {
                    say(p, tx("§7Yetenek menusu aciliyor... sohbeti kapat.§r", "§7Opening perks... close the chat.§r"));
                    safe(() -> openPerks(p));
                });
            });
        } catch (Throwable e) { /* yut */ }
    }
}

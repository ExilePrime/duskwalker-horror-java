package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.*;

import static com.duskwalker.mod.Core.*;

/**
 * DUSKWALKER v14 - #5 ANLASMALAR (Duskwalker ile pazarlik). pacts.js portu.
 * !dwpact / !dwpact decline / !dwpay
 */
public final class Pacts {
    private Pacts() {}

    private static final String PACT_KEY = "duskwalker:v14pact";
    private static final String OFFER_KEY = "duskwalker:v14offer";
    private static final String DEBT_KEY = "duskwalker:v14debt";

    private static double absDay() { return Math.floor(safe(() -> (double) World.getAbsoluteTime(), 0.0) / 24000); }

    private static void clearKey(Player p, String key) { safe(() -> p.setDynamicProperty(key, null)); }

    private static final class Fx { final String id; final int amp; Fx(String id, int amp) { this.id = id; this.amp = amp; } }
    private static final class Debt { final String item; final int count; final int days; Debt(String item, int count, int days) { this.item = item; this.count = count; this.days = days; } }

    /** id, ad, nimet, lanet, carpanlar, surekli efektler, borc, ozel */
    private static final class Pact {
        String id; String[] name, boon, curse; Map<String, Double> mods = new HashMap<>(); List<Fx> fx = new ArrayList<>();
        Debt debt; boolean dawnLoot; boolean insight;
    }

    private static Pact pact(String id, String nTr, String nEn, String bTr, String bEn, String cTr, String cEn, Object[] mods, Fx[] fx) {
        Pact p = new Pact();
        p.id = id; p.name = new String[] { nTr, nEn }; p.boon = new String[] { bTr, bEn }; p.curse = new String[] { cTr, cEn };
        for (int i = 0; i < mods.length; i += 2) p.mods.put((String) mods[i], ((Number) mods[i + 1]).doubleValue());
        p.fx = new ArrayList<>(Arrays.asList(fx));
        return p;
    }

    private static final List<Pact> PACTS = new ArrayList<>();
    static {
        PACTS.add(pact("sight", "Gorus", "Sight", "Gece boyunca gece gorusu", "Night vision all night", "Gurultu 2 kat agir sayilir", "Noise counts double", new Object[] { "noise", 2 }, new Fx[] { new Fx("night_vision", 0) }));
        PACTS.add(pact("stride", "Adim", "Stride", "Gece boyunca hizli yuruyus", "Faster walking all night", "Korku kayiplari %40 artar", "Fear losses +40%", new Object[] { "fear", 1.4 }, new Fx[] { new Fx("speed", 0) }));
        PACTS.add(pact("warmth", "Sicaklik", "Warmth", "Isik siginagi 2 kat toparlar + yenilenme", "Refuges heal double + regeneration", "Surekli aclik", "Constant hunger", new Object[] { "regen", 2 }, new Fx[] { new Fx("regeneration", 0), new Fx("hunger", 1) }));
        PACTS.add(pact("hush", "Sus", "Hush", "Gurultu %70 azalir", "Noise reduced by 70%", "Zayiflik (az hasar)", "Weakness (less damage)", new Object[] { "noise", 0.3 }, new Fx[] { new Fx("weakness", 0) }));
        Pact fortune = pact("fortune", "Talih", "Fortune", "Sabah 3 ganimet", "3 treasures at dawn", "Olaylar %40 siklasir", "Events 40% more frequent", new Object[] { "gap", 0.6 }, new Fx[0]);
        fortune.dawnLoot = true;
        PACTS.add(fortune);
        Pact ward = pact("ward", "Koruma", "Ward", "Korku kayiplari yariya iner", "Fear losses halved", "BORC: 4 demir (2 gun icinde)", "DEBT: 4 iron ingots (within 2 days)", new Object[] { "fear", 0.5 }, new Fx[0]);
        ward.debt = new Debt("minecraft:iron_ingot", 4, 2);
        PACTS.add(ward);
        PACTS.add(pact("haste", "Acele", "Haste", "Gece boyunca hizli kazma", "Fast mining all night", "Korku +%25, gurultu +%50", "Fear +25%, noise +50%", new Object[] { "fear", 1.25, "noise", 1.5 }, new Fx[] { new Fx("haste", 1) }));
        Pact insight = pact("insight", "Iç Gorus", "Insight", "Yakindaki siluetleri isaretli gorursun", "You see nearby silhouettes marked", "Olaylar %30 siklasir", "Events 30% more frequent", new Object[] { "gap", 0.7 }, new Fx[0]);
        insight.insight = true;
        PACTS.add(insight);
        Pact mercy = pact("mercy", "Merhamet", "Mercy", "Korku kayiplari %40 azalir", "Fear losses -40%", "BORC: 2 altin (3 gun icinde)", "DEBT: 2 gold ingots (within 3 days)", new Object[] { "fear", 0.6 }, new Fx[0]);
        mercy.debt = new Debt("minecraft:gold_ingot", 2, 3);
        PACTS.add(mercy);
        PACTS.add(pact("gluttony", "Oburluk", "Gluttony", "Doygunluk (aclik hissetmezsin)", "Saturation (never hungry)", "Gurultu +%80, olaylar +%25", "Noise +80%, events +25%", new Object[] { "noise", 1.8, "gap", 0.8 }, new Fx[] { new Fx("saturation", 0) }));
    }

    private static Pact defOf(String id) {
        for (Pact x : PACTS) if (x.id.equals(id)) return x;
        return null;
    }

    private static String n(double v) { return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v); }

    // ---------------------------------------------------------
    //  Teklifler (her aksam)
    // ---------------------------------------------------------
    private static List<Object> makeOffers(Player p) {
        List<Pact> pool = new ArrayList<>(PACTS);
        List<Object> offers = new ArrayList<>();
        while (offers.size() < 3 && !pool.isEmpty()) offers.add(pool.remove((int) Math.floor(Math.random() * pool.size())).id);
        Map<String, Object> o = Json.newMap();
        o.put("day", absDay()); o.put("ids", offers); o.put("taken", false);
        playerSet(p, OFFER_KEY, o);
        return offers;
    }

    private static int lastT = -1;

    // ---------------------------------------------------------
    //  Kabul / uygulama
    // ---------------------------------------------------------
    private static void applyMods(Player p, Pact def) {
        Map<String, Double> m = new HashMap<>();
        m.put("fear", 1.0); m.put("noise", 1.0); m.put("regen", 1.0); m.put("gap", 1.0);
        m.putAll(def.mods);
        pactMods.put(p.id(), m);
    }

    private static boolean accept(Player p, String id) {
        Map<String, Object> offer = Json.asMap(playerGet(p, OFFER_KEY, null));
        Pact def = defOf(id);
        if (def == null || offer == null || Json.bool(offer.get("taken"), false)) return false;
        List<Object> ids = Json.asList(offer.get("ids"));
        if (ids == null || !ids.contains(id)) return false;
        if (!(timeOfDay() >= 11500 || timeOfDay() <= 100)) return false;
        offer.put("taken", true);
        playerSet(p, OFFER_KEY, offer);
        Map<String, Object> act = Json.newMap();
        act.put("id", id); act.put("day", absDay());
        playerSet(p, PACT_KEY, act);
        applyMods(p, def);
        if (def.debt != null) {
            Map<String, Object> d = Json.newMap();
            d.put("item", def.debt.item); d.put("count", (double) def.debt.count); d.put("due", absDay() + def.debt.days);
            playerSet(p, DEBT_KEY, d);
            String nm = def.debt.item.replace("minecraft:", "");
            say(p, tx("§c[Borc]§r " + def.debt.count + " " + nm + " ode: " + def.debt.days + " gun icinde (!dwpay).",
                "§c[Debt]§r Pay " + def.debt.count + " " + nm + " within " + def.debt.days + " days (!dwpay)."));
        }
        title(p, tx("§4Anlasma", "§4Pact"), "§7" + tx(def.name), 70);
        say(p, tx("§aNimet: §f", "§aBoon: §f") + tx(def.boon) + tx("\n§cLanet: §f", "\n§cCurse: §f") + tx(def.curse));
        playTo(p, "duskwalker.drone", 0.7, 0.6);
        addStat(p, "pactsAccepted");
        logChronicle(p, "pact", id);
        return true;
    }

    public static boolean acceptPact(Player p, String id) { return accept(p, id); }

    private static void settleNight(Player p) {
        Object active = playerGet(p, PACT_KEY, null);
        clearKey(p, PACT_KEY);
        pactMods.remove(p.id());
        Map<String, Object> am = Json.asMap(active);
        if (am == null) return;
        Pact def = defOf(Json.str(am.get("id"), ""));
        if (def != null && def.dawnLoot) {
            Object[][] loot = { { "minecraft:golden_carrot", 4 }, { "minecraft:bread", 5 }, { "minecraft:torch", 12 }, { "minecraft:experience_bottle", 3 }, { "minecraft:candle", 3 }, { "minecraft:golden_apple", 1 } };
            for (int i = 0; i < 3; i++) { Object[] it = pick(loot); giveItem(p, (String) it[0], (Integer) it[1]); }
            actionbar(p, tx("§6(talih sana guldu)§r", "§6(fortune smiled on you)§r"));
        }
    }

    // ---------------------------------------------------------
    //  Borc ve tahsilatci
    // ---------------------------------------------------------
    private static int countItem(Player p, String id) {
        Container c = safe(() -> p.inventory(), (Container) null);
        if (c == null) return 0;
        int n = 0;
        for (int i = 0; i < c.size(); i++) {
            final int slot = i;
            ItemStack it = safe(() -> c.getItem(slot), (ItemStack) null);
            if (it != null && it.typeId.equals(id)) n += it.amount;
        }
        return n;
    }

    private static boolean takeItem(Player p, String id, int count) {
        Container c = safe(() -> p.inventory(), (Container) null);
        if (c == null) return false;
        int left = count;
        for (int i = 0; i < c.size() && left > 0; i++) {
            final int slot = i;
            ItemStack it = safe(() -> c.getItem(slot), (ItemStack) null);
            if (it == null || !it.typeId.equals(id)) continue;
            int take = Math.min(left, it.amount);
            if (take >= it.amount) c.setItem(i, null);
            else { it.amount -= take; c.setItem(i, it); }
            left -= take;
        }
        return left <= 0;
    }

    private static void checkDebt(Player p) {
        Map<String, Object> debt = Json.asMap(playerGet(p, DEBT_KEY, null));
        if (debt == null || absDay() <= Json.num(debt.get("due"), 0)) return;
        // vadesi gecti: tahsilatci
        clearKey(p, DEBT_KEY);
        title(p, tx("§4Tahsilatci", "§4The Collector"), tx("§7borcunu odemedin", "§7you did not pay"), 80);
        loseSanity(p, 20);
        addStat(p, "debtsDefaulted");
        for (String id : new String[] { "dont_blink", "ring_of_faces", "countdown" }) { if (triggerEvent(id, p, true)) break; }
    }

    private static void pay(Player p) {
        Map<String, Object> debt = Json.asMap(playerGet(p, DEBT_KEY, null));
        if (debt == null) { say(p, tx("§7Borcun yok.§r", "§7You owe nothing.§r")); return; }
        String item = Json.str(debt.get("item"), "");
        int count = (int) Json.num(debt.get("count"), 0);
        if (countItem(p, item) < count) {
            say(p, tx("§cYeterli esyan yok: ", "§cNot enough items: ") + count + " " + item.replace("minecraft:", "") + tx(" gerekli.", " needed."));
            return;
        }
        takeItem(p, item, count);
        clearKey(p, DEBT_KEY);
        gainSanity(p, 5);
        addStat(p, "debtsPaid");
        title(p, tx("§aBorc Odendi", "§aDebt Paid"), "", 50);
        playTo(p, "random.orb", 0.7, 0.8);
    }

    // ---------------------------------------------------------
    //  Form ve komutlar
    // ---------------------------------------------------------
    public static void openOffers(Player p) {
        Map<String, Object> offer = Json.asMap(playerGet(p, OFFER_KEY, null));
        if (offer == null || Json.num(offer.get("day"), -1) != absDay() || !(timeOfDay() >= 11500 || timeOfDay() <= 100)) {
            say(p, tx("§7Su an bir teklif yok. Aksam olunca Duskwalker yeniden gelir.§r", "§7No offer right now. The Duskwalker returns at dusk.§r"));
            return;
        }
        if (Json.bool(offer.get("taken"), false)) { say(p, tx("§7Bu gece zaten bir anlasma yaptin.§r", "§7You already made a pact tonight.§r")); return; }
        List<Object> ids = Json.asList(offer.get("ids"));
        if (ids == null) return;
        Forms.Action form = new Forms.Action().title(tx("Anlasmalar", "Pacts")).body(tx("§7Bir anlasma sec ya da reddet.", "§7Choose a pact or decline."));
        for (Object o : ids) {
            Pact d = defOf(String.valueOf(o));
            form.button("§f" + tx(d.name) + "\n§a+ " + tx(d.boon) + "  §c- " + tx(d.curse));
        }
        form.button(tx("§8Reddet (+2 saglamlik)", "§8Decline (+2 sanity)"));
        showForm(p, form, res -> {
            int i = res.selection;
            if (i == ids.size()) { declineOffer(p); return; }
            if (i >= 0 && i < ids.size()) accept(p, String.valueOf(ids.get(i)));
        });
    }

    private static void declineOffer(Player p) {
        Map<String, Object> offer = Json.asMap(playerGet(p, OFFER_KEY, null));
        if (offer == null || Json.bool(offer.get("taken"), false)) return;
        offer.put("taken", true);
        playerSet(p, OFFER_KEY, offer);
        gainSanity(p, 2);
        addStat(p, "pactsDeclined");
        actionbar(p, tx("§7(reddettin)§r", "§7(you refused)§r"));
    }

    public static void init() {
        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("pacts")) { lastT = -1; return; }
            int t = timeOfDay();
            if (lastT >= 0) {
                if (lastT < 12500 && t >= 12500) {
                    for (Player p : World.getPlayers()) {
                        if (!isSurvival(p)) continue;
                        safe(() -> {
                            makeOffers(p);
                            say(p, tx("§4Duskwalker§r: Bir anlasma teklif ediyorum. §8(!dwpact)", "§4Duskwalker§r: I offer you a bargain. §8(!dwpact)"));
                            playTo(p, "duskwalker.whisper", 0.6, 0.7);
                            checkDebt(p);
                        });
                    }
                }
                if (lastT > 18000 && t < 4000) for (Player p : World.getPlayers()) safe(() -> settleNight(p));
            }
            lastT = t;
        }, 40);

        // restore: oyuncu geceyi yarida actiysa carpanlar geri gelir
        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("pacts")) return;
            for (Player p : World.getPlayers()) {
                try {
                    Map<String, Object> active = Json.asMap(playerGet(p, PACT_KEY, null));
                    if (active == null) { pactMods.remove(p.id()); continue; }
                    Pact def = defOf(Json.str(active.get("id"), ""));
                    if (def == null) continue;
                    if (!pactMods.containsKey(p.id())) applyMods(p, def);
                    if (!isSurvival(p) || realm.contains(p.id())) continue;
                    for (Fx f : def.fx) effect(p, f.id, 160, f.amp);
                    if (def.insight) {
                        List<Entity> list = safe(() -> p.dimension().getEntities(Opts.EntityQuery.type("duskwalker:effigy").near(p.location(), 45)), new ArrayList<Entity>());
                        for (Entity e : list) particle(p.dimension(), "minecraft:villager_angry", new Vec3(e.location().x(), e.location().y() + 2.4, e.location().z()));
                    }
                } catch (Throwable e) { /* yut */ }
            }
        }, 100);

        try {
            Events.Before.chatSend.subscribe(ev -> {
                String m = ev.message;
                if (!"!dwpact".equals(m) && !"!dwpact decline".equals(m) && !"!dwpay".equals(m)) return;
                ev.cancel = true;
                Player p = ev.sender;
                Sys.run(() -> {
                    try {
                        if (m.equals("!dwpay")) { pay(p); return; }
                        if (m.equals("!dwpact decline")) { declineOffer(p); return; }
                        say(p, tx("§7Anlasmalar aciliyor... sohbeti kapat.§r", "§7Opening pacts... close the chat.§r"));
                        openOffers(p);
                    } catch (Throwable e) { /* yut */ }
                });
            });
            Events.After.playerLeave.subscribe(ev -> pactMods.remove(ev.playerId));
        } catch (Throwable e) { /* yut */ }
    }
}

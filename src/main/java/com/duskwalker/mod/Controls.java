package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.*;
import java.util.function.Consumer;

import static com.duskwalker.mod.Core.*;

/**
 * DUSKWALKER v14.1 - KONTROL ESYASI ("Cep Saati") VE ANA MENU - v10/controls.js portu.
 * Saati KULLANIRSAN (sag tik / dokun) ANA MENU acilir. Saat kaybolursa bir dakika icinde yenisi verilir.
 */
public final class Controls {
    private Controls() {}

    private static final String CMARK = "§8​DWC";
    private static final String OPTOUT = "duskwalker:v14noWatch";

    /** main.js'in setMainHooks({ openSettings, openJournals }) ile verdigi geri cagrimlar (null olabilir). */
    public static final class Hooks {
        public Consumer<Player> openSettings;
        public Consumer<Player> openJournals;
        public Hooks() {}
        public Hooks(Consumer<Player> openSettings, Consumer<Player> openJournals) { this.openSettings = openSettings; this.openJournals = openJournals; }
    }

    private static final Hooks hooks = new Hooks();

    /** Object.assign(hooks, h): verilen (null olmayan) alanlar uzerine yazilir. */
    public static void setMainHooks(Hooks h) {
        if (h == null) return;
        if (h.openSettings != null) hooks.openSettings = h.openSettings;
        if (h.openJournals != null) hooks.openJournals = h.openJournals;
    }

    public static ItemStack makeControlItem() {
        ItemStack it = new ItemStack("minecraft:clock", 1);
        it.nameTag = tx("§6Duskwalker Cep Saati", "§6Duskwalker Pocket Watch");
        it.setLore(CMARK,
            tx("§7Kullan (sag tik / dokun): ana menu", "§7Use (right-click / tap): main menu"),
            tx("§8Sohbet komutu gerektirmez (Hardcore'da da calisir)", "§8Needs no chat commands (works in Hardcore)"));
        return it;
    }

    public static boolean isControlItem(ItemStack it) {
        if (it == null) return false;
        List<String> lore = safe(() -> it.getLore(), new ArrayList<String>());
        if (lore == null) lore = new ArrayList<>();
        return lore.size() > 0 && lore.get(0).equals(CMARK);
    }

    private static boolean hasControlItem(Player p) {
        Container inv = safe(() -> p.inventory(), (Container) null);
        if (inv == null) return true; // bakilamiyorsa verme
        for (int i = 0; i < inv.size(); i++) {
            final int slot = i;
            if (isControlItem(safe(() -> inv.getItem(slot), (ItemStack) null))) return true;
        }
        return false;
    }

    private static boolean giveControl(Player p) {
        Container inv = safe(() -> p.inventory(), (Container) null);
        if (inv == null || inv.emptySlotsCount() == 0) return false;
        safe(() -> inv.addItem(makeControlItem()));
        return true;
    }

    // ---------------------------------------------------------
    //  Ana menu
    // ---------------------------------------------------------
    private static final class Entry {
        final String label; final Consumer<Player> action;
        Entry(String label, Consumer<Player> action) { this.label = label; this.action = action; }
    }

    private static List<Entry> entries() {
        List<Entry> list = new ArrayList<>();
        list.add(new Entry(tx("Gunlukler (notlar)", "Journals (notes)"), p -> { if (hooks.openJournals != null) hooks.openJournals.accept(p); }));
        list.add(new Entry(tx("Istatistikler", "Statistics"), p -> Stats.openStatsForm(p)));
        list.add(new Entry(tx("Basarimlar", "Achievements"), p -> Stats.openAchievementsForm(p)));
        list.add(new Entry(tx("Kodeks", "Codex"), p -> Codex.openCodex(p)));
        list.add(new Entry(tx("Kayip Mektuplar", "Lost Letters"), p -> Extras.openLetters(p)));
        list.add(new Entry(tx("Hikaye Notlari", "Story Notes"), p -> Lore.openLoreForm(p)));
        list.add(new Entry(tx("Azim Yetenekleri", "Resolve Perks"), p -> Perks.openPerks(p)));
        list.add(new Entry(tx("Kronik", "Chronicle"), p -> Chronicle.openChronicle(p)));
        list.add(new Entry(tx("Duskwalker Dosyasi", "Duskwalker Dossier"), p -> Learn.openDossier(p)));
        list.add(new Entry(tx("Anlasmalar", "Pacts"), p -> Pacts.openOffers(p)));
        list.add(new Entry(tx("Rahatlik Ayarlari", "Comfort Settings"), p -> Comfort.openComfort(p)));
        list.add(new Entry(tx("Zorluk Onayarlari", "Difficulty Presets"), p -> Presets.openPresetForm(p)));
        list.add(new Entry(tx("Mod Ayarlari", "Mod Settings"), p -> { if (hooks.openSettings != null) hooks.openSettings.accept(p); }));
        list.add(new Entry(tx("Komut Listesi", "Command List"), p -> openHelp(p)));
        list.add(new Entry(tx("Saati bir daha verme", "Stop giving me the watch"), p -> optOut(p)));
        return list;
    }

    private static void optOut(Player p) {
        playerSet(p, OPTOUT, true);
        say(p, tx("§7Cep saati artik otomatik verilmeyecek. (Sohbette !dwmenu ile ayarlari acabilirsin.)§r", "§7The pocket watch will no longer be given automatically. (Use !dwmenu in chat for settings.)§r"));
    }

    private static void openHelp(Player p) {
        String body = tx(
            "§e!dwmenu§r ayarlar\n§e!dwstats§r istatistik  §e!dwach§r basarimlar  §e!dwcodex§r kodeks\n§e!dwperks§r yetenekler  §e!dwchronicle§r kronik  §e!dwdossier§r dosya\n§e!dwpact§r anlasma  §e!dwpay§r borc  §e!dwoath§r yemin\n§e!dwsanctuary§r siginak  §e!dwpast§r gecmis  §e!dwdescend§r derinlikler\n§e!paradox§r paradoks  §e!moon§r ay  §e!night§r gece  §e!dwforecast§r tahmin\n§e!dwletters§r mektuplar  §e!dwgraph§r grafik  §e!dwrank§r unvan  §e!dwcompass§r pusula\n§7Tum bunlara Cep Saati menusunden de ulasabilirsin.",
            "§e!dwmenu§r settings\n§e!dwstats§r statistics  §e!dwach§r achievements  §e!dwcodex§r codex\n§e!dwperks§r perks  §e!dwchronicle§r chronicle  §e!dwdossier§r dossier\n§e!dwpact§r pact  §e!dwpay§r debt  §e!dwoath§r oath\n§e!dwsanctuary§r sanctuary  §e!dwpast§r past  §e!dwdescend§r depths\n§e!paradox§r paradox  §e!moon§r moon  §e!night§r night  §e!dwforecast§r forecast\n§e!dwletters§r letters  §e!dwgraph§r graph  §e!dwrank§r rank  §e!dwcompass§r compass\n§7You can reach most of these from the Pocket Watch menu too.");
        Forms.Action form = new Forms.Action().title(tx("Komutlar", "Commands")).body(body).button(tx("Geri", "Back"));
        showForm(p, form, res -> openMainMenu(p));
    }

    public static void openMainMenu(Player player) {
        List<Entry> list = entries();
        Forms.Action form = new Forms.Action().title(tx("Duskwalker Cep Saati", "Duskwalker Pocket Watch")).body(tx("§7Bir bolum sec.", "§7Choose a section."));
        for (Entry e : list) form.button(e.label);
        showForm(player, form, res -> {
            if (res.selection >= 0 && res.selection < list.size()) {
                Entry e = list.get(res.selection);
                safe(() -> e.action.accept(player));
            }
        });
    }

    // ---------------------------------------------------------
    //  Esya kullanimi
    // ---------------------------------------------------------
    private static final Map<String, Long> lastUse = new HashMap<>();

    private static void onUse(Player p, ItemStack item) {
        if (!isControlItem(item)) return;
        long t = Sys.currentTick();
        if (t - lastUse.getOrDefault(p.id(), 0L) < 10) return;
        lastUse.put(p.id(), t);
        playTo(p, "random.click", 0.5, 1.2);
        safe(() -> openMainMenu(p));
    }

    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        initialized = true;

        try {
            Events.After.itemUse.subscribe(ev -> { safe(() -> onUse(ev.source, ev.itemStack)); });
            Events.After.playerInteractWithBlock.subscribe(ev -> { safe(() -> onUse(ev.player, ev.itemStack)); });
            Events.After.playerSpawn.subscribe(ev -> {
                safe(() -> {
                    Player p = ev.player;
                    Sys.runTimeout(() -> {
                        if (isValid(p) && isEnabled("controls") && !Boolean.TRUE.equals(playerGet(p, OPTOUT, false)) && !hasControlItem(p)) {
                            giveControl(p);
                            if (ev.initialSpawn) say(p, tx("§6[Cep Saati]§r Envanterine bir saat eklendi. Kullan (sag tik / dokun): ana menu. Hardcore'da da calisir.", "§6[Pocket Watch]§r A watch was added to your inventory. Use it (right-click / tap) for the main menu. Works in Hardcore too."));
                        }
                    }, 60);
                });
            });
            Events.After.playerLeave.subscribe(ev -> { lastUse.remove(ev.playerId); });
        } catch (Throwable e) { /* yut */ }

        // kaybolursa (olum, dusurme) bir dakika icinde yenisi
        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("controls")) return;
            for (Player p : World.getPlayers()) {
                try { if (!Boolean.TRUE.equals(playerGet(p, OPTOUT, false)) && !hasControlItem(p)) giveControl(p); } catch (Throwable e) { /* yut */ }
            }
        }, TICKS_PER_SEC * 60);
    }
}

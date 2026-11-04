package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import static com.duskwalker.mod.Core.*;

/** DUSKWALKER v10 - BULUNAN NOTLAR (LORE). lore.js portu. !dwlore ile okunur. */
public final class Lore {
    private Lore() {}

    private record Page(int need, String title, String text) {}

    private static final Page[] PAGES = {
        new Page(0, "Ilk Not", "Bu dunya her aksam biraz daha sessiz oluyor. Hayvanlar arkana bakiyor. Kimse nedenini soylemiyor."),
        new Page(1, "Alacakaranlik", "Gun batarken bir sey beliriyor. Yuzu yok, sadece bir siluet. Koyluler ona Duskwalker diyor: alacakaranligin yuruyeni."),
        new Page(2, "Isik", "Mesaleler onu uzak tutuyor. Ama sadece yeterince cok olurlarsa. Uc isik, bir siginak. Ondan azi sadece bir umut."),
        new Page(3, "Aylin", "Aylin onun kiz kardesiydi. Bir gece onu aramaya gitti ve donmedi. Simdi iki dunya arasinda bekliyor; seni uyarmaya calisiyor."),
        new Page(5, "Durmak", "Kipirdamayan avi gorur, sanir. Ama kipirdamayana yaklasir. Bu yuzden hareket etmek de, durmak da bir secimdir."),
        new Page(7, "Sesler", "Kendi ayak seslerini geriden duydugunda ugrasma. Ses sana yetisir. Onemli olan yetistiginde neye donuk durdugundur."),
        new Page(10, "Evler", "Her insan bir yeri 'ev' yapar. Duskwalker bunu bilir. Sen yokken kapini acar. Kotu niyetle degil, sadece merakla."),
        new Page(14, "Lav", "Eski kayitlar tek bir zayifliktan soz ediyor: lav. Hicbir kilic, hicbir ates onu yaralamaz. Ama eriyen tas, hatirlanan her seyi yutar."),
        new Page(20, "Labirent", "Uyurken seni yakaladiginda gotürdugu yer bir rüya degil. Duvarlar yer degistirir. Zumrut her zaman cikis demek degildir."),
        new Page(30, "Dolunay", "Dolunayda ince bir perde kalkar. Hangi sesin gercek oldugunu ayirt etmek zorlasir. Isiginin yaninda kal."),
        new Page(40, "Aylin'in Mektubu", "'Eger bunu okuyorsan elli gune yaklasiyorsun. Onu yenebilirsin ama yenmek yetmez; ona neden yuruduğunu hatirlatmak gerek.'"),
        new Page(49, "Son Sayfa", "Elli gun dolunca yuzlesme gelir. Hazir degilsen kacma. Hazirsan da kacma. O seni zaten hatirliyor.")
    };

    private static int unlockedCount(Player player) {
        double nights = getStat(player, "nightsSurvived", 0);
        int n = 0;
        for (Page p : PAGES) if (nights >= p.need()) n++;
        return n;
    }

    public static void openLoreForm(Player player) {
        int count = unlockedCount(player);
        Forms.Action form = new Forms.Action().title("Duskwalker - Notlar").body("§7Acilan sayfa: " + count + " / " + PAGES.length + "§r");
        for (int i = 0; i < PAGES.length; i++) form.button(i < count ? PAGES[i].title() : "§8???");
        showForm(player, form, res -> {
            int i = res.selection;
            if (i < 0 || i >= count) return;
            Forms.Action page = new Forms.Action().title(PAGES[i].title()).body("§f" + PAGES[i].text()).button("Geri");
            showForm(player, page, r -> openLoreForm(player));
        });
    }

    public static void init() {
        // Yeni sayfa acilinca haber ver
        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("achievements")) return;
            for (Player p : World.getPlayers()) {
                try {
                    int count = unlockedCount(p);
                    double seen = getStat(p, "loreSeen", 1);
                    if (count > seen) {
                        setStat(p, "loreSeen", (double) count);
                        say(p, "§6[Not]§r Yeni bir not sayfasi acildi: §e" + PAGES[count - 1].title() + "§r (!dwlore)");
                        playTo(p, "random.orb", 0.6, 0.9);
                    }
                } catch (Throwable e) { /* yut */ }
            }
        }, 400);

        try {
            Events.Before.chatSend.subscribe(ev -> {
                if (!"!dwlore".equals(ev.message)) return;
                ev.cancel = true;
                Player player = ev.sender;
                Sys.run(() -> {
                    say(player, "§7Notlar aciliyor... sohbeti kapat.§r");
                    safe(() -> openLoreForm(player));
                });
            });
        } catch (Throwable e) { /* yut */ }
    }
}

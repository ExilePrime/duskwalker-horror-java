package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.ArrayList;
import java.util.List;

import static com.duskwalker.mod.Core.*;

/**
 * DUSKWALKER v11 - KODEKS (korku ansiklopedisi). codex.js portu.
 * !dwcodex ile acilir. Yasanan her korku olayi ve gorulen her ay evresi kodekse eklenir.
 */
public final class Codex {
    private Codex() {}

    /** [istatistik anahtari, ad, aciklama, ipucu] (her biri [tr, en]) */
    public static final class Entry {
        public final String key;
        public final String[] name, desc, tip;
        Entry(String key, String[] name, String[] desc, String[] tip) { this.key = key; this.name = name; this.desc = desc; this.tip = tip; }
    }

    public static final List<Entry> ENTRIES = new ArrayList<>();

    private static void add(String key, String nameTr, String nameEn, String descTr, String descEn, String tipTr, String tipEn) {
        ENTRIES.add(new Entry(key, new String[] { nameTr, nameEn }, new String[] { descTr, descEn }, new String[] { tipTr, tipEn }));
    }

    static {
        add("ev_delayed_steps",
            "Gecikmeli Adimlar", "Delayed Steps",
            "Kendi adimlarin sana geri gelir.", "Your own footsteps come back to you.",
            "Durma; yurumeye devam et.", "Don't stop; keep walking.");
        add("ev_phantom_sound",
            "Hayalet Sesler", "Phantom Sounds",
            "Olmayan seylerin sesleri.", "Sounds of things that aren't there.",
            "Isik siginagi seni korur.", "A light refuge protects you.");
        add("ev_fake_damage",
            "Sahte Hasar", "Fake Damage",
            "Can gitmez ama acisi gercek gibi.", "No health is lost, but the pain feels real.",
            "Panik yapma, bir sey olmadi.", "Don't panic; nothing happened.");
        add("ev_door_knock",
            "Kapi Vurusu", "Door Knock",
            "Biri kapini calar. Dis tarafta kimse yok.", "Someone knocks. Nobody is outside.",
            "Dordüncü vurus iceriden gelir.", "The fourth knock comes from inside.");
        add("ev_far_bell",
            "Uzak Can", "Distant Bell",
            "Hicbir yerde can yokken can sesi.", "A bell rings where no bell exists.",
            "Sesin tersi yone git.", "Go the opposite way of the sound.");
        add("ev_freeze_frame",
            "Sahte Cokme", "Fake Crash",
            "Oyun donmus gibi olur, sonra o yakindadir.", "The game seems to freeze, then it is close.",
            "Donmuşken bile hazirlikli ol.", "Be ready even when frozen.");
        add("ev_turning_heads",
            "Donen Kafalar", "Turning Heads",
            "Hayvanlar hep birden arkana bakar.", "The animals all look behind you at once.",
            "Arkana bakma; ileri git.", "Don't look back; go forward.");
        add("ev_stand_still",
            "Durursan Gelir", "Stand Still",
            "Kipirdamazsan bir sey yaklasir.", "If you don't move, something approaches.",
            "Hareket etmek onu dagitir.", "Moving makes it vanish.");
        add("ev_cold_spot",
            "Soguk Nokta", "Cold Spot",
            "Havada duran soguk bir bolge.", "A cold region hanging in the air.",
            "Icinden gecme.", "Don't walk through it.");
        add("ev_fake_friend",
            "Sahte Arkadas", "Fake Friend",
            "Cevrimdisi bir arkadasin yaziyor.", "An offline friend is writing.",
            "Cevap verme.", "Don't reply.");
        add("ev_sign_diary",
            "Tabela Gunlugu", "Sign Diary",
            "Sana ait bilgiler yazili bir tabela.", "A sign with details only you know.",
            "Okumak zararsiz; dokunma.", "Reading is harmless; don't touch.");
        add("ev_light_flicker",
            "Titreyen Isik", "Flickering Light",
            "Mesaleler sonup geri gelir.", "The torches die and come back.",
            "Isik dönecek. Sabret.", "The light will return. Be patient.");
        add("ev_grave_marker",
            "Mezar Tasi", "Grave Marker",
            "Senin adinin yazili oldugu bir mezar.", "A grave bearing your name.",
            "Uzaklas; kendiliginden kaybolur.", "Back away; it vanishes on its own.");
        add("ev_dont_blink",
            "Don't Blink", "Don't Blink",
            "Bakmadiginda yaklasan bir heykel.", "A statue that advances when you look away.",
            "Ona bakarak geri geri git.", "Back away while facing it.");
        add("ev_eyes_dark",
            "Karanliktaki Gozler", "Eyes in the Dark",
            "Uzakta iki kirmizi goz.", "Two red eyes in the distance.",
            "Dogrudan bakarsan kaybolur.", "Stare at them and they vanish.");
        add("ev_shadow_follower",
            "Golge Takipci", "Shadow Follower",
            "Yolunu geriden tekrarlayan golge.", "A shadow retracing your path.",
            "Hizli yuru, durma.", "Move fast; don't stop.");
        add("ev_haunted_item",
            "Lanetli Esya", "Haunted Item",
            "Bir esyan soguk olur ve not tasir.", "An item turns cold and bears a note.",
            "Zararsiz; sadece adi degisir.", "Harmless; only its name changes.");
        add("ev_chest_bite",
            "Sandik Isirigi", "Chest Bite",
            "Gece bir sandik seni isirabilir.", "A chest may bite you at night.",
            "Gece sandik acarken dikkatli ol.", "Be careful opening chests at night.");
        add("ev_system_glitch",
            "Sistem Arizasi", "System Glitch",
            "Sahte sunucu kayitlari.", "Fake server logs.",
            "Gercek degil; devam et.", "Not real; carry on.");
        add("ev_candle_gamble",
            "Mum Kumari", "Candle Gamble",
            "Sans oyunu: nimet, lanet, alamet, hediye.", "A gamble: blessing, curse, omen, gift.",
            "Saglamligin yuksekken dene.", "Try it when your sanity is high.");
        add("ev_fake_coords",
            "Sahte Koordinat", "Fake Coordinates",
            "Konum gostergesi yalan soyler.", "The coordinate display lies.",
            "Kendi sezgine guven.", "Trust your instincts.");
        add("ev_slot_drift",
            "Kayan Slot", "Slot Drift",
            "Elindeki esya kendiliginden degisir.", "Your held item changes by itself.",
            "Dusuk saglamlik belirtisi: toparlan.", "A sign of low sanity: recover.");
        add("ev_whisper_title",
            "Altlik Mesaj", "Subliminal Message",
            "Ekranda ucuk bir yazi.", "A brief flash of text on screen.",
            "Dikkat etme.", "Pay it no mind.");
        add("ev_false_dawn",
            "Yalan Safak", "False Dawn",
            "Gece ortasinda sahte sabah.", "A fake morning in the middle of the night.",
            "Hala gece. Isiginin yaninda kal.", "It's still night. Stay by your light.");
        add("ev_deja_vu",
            "Deja Vu", "Deja Vu",
            "6 saniye geri isinlanirsin; sohbet satirin tekrar belirir.", "You are thrown back 6 seconds; your chat line appears again.",
            "Sakin ol; tekrar ilerle.", "Stay calm; move forward again.");
        add("ev_countdown",
            "Geri Sayim", "Countdown",
            "Hizlanan bir tik sesi; sifirda bir sey belirir.", "An accelerating tick; something appears at zero.",
            "Isik siginagina gir.", "Get inside a light refuge.");
        add("ev_nape_breath",
            "Ense Nefesi", "Breath on Your Neck",
            "Hareketsizken ensende nefes.", "Breathing at your neck while you stand still.",
            "Don ve bak; hicbir sey yok.", "Turn and look; there is nothing.");
        add("ev_mirror_twin",
            "Ayna Ikiz", "Mirror Twin",
            "Hareketlerinin nokta yansimasi olan bir figur.", "A figure that is the point reflection of your movement.",
            "Ona yuruyerek degil, yana kayarak yaklas.", "Don't walk at it; sidestep away.");
        add("ev_unwritten",
            "Yazilmamis", "Unwritten",
            "Kendini harf harf yazan tabela.", "A sign that writes itself letter by letter.",
            "Oku ve uzaklas.", "Read it and walk away.");
        add("ev_listening_hour",
            "Dinleme Saati", "The Listening Hour",
            "2 dakika boyunca gurultu 3 kat agir sayilir.", "For 2 minutes, noise counts triple.",
            "Egil, kosma, kazma.", "Sneak; don't run or dig.");
        add("ev_lantern_thief",
            "Fener Hirsizi", "Lantern Thief",
            "Bir isik tasinir; uzakta belirir.", "A light is carried off and reappears far away.",
            "Peşinden gitme; geri gelir.", "Don't follow it; it returns.");
        add("ev_footprint_trail",
            "Ruh Ayak Izleri", "Footprints",
            "Uzaktan sana gelen ruh ayak izleri.", "Soul footprints approaching you from afar.",
            "Izlerin gittigi yere bakma.", "Don't look where the prints lead.");
        add("ev_bat_omen",
            "Yarasa Alameti", "Bat Omen",
            "Yarasalar hep ayni yone ucar.", "Bats all flee the same way.",
            "Onlarin gittigi yonun tersine git.", "Go opposite to where they fly.");
        add("ev_glitch_hud",
            "Arayuz Arizasi", "HUD Glitch",
            "Ekran yazisi bozulur.", "The on-screen text corrupts.",
            "Gercek degil; gecer.", "Not real; it passes.");
        add("ev_circling_bells",
            "Donen Canlar", "Circling Bells",
            "Etrafinda donen can sesi.", "Bells ring in a circle around you.",
            "Yerinde kal; sesi takip etme.", "Hold still; don't follow the sound.");
        add("ev_music_box",
            "Muzik Kutusu", "Music Box",
            "Uzakta yarim kalan bir ninni.", "A distant lullaby that stops halfway.",
            "Sesin geldigi yere yaklasma.", "Don't approach the sound.");
        add("ev_pit_whisper",
            "Alttan Fisilti", "Whisper from Below",
            "Ayaklarinin altindan bir ses.", "A voice from beneath your feet.",
            "Yere kulak verme.", "Don't listen to the ground.");
        add("ev_weightless",
            "Agirliksizlik", "Weightless",
            "Yer cekimi bir an birakir.", "Gravity lets go for a moment.",
            "Sakin ol; yavas duser.", "Stay calm; you fall slowly.");
        add("ev_sign_turn",
            "Donen Tabela", "Sign Turn",
            "Yakindaki tabelanin yazisi degisir.", "A nearby sign's text changes.",
            "Yazi sonra geri doner.", "The text returns after a while.");
        add("ev_shared_breath",
            "Paylasilan Nefes", "Shared Breath",
            "Nefesinle esgudumlu ikinci bir nefes.", "A second breath in sync with yours.",
            "Nefesini say; ikincisini ayirt et.", "Count your breaths; notice the second.");
        add("ev_numbers_station",
            "Sayi Istasyonu", "Numbers Station",
            "Sayilar okunur; sonunda 'sira sende'.", "Numbers are read; at the end: 'your turn'.",
            "Cevap verme.", "Do not answer.");
        add("ev_moths",
            "Guveler", "Moths",
            "Bir isigin etrafindaki parcaciklar sana gelir.", "Particles swarm a light, then come to you.",
            "Isiktan uzaklas.", "Step away from the light.");
        add("ev_echo_mine",
            "Yanki Kazma", "Echo Mine",
            "Kazarken duvarin ardindan kazma sesi.", "A pickaxe sounds behind the wall as you mine.",
            "Baska yone kaz.", "Dig another way.");
        add("ev_tinnitus",
            "Kulak Cinlamasi", "Tinnitus",
            "Yuksek bir cinlama.", "A high-pitched ringing.",
            "Gecer.", "It passes.");
        add("ev_fake_loot",
            "Yalan Ganimet", "Mirage Loot",
            "Yerde elmas; yaklasinca yok.", "A diamond on the ground; gone when you near.",
            "Hayal; yaklasma.", "A mirage; don't chase it.");
        add("ev_the_lull",
            "Yalan Sakinlik", "The Lull",
            "Bir dakika sakinlik, sonra bir olay.", "A minute of calm, then an event.",
            "Sakinlige guvenme.", "Don't trust the calm.");
        add("ev_ring_of_faces",
            "Yuzler Halkasi", "Ring of Faces",
            "Etrafinda halka olan siluetler.", "Silhouettes form a ring around you.",
            "Hepsi birden yok olur.", "They vanish all at once.");
        add("ev_wall_of_faces",
            "Yuzler Duvari", "Wall of Faces",
            "Baska yone bakinca onunde bir sira.", "A row appears when you look away.",
            "Bak; yok olurlar.", "Look; they vanish.");
        add("ev_horizon_watchers",
            "Ufuk Gozculeri", "Horizon Watchers",
            "Ufukta duran siluetler.", "Silhouettes standing on the horizon.",
            "Yaklasirsan sirt doner.", "They turn their backs if you approach.");
        add("ev_paradox",
            "Paradoks Koridoru", "Paradox Corridor",
            "Sonsuz tahta koridor; kurtulmak icin bir kapi gerekir.", "An endless wooden corridor; only a door can free you.",
            "Kapi yap, yerlestir, ona arkani don ve uzaklas.", "Make a door, place it, turn your back and walk away.");
        add("ev_peripheral_figure",
            "Yan Gorus Silueti", "Peripheral Figure",
            "Gorus alaninin kenarinda bir siluet.", "A silhouette at the edge of your vision.",
            "Bakma; kendiliginden gider.", "Don't look; it leaves on its own.");
        add("ev_mirror_behind",
            "Arkadaki Sima", "Mirror Behind You",
            "Hareketsizken tam arkanda bir siluet.", "A silhouette right behind you while you stand still.",
            "Don; yok olur.", "Turn around; it vanishes.");
        add("ev_tunnel_vision",
            "Tunel Gorusu", "Tunnel Vision",
            "Gorus alanin daralir.", "Your vision narrows.",
            "Dur, nefes al.", "Stop and breathe.");
        add("ev_water_shadow",
            "Sudaki Golge", "Shadow in the Water",
            "Suyun altinda bir sey nefes alir.", "Something breathes beneath the water.",
            "Sudan uzaklas.", "Move away from the water.");
        add("ev_lightning_silhouette",
            "Yildirim Siluet", "Lightning Silhouette",
            "Simsekte ileride bir siluet.", "A silhouette ahead in the lightning.",
            "Siginaga git.", "Seek shelter.");
        add("moon_full",
            "Dolunay", "Full Moon",
            "Tek buyuk ay. Perde incelir.", "A single great moon. The veil thins.",
            "Isik siginagi sart.", "A light refuge is a must.");
        add("moon_twin",
            "Ikiz Aylar", "Twin Moons",
            "Gokte iki ay. Hayalet sesler artar.", "Two moons. Phantom sounds multiply.",
            "Aciktayken uzun kalma.", "Don't linger in the open.");
        add("moon_third",
            "Ucte Uc", "Third Quarter",
            "Yari ay. Sakin bir gece.", "A half moon. A calm night.",
            "Dinlen.", "Rest.");
        add("moon_none",
            "Aysiz Gece", "Moonless Night",
            "Gokte ay yok. Karanlik pulse'lari.", "No moon. Pulses of darkness.",
            "Isik siginagi ekstra toparlar.", "Light refuges heal extra.");
        add("moon_eclipse",
            "Tutulma", "Eclipse",
            "Kara disk ve turuncu korona.", "A black disc and orange corona.",
            "Saglamligin yavasca erir; icerde kal.", "Sanity erodes; stay inside.");
        add("moon_crescent",
            "Ikiz Hilal", "Twin Crescents",
            "Iki ince hilal.", "Two thin crescents.",
            "Olaylar biraz sikir.", "Events come a bit more often.");
        add("moon_first",
            "Ilk Dordun", "First Quarter",
            "Yari ay. Normal bir gece.", "A half moon. A normal night.",
            "Hazirlik yap.", "Prepare.");
        add("moon_blood",
            "Kan Ayi", "Blood Moon",
            "Kirmizi ay. En tehlikeli gece.", "A red moon. The most dangerous night.",
            "Yer alti veya siginak sart.", "Go underground or into a refuge.");
    }

    private static String n(double v) { return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v); }

    private static boolean discovered(Player player, String key) { return getStat(player, key, 0) > 0; }

    public static int codexProgress(Player player) {
        int n = 0;
        for (Entry e : ENTRIES) if (discovered(player, e.key)) n++;
        return n;
    }

    public static void openCodex(Player player) {
        int found = codexProgress(player);
        Forms.Action form = new Forms.Action()
            .title(tx("Duskwalker Kodeksi", "Duskwalker Codex"))
            .body(tx("§7Kesfedilen: §f", "§7Discovered: §f") + found + " / " + ENTRIES.size());
        for (Entry e : ENTRIES) {
            double count = getStat(player, e.key, 0);
            form.button(count > 0 ? "§f" + tx(e.name) + " §8(x" + n(count) + ")" : "§8???");
        }
        showForm(player, form, res -> {
            if (res.selection < 0 || res.selection >= ENTRIES.size()) return;
            Entry e = ENTRIES.get(res.selection);
            if (!discovered(player, e.key)) return;
            double count = getStat(player, e.key, 0);
            Forms.Action detail = new Forms.Action()
                .title(tx(e.name))
                .body("§f" + tx(e.desc) + "\n\n§7" + tx("Yasanma: ", "Seen: ") + n(count) + "\n§e" + tx("Ipucu: ", "Tip: ") + "§f" + tx(e.tip))
                .button(tx("Geri", "Back"));
            showForm(player, detail, r -> openCodex(player));
        });
    }

    public static void init() {
        // Kodeks tamamlaninca bir kerelik duyuru
        Sys.runInterval(() -> {
            if (isDefeated() || !isEnabled("achievements")) return;
            for (Player p : World.getPlayers()) {
                try {
                    if (getStat(p, "codexDone", 0) > 0) continue;
                    if (codexProgress(p) >= ENTRIES.size()) {
                        setStat(p, "codexDone", 1.0);
                        title(p, tx("§6Kodeks Tamam", "§6Codex Complete"), tx("§7Hepsini gordun", "§7You have seen it all"), 70);
                        playTo(p, "random.levelup", 0.9, 0.8);
                    }
                } catch (Throwable e) { /* yut */ }
            }
        }, 400);

        try {
            Events.Before.chatSend.subscribe(ev -> {
                if (!"!dwcodex".equals(ev.message)) return;
                ev.cancel = true;
                Player p = ev.sender;
                Sys.run(() -> {
                    say(p, tx("§7Kodeks aciliyor... sohbeti kapat.§r", "§7Opening codex... close the chat.§r"));
                    safe(() -> openCodex(p));
                });
            });
        } catch (Throwable e) { /* yut */ }
    }
}

package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.*;

import static com.duskwalker.mod.Core.*;

/** DUSKWALKER v10 - ZORLUK ONAYARLARI + HOSGELDIN. presets.js portu. */
public final class Presets {
    private Presets() {}

    private record Preset(String name, String desc, double mult, String[] off) {}

    private static final Preset[] PRESETS = {
        new Preset("Huzur", "Hafif: az yogunluk, en agir olaylar kapali", 0.5,
            new String[] { "twinhunt", "huntfast", "abduction", "freeze_frame", "stand_still", "possession", "fake_damage" }),
        new Preset("Normal", "Onerilen deneyim: her sey acik", 1.0, new String[0]),
        new Preset("Kabus", "Daha sik ve daha yogun olaylar", 1.4, new String[0]),
        new Preset("Zalim", "En yuksek yogunluk. Dikkatli ol.", 2.0, new String[0])
    };

    private static void applyPreset(Player player, Preset preset) {
        Map<String, Object> cfg = readConfig();
        Map<String, Object> enabled = Json.asMap(cfg.get("enabled"));
        if (enabled == null) enabled = Json.newMap();
        // onceki onayardan kalan kapali olaylari temizle, sonra yenilerini kapat
        for (String k : new ArrayList<>(enabled.keySet())) enabled.put(k, Boolean.TRUE);
        for (String k : preset.off()) enabled.put(k, Boolean.FALSE);
        Map<String, Object> out = Json.newMap();
        out.put("intensityMult", preset.mult());
        out.put("enabled", enabled);
        safe(() -> World.setDynamicProperty(KEY.CONFIG, Json.stringify(out)));
        say(player, "§7Onayar uygulandi: §f" + preset.name() + "§7 (yogunluk x" + String.format(Locale.ROOT, "%.1f", preset.mult()) + ")§r");
    }

    public static void openPresetForm(Player player) {
        Forms.Action form = new Forms.Action().title("Duskwalker - Zorluk").body("§7Bir onayar sec. Sonra !dwmenu ile ince ayar yapabilirsin.§r");
        for (Preset p : PRESETS) form.button(p.name() + "\n§8" + p.desc());
        showForm(player, form, res -> {
            if (res.selection >= 0 && res.selection < PRESETS.length) applyPreset(player, PRESETS[res.selection]);
        });
    }

    private static final String WELCOMED = "duskwalker:v10welcomed";

    public static void init() {
        try {
            Events.Before.chatSend.subscribe(ev -> {
                if (!"!dwpreset".equals(ev.message)) return;
                ev.cancel = true;
                Player player = ev.sender;
                Sys.run(() -> {
                    say(player, "§7Onayar menusu aciliyor... sohbeti kapat.§r");
                    openPresetForm(player);
                });
            });
        } catch (Throwable e) { /* yut */ }

        // Hosgeldin (her oyuncuya bir kez)
        try {
            Events.After.playerSpawn.subscribe(ev -> {
                if (!ev.initialSpawn) return;
                Player player = ev.player;
                Sys.runTimeout(() -> {
                    try {
                        if (!isValid(player)) return;
                        Object w = player.getDynamicProperty(WELCOMED);
                        if (w != null && !Boolean.FALSE.equals(w) && !(w instanceof Number n && n.doubleValue() == 0) && !"".equals(w)) return;
                        player.setDynamicProperty(WELCOMED, true);
                        say(player, "§6Duskwalker Horror§r\n§7Alacakaranlikta bir sey yuruyor. Isik seni korur; karanlik ve kipirdamamak seni ele verir.§r\n§e!dwhelp§7 komutlari, §e!dwpreset§7 zorlugu, §e!dwlore§7 notlari gosterir.§r");
                    } catch (Throwable e) { /* yut */ }
                }, 200);
            });
        } catch (Throwable e) { /* yut */ }
    }
}

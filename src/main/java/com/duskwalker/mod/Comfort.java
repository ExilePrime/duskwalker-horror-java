package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import static com.duskwalker.mod.Core.*;

/** DUSKWALKER v11 - RAHATLIK AYARLARI (erisilebilirlik). comfort.js portu. */
public final class Comfort {
    private Comfort() {}

    public static void openComfort(Player player) {
        Prefs cur = getPrefs(player);
        Forms.Modal form = new Forms.Modal()
            .title(tx("Rahatlik Ayarlari", "Comfort Settings"))
            .toggle(tx("Hafif mod (korku kaybi yariya iner)", "Mild mode (halve fear losses)"), cur.mild)
            .toggle(tx("Isiksiz mod (titreme/flas yok)", "Flash-free mode (no shake/flash)"), cur.noflash)
            .toggle(tx("Sahte sohbet mesajlari yok", "No fake chat messages"), cur.nochat)
            .toggle(tx("Yeni korku olaylarini durdur", "Pause new horror events"), cur.pause);
        showForm(player, form, res -> {
            java.util.List<Object> v = res.formValues;
            setPrefs(player, new Prefs(
                Boolean.TRUE.equals(v.get(0)), Boolean.TRUE.equals(v.get(1)), Boolean.TRUE.equals(v.get(2)), Boolean.TRUE.equals(v.get(3))));
            say(player, tx("§7Rahatlik ayarlari kaydedildi.§r", "§7Comfort settings saved.§r"));
        });
    }

    public static void init() {
        try {
            Events.Before.chatSend.subscribe(ev -> {
                if (!"!dwcomfort".equals(ev.message)) return;
                ev.cancel = true;
                Player p = ev.sender;
                Sys.run(() -> {
                    say(p, tx("§7Rahatlik menusu aciliyor... sohbeti kapat.§r", "§7Opening comfort menu... close the chat.§r"));
                    safe(() -> openComfort(p));
                });
            });
        } catch (Throwable e) { /* yut */ }
    }
}

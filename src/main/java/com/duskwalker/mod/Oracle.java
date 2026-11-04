package com.duskwalker.mod;

import com.duskwalker.bedrock.*;

import java.util.*;

import static com.duskwalker.mod.Core.*;

/** DUSKWALKER v11 - KEHANET (sohbete tepki). oracle.js portu. */
public final class Oracle {
    private Oracle() {}

    /** [anahtar kelimeler (kucuk harf), minDay, [tr, en]] */
    private record Reply(String[] keys, int minDay, String[] text) {}

    private static Reply r(String[] keys, int minDay, String tr, String en) { return new Reply(keys, minDay, new String[] { tr, en }); }

    private static final Reply[] REPLIES = {
        r(new String[] { "yardim", "help" }, 1, "Kimse gelmiyor.", "No one is coming."),
        r(new String[] { "merhaba", "selam", "hello", "hi " }, 1, "...", "..."),
        r(new String[] { "kimsin", "who are you", "who r u" }, 2, "Senin duymak istemedigin.", "The one you didn't want to hear."),
        r(new String[] { "korkuyorum", "scared", "afraid" }, 2, "Biliyorum. Hissediyorum.", "I know. I can feel it."),
        r(new String[] { "gitmek", "ayril", "leave", "quit" }, 3, "Nereye?", "Where to?"),
        r(new String[] { "aylin" }, 3, "O artik seni duymuyor.", "She no longer hears you."),
        r(new String[] { "kardes", "kardeş", "sister" }, 5, "Onu sen de kaybedeceksin.", "You'll lose her too."),
        r(new String[] { "lav", "lava" }, 4, "Aptal degilim.", "I'm not a fool."),
        r(new String[] { "mesale", "torch" }, 3, "Bir gun biter.", "They run out one day."),
        r(new String[] { "yaklas", "come", "gel " }, 6, "Zaten yakindayim.", "I'm already close."),
        r(new String[] { "gorunmez", "where are you", "nerdesin" }, 4, "Arkanda.", "Behind you."),
        r(new String[] { "lol", "haha", "xd" }, 3, "Guluyorsun. Guzel.", "You laugh. Good."),
        r(new String[] { "ay ", "moon" }, 4, "Ay seni gormuyor. Ben goruyorum.", "The moon doesn't see you. I do."),
        r(new String[] { "ugur", "gule gule", "bye" }, 5, "Gidemezsin.", "You cannot leave."),
        r(new String[] { "nasil", "how" }, 6, "Hep ayni sekilde.", "Always the same way."),
        r(new String[] { "uyu", "sleep", "bed" }, 5, "Uyu. Bekliyorum.", "Sleep. I'll wait."),
        r(new String[] { "olum", "oldum", "died", "death" }, 4, "Alisirsin.", "You get used to it."),
        r(new String[] { "sen", "you " }, 8, "Hayir. Sen.", "No. You."),
        r(new String[] { "yenecegim", "kill you", "defeat" }, 10, "Elli gun sonra gorusuruz.", "See you in fifty days."),
        r(new String[] { "hile", "cheat", "creative" }, 3, "Oyun kural degil, korku.", "This isn't rules, it's fear."),
        r(new String[] { "neden", "why" }, 7, "Cunku yalnizdim.", "Because I was alone.")
    };

    private static final Map<String, Long> cooldown = new HashMap<>();

    public static void init() {
        try {
            Events.Before.chatSend.subscribe(ev -> {
                try {
                    String msg = ev.message;
                    if (msg == null || (!msg.isEmpty() && msg.charAt(0) == '!')) return;
                    Player sender = ev.sender;
                    String low = " " + msg.toLowerCase(Locale.ROOT) + " ";
                    Sys.run(() -> {
                        try {
                            if (isDefeated() || !isEnabled("oracle") || !isValid(sender) || getPrefs(sender).nochat) return;
                            long tick = Sys.currentTick();
                            if (tick < cooldown.getOrDefault(sender.id(), 0L)) return;
                            int day = getDay();
                            List<Reply> hits = new ArrayList<>();
                            for (Reply rp : REPLIES) {
                                if (day < rp.minDay()) continue;
                                boolean any = false;
                                for (String k : rp.keys()) if (low.contains(k)) { any = true; break; }
                                if (any) hits.add(rp);
                            }
                            if (hits.isEmpty() || !chance(0.35)) return;
                            String reply = tx(hits.get(randInt(0, hits.size() - 1)).text());
                            cooldown.put(sender.id(), tick + 90L * TICKS_PER_SEC);
                            Sys.runTimeout(() -> {
                                if (!isValid(sender)) return;
                                say(sender, "§4Duskwalker§r: " + reply);
                                playTo(sender, "ambient.cave", 0.5, 0.6);
                                loseSanity(sender, 2);
                            }, randInt(40, 90));
                        } catch (Throwable e) { /* yut */ }
                    });
                } catch (Throwable e) { /* yut */ }
            });
        } catch (Throwable e) { /* yut */ }

        try { Events.After.playerLeave.subscribe(ev -> cooldown.remove(ev.playerId)); } catch (Throwable e) { /* yut */ }
    }
}
